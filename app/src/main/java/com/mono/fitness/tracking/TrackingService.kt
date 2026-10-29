package com.mono.fitness.tracking

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.mono.fitness.MainActivity
import com.mono.fitness.MonoApp
import com.mono.fitness.R
import com.mono.fitness.data.Activity
import com.mono.fitness.data.ActivityPoint
import com.mono.fitness.data.ActivityType
import com.mono.fitness.data.MonoDatabase
import com.mono.fitness.data.MonoRepository
import com.mono.fitness.util.Formatters
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Foreground GPS recording service. Survives screen-off.
 *
 * Session metrics live in [Session] (process-scoped companion state) so a service
 * restart, pause, or resume never wipes distance / elevation / time mid-workout.
 * UI observes [TrackingController], which mirrors that state.
 */
class TrackingService : LifecycleService() {

    private val fused by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private val repo: MonoRepository by lazy {
        (application as? MonoApp)?.repository ?: MonoRepository(MonoDatabase.get(this))
    }
    private var hrClient: HeartRateBleClient? = null
    private var locationsRequested: Boolean = false
    private var ticker: Job? = null
    private var heartRateJob: Job? = null

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val session = Session.current ?: return
            if (session.paused || session.finishing) return
            result.locations.forEach { onLocation(session, it) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        Session.current?.writer?.rebind(lifecycleScope)
        when (intent?.action) {
            ACTION_START -> {
                val type = intent.getStringExtra(EXTRA_TYPE) ?: ActivityType.RUN.name
                val actId = intent.getLongExtra(EXTRA_ACTIVITY_ID, 0L)
                startRecording(type, actId)
            }
            ACTION_PAUSE -> pause()
            ACTION_RESUME -> resume()
            ACTION_STOP -> stopAndFinish()
            null -> {
                // Process/service restart with no action: keep an in-progress session alive.
                if (Session.current != null) {
                    ensureForeground()
                    requestLocations()
                    startTicker()
                    publish()
                    isRunning.value = true
                }
            }
        }
        // NOT_STICKY: do not redeliver START (which would look like a fresh session).
        return START_NOT_STICKY
    }

    private fun startRecording(type: String, actId: Long = 0L) {
        // Duplicate START (double-tap / accidental redelivery) must not wipe progress.
        val existing = Session.current
        if (existing != null) {
            if (existing.finishing) return
            if (actId > 0 && existing.activityId == 0L) {
                existing.activityId = actId
            }
            ensureForeground()
            requestLocations()
            startTicker()
            publish()
            isRunning.value = true
            return
        }

        val now = System.currentTimeMillis()
        val session = Session(
            id = nextSessionId.incrementAndGet(),
            activityType = type,
            startedAt = now,
            lastResumeAt = now,
            activityId = actId,
            writer = RecordingWriter(lifecycleScope, repo::saveRecordingProgress, actId)
        )
        Session.current = session

        saveError.value = null
        lastFinished.value = null
        saveCheckpoint(session)

        ensureChannel()
        ensureForeground()
        requestLocations()
        startHeartRate()
        startTicker()
        publish()
        isRunning.value = true
    }

    private fun ensureForeground() {
        val session = Session.current
        val notification = buildNotification(
            when {
                session == null -> "Recording…"
                session.paused -> "Paused"
                else -> "Recording…"
            }
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIF_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = lifecycleScope.launch {
            while (Session.current != null) {
                publish()
                delay(1000L)
            }
        }
    }

    private fun startHeartRate() {
        stopHeartRate()
        try {
            hrClient = HeartRateBleClient(this).also { client ->
                client.start()
                heartRateJob = lifecycleScope.launch {
                    launch {
                        client.connected.collect { companionHrConnected.value = it }
                    }
                    client.bpm.collect { bpm ->
                        val session = Session.current ?: return@collect
                        session.currentHr = bpm
                        if (bpm != null && !session.paused && !session.finishing) {
                            session.hrSum += bpm
                            session.hrCount++
                            session.maxHr = maxOf(session.maxHr, bpm)
                        }
                        publish()
                    }
                }
            }
        } catch (_: Exception) {
            stopHeartRate()
        }
    }

    private fun stopHeartRate() {
        heartRateJob?.cancel()
        heartRateJob = null
        try {
            hrClient?.stop()
        } catch (_: Exception) {
        }
        hrClient = null
        companionHrConnected.value = false
    }

    private fun requestLocations() {
        if (locationsRequested) return
        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L)
            .setMinUpdateIntervalMillis(1000L)
            .setMinUpdateDistanceMeters(2f)
            .setWaitForAccurateLocation(false)
            .build()
        try {
            fused.requestLocationUpdates(req, callback, Looper.getMainLooper())
            locationsRequested = true
        } catch (_: SecurityException) {
            locationsRequested = false
            // Do not stopSelf here — session state must stay until the user finishes.
        }
    }

