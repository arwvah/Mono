package com.mono.fitness.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        Activity::class,
        ActivityPoint::class,
        Route::class,
        RoutePoint::class,
        PersonalRecord::class
    ],
    version = 1,
    exportSchema = false
)
abstract class MonoDatabase : RoomDatabase() {
    abstract fun activityDao(): ActivityDao
    abstract fun activityPointDao(): ActivityPointDao
    abstract fun routeDao(): RouteDao
    abstract fun routePointDao(): RoutePointDao
    abstract fun personalRecordDao(): PersonalRecordDao

    companion object {
        @Volatile
        private var INSTANCE: MonoDatabase? = null

        fun get(context: Context): MonoDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    MonoDatabase::class.java,
                    "mono.db"
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
        }
    }
}
