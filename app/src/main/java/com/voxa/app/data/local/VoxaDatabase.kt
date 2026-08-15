package com.voxa.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.voxa.app.data.local.dao.ItineraryDao
import com.voxa.app.data.local.entity.ItineraryEntity

@Database(entities = [ItineraryEntity::class], version = 1, exportSchema = false)
abstract class VoxaDatabase : RoomDatabase() {
    abstract fun itineraryDao(): ItineraryDao

    companion object {
        @Volatile
        private var INSTANCE: VoxaDatabase? = null

        fun getDatabase(context: Context): VoxaDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    VoxaDatabase::class.java,
                    "voxa_database"
                )
                .setJournalMode(JournalMode.TRUNCATE) // Reduces temp files and potential leaks
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