    private fun stopLocationUpdates() {
        if (!locationsRequested) return
        try {
            fused.removeLocationUpdates(callback)
        } catch (_: Exception) {
        }
        locationsRequested = false
    }

    private fun onLocation(session: Session, loc: Location) {
        // Discard poor accuracy fixes (> 35m) to avoid erratic GPS jumps indoors / in tunnels
        if (loc.hasAccuracy() && loc.accuracy > 35f) {
            return
        }

        val elev = if (loc.hasAltitude()) loc.altitude else null
        session.lastLocation?.let { prev ->
            val d = prev.distanceTo(loc).toDouble()
            val elapsedNanos = loc.elapsedRealtimeNanos - prev.elapsedRealtimeNanos
            if (elapsedNanos <= 0) return
            val impliedSpeed = d / (elapsedNanos / 1_000_000_000.0)
            // Reject the entire fix; an outlier must not become the next anchor.
            if (impliedSpeed >= 45.0) return

            // Filter stationary GPS jitter (< 1.5m) and impossible jumps (> 45 m/s or 162 km/h)
            if (d >= 1.5 && impliedSpeed < 45.0) {
                session.distanceMeters += d
            }
        }
        elev?.let { e ->
            session.lastElev?.let { prev ->
                val g = e - prev
                // Require >= 1.0m to filter vertical sensor noise, max 35m per sample
                if (g >= 1.0 && g < 35.0) session.elevGain += g
            }
            session.lastElev = e
        }
        if (loc.hasSpeed() && loc.speed > 0f) {
            session.maxSpeed = maxOf(session.maxSpeed, loc.speed.toDouble())
        }
        session.lastLocation = loc
        val currentKm = (session.distanceMeters / 1000.0).toInt()
        if (currentKm > session.lastSplitKm && currentKm > 0) {
            val now = System.currentTimeMillis()
            val splitDuration = now - session.lastSplitTimeMs
            session.lastSplitKm = currentKm
            session.lastSplitTimeMs = now
            triggerSplitMilestone(currentKm, splitDuration)
        }

        val pt = ActivityPoint(
            activityId = session.activityId,
            latitude = loc.latitude,
            longitude = loc.longitude,
            elevationMeters = elev,
            timestampMillis = loc.time.takeIf { it > 0 } ?: System.currentTimeMillis(),
            speedMps = if (loc.hasSpeed()) loc.speed.toDouble() else null,
            accuracyMeters = if (loc.hasAccuracy()) loc.accuracy else null,
            sequence = session.path.size
        )
        session.path += pt
        publish()
        updateNotification()

        saveCheckpoint(session)
    }

