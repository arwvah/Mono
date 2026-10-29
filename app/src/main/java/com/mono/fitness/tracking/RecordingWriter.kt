package com.mono.fitness.tracking

import com.mono.fitness.data.Activity
import com.mono.fitness.data.ActivityPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async

/**
 * Serializes immutable recording checkpoints. A failed write leaves the durable
 * point count unchanged, so the next checkpoint retries every unsaved point.
 * Call [save] from the service's main thread to preserve submission order.
 */
internal class RecordingWriter(
    private var scope: CoroutineScope,
    private val persist: suspend (Activity, List<ActivityPoint>) -> Long,
    initialActivityId: Long = 0L
) {
    private var activityId = initialActivityId
    private var savedPointCount = 0
    private var pending: Job? = null

    fun rebind(scope: CoroutineScope) {
        this.scope = scope
    }

    fun save(activity: Activity, path: List<ActivityPoint>): Deferred<Result<Long>> {
        val previous = pending
        return scope.async {
            previous?.join()
            try {
                val id = persist(activity.copy(id = activityId), path.drop(savedPointCount))
                activityId = id
                savedPointCount = path.size
                Result.success(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }.also { pending = it }
    }
}
