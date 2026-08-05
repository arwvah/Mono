package com.mono.fitness

import android.app.Application
import com.mono.fitness.data.MonoDatabase
import com.mono.fitness.data.MonoRepository
import com.mono.fitness.data.SeedData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MonoApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var repository: MonoRepository
        private set

    override fun onCreate() {
        super.onCreate()
        val db = MonoDatabase.get(this)
        repository = MonoRepository(db)
        appScope.launch {
            val prefs = getSharedPreferences("mono_prefs", MODE_PRIVATE)
            if (!prefs.getBoolean("prs_cleared_v2", false)) {
                repository.clearPersonalRecords()
                prefs.edit().putBoolean("prs_cleared_v2", true).apply()
            }
        }
        if (BuildConfig.SEED_DATA) {
            appScope.launch {
                SeedData.seedIfEmpty(repository)
            }
        }
    }
}