    private fun activityFor(session: Session, end: Long): Activity {
        val moving = if (session.paused || session.finishing) session.movingAccumulated
            else session.movingAccumulated + (end - session.lastResumeAt).coerceAtLeast(0L)
        val type = ActivityType.fromName(session.activityType)
        return Activity(
            id = session.activityId,
            type = session.activityType,
            title = "${type.label} · ${Formatters.date(session.startedAt)}",
            distanceMeters = session.distanceMeters,
            durationMillis = (end - session.startedAt).coerceAtLeast(0L),
            movingTimeMillis = moving,
            avgSpeedMps = if (moving > 0) session.distanceMeters / (moving / 1000.0) else 0.0,
            maxSpeedMps = session.maxSpeed,
            elevationGainMeters = session.elevGain,
            calories = MonoRepository.estimateCalories(type, session.distanceMeters, moving),
            avgHeartRate = session.avgHr(),
            maxHeartRate = session.maxHr.takeIf { it > 0 },
            startTimeMillis = session.startedAt,
            endTimeMillis = end,
            isManual = false,
            source = "gps"
        )
    }

    private fun saveCheckpoint(session: Session) {
        val write = session.writer.save(activityFor(session, System.currentTimeMillis()), session.path.toList())
        lifecycleScope.launch {
            write.await().fold(
                onSuccess = {
                    session.activityId = it
                    saveError.value = null
                },
                onFailure = { saveError.value = "Could not save recording. Your next save will retry." }
            )
        }
    }

