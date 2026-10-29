package com.mono.fitness.data

/**
 * No mock data is seeded. Mono starts with a clean on-device database,
 * persisting only user-recorded sessions and user-imported GPX tracks.
 */
object SeedData {
    @Suppress("RedundantSuspendModifier", "UNUSED_PARAMETER")
    suspend fun seedIfEmpty(repo: MonoRepository) {
        // Zero mock data. Preserves clean user database.
    }
}
