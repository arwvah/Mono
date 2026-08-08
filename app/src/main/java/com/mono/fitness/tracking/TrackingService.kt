package com.mono.fitness.tracking

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.mono.fitness.MainActivity
import com.mono.fitness.R
import com.mono.fitness.data.ActivityPoint
import com.mono.fitness.data.ActivityType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
    private var hrClient: HeartRateBleClient? = null
    private var locationsRequested: Boolean = false

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val session = Session.current ?: return
            if (session.paused) return
            val loc = result.lastLocation ?: return
            onLocation(session, loc)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> {
                val type = intent.getStringExtra(EXTRA_TYPE) ?: ActivityType.RUN.name
                startRecording(type)
            }
            ACTION_PAUSE -> pause()
            ACTION_RESUME -> resume()
            ACTION_STOP -> stopAndFinish()
            null -> {
                // Process/service restart with no action: keep an in-progress session alive.
                if (Session.current != null) {
                    ensureForeground()
                    requestLocations()
                    publish()
                    isRunning.value = true
                }
            }
        }
        // NOT_STICKY: do not redeliver START (which would look like a fresh session).
        return START_NOT_STICKY
    }

    private fun startRecording(type: String) {
        // Duplicate START (double-tap / accidental redelivery) must not wipe progress.
        val existing = Session.current
        if (existing != null && !existing.finishing) {
            ensureForeground()
            requestLocations()
            publish()
            isRunning.value = true
            return
        }

        val now = System.currentTimeMillis()
        Session.current = Session(
            id = nextSessionId.incrementAndGet(),
            activityType = type,
            startedAt = now,
            lastResumeAt = now
        )
        ensureChannel()
        ensureForeground()
        requestLocations()
        startHeartRate()
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

    private fun startHeartRate() {
        try {
            hrClient?.stop()
            hrClient = HeartRateBleClient(this).also { client ->
                client.start()
                lifecycleScope.launch {
                    companionHrConnected.value = true
                    client.bpm.collect { bpm ->
                        val session = Session.current ?: return@collect
                        if (bpm != null && !session.paused && !session.finishing) {
                            session.currentHr = bpm
                            session.hrSum += bpm
                            session.hrCount++
                            session.maxHr = maxOf(session.maxHr, bpm)
                            publish()
                        }
                    }
                }
            }
        } catch (_: Exception) {
            companionHrConnected.value = false
            hrClient = null
        }
    }

    private fun stopHeartRate() {
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
        val elev = if (loc.hasAltitude()) loc.altitude else null
        session.lastLocation?.let { prev ->
            val d = prev.distanceTo(loc).toDouble()
            if (d < 100) session.distanceMeters += d // filter GPS jumps
        }
        elev?.let { e ->
            session.lastElev?.let { prev ->
                val g = e - prev
                if (g > 0 && g < 30) session.elevGain += g
            }
            session.lastElev = e
        }
        if (loc.hasSpeed()) {
            session.maxSpeed = maxOf(session.maxSpeed, loc.speed.toDouble())
        }
        session.lastLocation = loc
        session.path += ActivityPoint(
            activityId = 0,
            latitude = loc.latitude,
            longitude = loc.longitude,
            elevationMeters = elev,
            timestampMillis = loc.time.takeIf { it > 0 } ?: System.currentTimeMillis(),
            speedMps = if (loc.hasSpeed()) loc.speed.toDouble() else null,
            accuracyMeters = if (loc.hasAccuracy()) loc.accuracy else null,
            sequence = session.path.size
        )
        publish()
        updateNotification()
    }

    /**
     * startForegroundService() requires startForeground() quickly. Control intents
     * on a cold service with no session promote briefly then tear down.
     */
    private fun acknowledgeForegroundOrStopIfIdle(): Boolean {
        ensureChannel()
        ensureForeground()
        val session = Session.current
        if (session != null && !session.finishing) return true
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
        session.movingAccumulated += System.currentTimeMillis() - session.lastResumeAt
        // Keep GPS subscribed while paused so the location FGS stays valid and
        // resume does not re-bind from a wiped / half-dead service. Callback
        // already ignores fixes when paused == true.
        // Drop last fix so the first point after resume does not add pause travel.
        session.lastLocation = null
        publish()
        updateNotification()
    }

    private fun resume() {
        if (!acknowledgeForegroundOrStopIfIdle()) return
        val session = Session.current ?: return
        if (!session.paused) {
            requestLocations()
            publish()
            updateNotification()
            return
        }
        session.paused = false
        session.lastResumeAt = System.currentTimeMillis()
        session.lastLocation = null
        requestLocations()
        // Ensure UI / running flag stay consistent after a service rebind.
        isRunning.value = true
        publish()
        updateNotification()
    }

    private fun stopAndFinish() {
        // startForegroundService(STOP) still requires a startForeground call.
        ensureChannel()
        ensureForeground()

        val session = Session.current
        // Guard against double Finish / second service instance: only the first
        // successful claim publishes a finished snapshot.
        if (session == null || session.finishing || !session.claimFinish()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        stopLocationUpdates()
        stopHeartRate()
        if (!session.paused) {
            session.movingAccumulated += System.currentTimeMillis() - session.lastResumeAt
        }
        val end = System.currentTimeMillis()
        val duration = (end - session.startedAt).coerceAtLeast(0L)
        val moving = session.movingAccumulated.coerceAtLeast(1L)
        val snapshot = TrackingSnapshot(
            type = session.activityType,
            points = session.path.toList(),
            distanceMeters = session.distanceMeters,
            durationMillis = duration,
            movingTimeMillis = moving,
            elevationGainMeters = session.elevGain,
            maxSpeedMps = session.maxSpeed,
            startTimeMillis = session.startedAt,
            endTimeMillis = end,
            paused = false,
            recording = false,
            currentHeartRate = session.currentHr,
            avgHeartRate = session.avgHr(),
            maxHeartRate = session.maxHr.takeIf { it > 0 },
            sessionId = session.id
        )
        // Publish finished once, then clear live session.
        lastFinished.value = snapshot
        liveState.value = snapshot
        isRunning.value = false
        Session.current = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun publish() {
        val session = Session.current ?: return
        val now = System.currentTimeMillis()
        val moving = if (session.paused) {
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
            currentHeartRate = session.currentHr,
            avgHeartRate = session.avgHr(),
            maxHeartRate = session.maxHr.takeIf { it > 0 },
            sessionId = session.id
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
            else -> String.format("%.2f km · recording", session.distanceMeters / 1000.0)
        }
        val mgr = getSystemService(NotificationManager::class.java)
        mgr.notify(NOTIF_ID, buildNotification(text))
    }

    override fun onDestroy() {
        stopLocationUpdates()
        stopHeartRate()
        // Only clear running if we did not already hand off a finished snapshot.
        // Keep Session.current so a quick rebind can continue the same workout.
        if (Session.current?.finishing != true && lastFinished.value == null) {
            // If the session is still active, leave isRunning true so the UI does
            // not flash READY with zeros while the process still holds Session.
            if (Session.current == null) {
                isRunning.value = false
            }
        }
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
        var currentHr: Int? = null
    ) {
        private val finishClaimed = AtomicBoolean(false)
        val finishing: Boolean get() = finishClaimed.get()

        fun claimFinish(): Boolean = finishClaimed.compareAndSet(false, true)

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

        private const val CHANNEL_ID = "mono_tracking"
        private const val NOTIF_ID = 42

        private val nextSessionId = AtomicLong(0)

        val liveState = MutableStateFlow(TrackingSnapshot())
        val lastFinished = MutableStateFlow<TrackingSnapshot?>(null)
        val isRunning = MutableStateFlow(false)
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
    val currentHeartRate: Int? = null,
    val avgHeartRate: Int? = null,
    val maxHeartRate: Int? = null,
    /** Monotonic id for this workout; used to dedupe finish saves. */
    val sessionId: Long = 0L
)

object TrackingController {
    val state = TrackingService.liveState
    val finished = TrackingService.lastFinished
    val running = TrackingService.isRunning
    val hrConnected = TrackingService.isHrConnected

    /** Last session id that was successfully handed to the UI for saving. */
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

    fun start(context: android.content.Context, type: ActivityType) {
        send(context, TrackingService.ACTION_START) {
            putExtra(TrackingService.EXTRA_TYPE, type.name)
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
     * Atomically claim a finished snapshot for persistence. Returns the snapshot
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