    private fun triggerSplitMilestone(km: Int, splitDurationMs: Long) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 150, 100, 250), -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(longArrayOf(0, 150, 100, 250), -1)
            }
        } catch (_: Exception) {
        }
    }

    /**
     * startForegroundService() requires startForeground() quickly. Control intents
     * on a cold service with no session promote briefly then tear down.
     */
    private fun acknowledgeForegroundOrStopIfIdle(): Boolean {
        ensureChannel()
        ensureForeground()
        val session = Session.current
        if (session != null) return !session.finishing
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        return false
    }

    private fun pause() {
        if (!acknowledgeForegroundOrStopIfIdle()) return
        val session = Session.current ?: return
        if (session.paused) {
            updateNotification()
            return
        }
        session.paused = true
        session.movingAccumulated += (System.currentTimeMillis() - session.lastResumeAt).coerceAtLeast(0L)
        // Keep GPS subscribed while paused so the location FGS stays valid and
        // resume does not re-bind from a wiped / half-dead service. Callback
        // already ignores fixes when paused == true.
        // Drop last fix so the first point after resume does not add pause travel.
        session.lastLocation = null
        session.lastElev = null
        saveCheckpoint(session)
        publish()
        updateNotification()
    }

    private fun resume() {
        if (!acknowledgeForegroundOrStopIfIdle()) return
        val session = Session.current ?: return
        startTicker()
        if (hrClient == null) startHeartRate()
        if (!session.paused) {
            requestLocations()
            publish()
            updateNotification()
            return
        }
        session.paused = false
        session.lastResumeAt = System.currentTimeMillis()
        session.lastLocation = null
        session.lastElev = null
        requestLocations()
        // Ensure UI / running flag stay consistent after a service rebind.
        isRunning.value = true
        publish()
        updateNotification()
    }

    private fun stopAndFinish() {
        ensureChannel()
        ensureForeground()
        val session = Session.current
        if (session == null) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        // A second STOP must not cancel an in-flight final write.
        if (!session.claimFinish()) return

        saveError.value = null
        stopLocationUpdates()
        stopHeartRate()
        if (!session.paused) {
            session.movingAccumulated += (System.currentTimeMillis() - session.lastResumeAt).coerceAtLeast(0L)
        }
        val end = System.currentTimeMillis()
        val activity = activityFor(session, end)
        val finalPoints = session.path.toList()
        val write = session.writer.save(activity, finalPoints)
        publish()
        lifecycleScope.launch {
            val result = try {
                val id = write.await().getOrThrow()
                repo.recalculatePersonalRecords()
                Result.success(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
            result.fold(
                onSuccess = { id ->
                    val snapshot = liveState.value.copy(
                        points = finalPoints,
                        activityId = id,
                        durationMillis = activity.durationMillis,
                        movingTimeMillis = activity.movingTimeMillis,
                        endTimeMillis = end,
                        paused = false,
                        recording = false,
                        saving = false
                    )
                    // Only announce completion after Room has committed the final track.
                    lastFinished.value = snapshot
                    liveState.value = snapshot
                    isRunning.value = false
                    Session.current = null
                    ticker?.cancel()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                },
                onFailure = {
                    session.releaseFinish()
                    session.paused = true
                    saveError.value = "Could not finish saving. Tap Finish to retry."
                    publish()
                    updateNotification()
                }
            )
        }
    }

    private fun publish() {
        val session = Session.current ?: return
        val now = System.currentTimeMillis()
        val moving = if (session.paused || session.finishing) {
            session.movingAccumulated
        } else {
            session.movingAccumulated + (now - session.lastResumeAt)
        }
        liveState.value = TrackingSnapshot(
            type = session.activityType,
            points = session.path.toList(),
            distanceMeters = session.distanceMeters,
            durationMillis = (now - session.startedAt).coerceAtLeast(0L),
            movingTimeMillis = moving,
            elevationGainMeters = session.elevGain,
            maxSpeedMps = session.maxSpeed,
            startTimeMillis = session.startedAt,
            endTimeMillis = now,
            paused = session.paused,
            recording = true,
            saving = session.finishing,
            currentHeartRate = session.currentHr,
            avgHeartRate = session.avgHr(),
            maxHeartRate = session.maxHr.takeIf { it > 0 },
            sessionId = session.id,
            activityId = session.activityId
        )
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NotificationManager::class.java)
            mgr.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.tracking_notification_channel),
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
    }

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.tracking_notification_title))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_stat_record)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun updateNotification() {
        val session = Session.current
        val text = when {
            session == null -> "Recording…"
            session.paused -> "Paused"
            session.lastSplitKm > 0 && (System.currentTimeMillis() - session.lastSplitTimeMs) < 15_000L ->
                "KM ${session.lastSplitKm} completed! · ${String.format(Locale.US, "%.2f km", session.distanceMeters / 1000.0)}"
            else -> String.format(Locale.US, "%.2f km · recording", session.distanceMeters / 1000.0)
        }
        val mgr = getSystemService(NotificationManager::class.java)
        mgr.notify(NOTIF_ID, buildNotification(text))
    }

    override fun onDestroy() {
        ticker?.cancel()
        stopLocationUpdates()
        stopHeartRate()
        // A surviving process can resume this session with a new service scope.
        // Do not leave a cancelled final write locked in the Saving state.
        Session.current?.let { session ->
            if (!session.paused && !session.finishing) {
                session.movingAccumulated += (System.currentTimeMillis() - session.lastResumeAt).coerceAtLeast(0L)
            }
            session.paused = true
            session.lastLocation = null
            session.lastElev = null
            session.releaseFinish()
            publish()
        }
        if (Session.current == null) isRunning.value = false
        super.onDestroy()
    }

    /**
     * In-memory workout session. Process-scoped so pause/resume and service
     * instance churn do not reset distance, elevation, or timers.
     */
    private class Session(
        val id: Long,
        var activityType: String,
        val startedAt: Long,
        var lastResumeAt: Long,
        var movingAccumulated: Long = 0L,
        var paused: Boolean = false,
        val path: MutableList<ActivityPoint> = mutableListOf(),
        var lastLocation: Location? = null,
        var distanceMeters: Double = 0.0,
        var elevGain: Double = 0.0,
        var lastElev: Double? = null,
        var maxSpeed: Double = 0.0,
        var hrSum: Long = 0L,
        var hrCount: Int = 0,
        var maxHr: Int = 0,
        var currentHr: Int? = null,
        var lastSplitKm: Int = 0,
        var lastSplitTimeMs: Long = startedAt,
        var activityId: Long = 0L,
        val writer: RecordingWriter
    ) {
        private val finishClaimed = AtomicBoolean(false)
        val finishing: Boolean get() = finishClaimed.get()

        fun claimFinish(): Boolean = finishClaimed.compareAndSet(false, true)
        fun releaseFinish() { finishClaimed.set(false) }

        fun avgHr(): Int? =
            if (hrCount > 0) (hrSum / hrCount).toInt() else null

        companion object {
            @Volatile
            var current: Session? = null
        }
    }

    companion object {
        const val ACTION_START = "com.mono.fitness.tracking.START"
        const val ACTION_PAUSE = "com.mono.fitness.tracking.PAUSE"
        const val ACTION_RESUME = "com.mono.fitness.tracking.RESUME"
        const val ACTION_STOP = "com.mono.fitness.tracking.STOP"
        const val EXTRA_TYPE = "type"
        const val EXTRA_ACTIVITY_ID = "activity_id"

        private const val CHANNEL_ID = "mono_tracking"
        private const val NOTIF_ID = 42

        private val nextSessionId = AtomicLong(0)

        val liveState = MutableStateFlow(TrackingSnapshot())
        val lastFinished = MutableStateFlow<TrackingSnapshot?>(null)
        val isRunning = MutableStateFlow(false)
        val saveError = MutableStateFlow<String?>(null)
        private val companionHrConnected = MutableStateFlow(false)
        val isHrConnected: StateFlow<Boolean> = companionHrConnected.asStateFlow()
    }
}

