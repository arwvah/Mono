package com.mono.fitness

import android.app.Application
import com.mono.fitness.data.MonoDatabase
import com.mono.fitness.data.MonoRepository
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
            if (!prefs.getBoolean("prs_rebuilt_v3", false)) {
                repository.recalculatePersonalRecords()
                prefs.edit().putBoolean("prs_rebuilt_v3", true).apply()
            }
        }
    }
}