data class TrackingSnapshot(
    val type: String = ActivityType.RUN.name,
    val points: List<ActivityPoint> = emptyList(),
    val distanceMeters: Double = 0.0,
    val durationMillis: Long = 0L,
    val movingTimeMillis: Long = 0L,
    val elevationGainMeters: Double = 0.0,
    val maxSpeedMps: Double = 0.0,
    val startTimeMillis: Long = 0L,
    val endTimeMillis: Long = 0L,
    val paused: Boolean = false,
    val recording: Boolean = false,
    val saving: Boolean = false,
    val currentHeartRate: Int? = null,
    val avgHeartRate: Int? = null,
    val maxHeartRate: Int? = null,
    /** Monotonic id for this workout; used to dedupe finish saves. */
    val sessionId: Long = 0L,
    val activityId: Long = 0L
)

object TrackingController {
    val state = TrackingService.liveState
    val finished = TrackingService.lastFinished
    val running = TrackingService.isRunning
    val saveError = TrackingService.saveError
    val hrConnected = TrackingService.isHrConnected

    /** Last saved session id that was handed to the UI for navigation. */
    private val consumedFinishedSessionId = AtomicLong(-1L)

    private fun send(context: android.content.Context, action: String, configure: Intent.() -> Unit = {}) {
        val i = Intent(context, TrackingService::class.java).apply {
            this.action = action
            configure()
        }
        // Always use startForegroundService on O+ so pause/resume/stop reach the
        // FGS reliably (plain startService can fail or spawn a cold instance).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(i)
        } else {
            context.startService(i)
        }
    }

    fun start(context: android.content.Context, type: ActivityType, activityId: Long = 0L) {
        send(context, TrackingService.ACTION_START) {
            putExtra(TrackingService.EXTRA_TYPE, type.name)
            putExtra(TrackingService.EXTRA_ACTIVITY_ID, activityId)
        }
    }

    fun pause(context: android.content.Context) {
        send(context, TrackingService.ACTION_PAUSE)
    }

    fun resume(context: android.content.Context) {
        send(context, TrackingService.ACTION_RESUME)
    }

    fun stop(context: android.content.Context) {
        send(context, TrackingService.ACTION_STOP)
    }

    fun clearFinished() {
        TrackingService.lastFinished.value = null
    }

    /**
     * Atomically claim an already-persisted snapshot for navigation. Returns the snapshot
     * once; any later call with the same [TrackingSnapshot.sessionId] returns null
     * so the activity feed cannot get two identical rows.
     */
    @Synchronized
    fun consumeFinished(): TrackingSnapshot? {
        val fin = TrackingService.lastFinished.value ?: return null
        TrackingService.lastFinished.value = null
        if (fin.sessionId != 0L) {
            val prev = consumedFinishedSessionId.get()
            if (fin.sessionId == prev) return null
            consumedFinishedSessionId.set(fin.sessionId)
        }
        return fin
    }
}
