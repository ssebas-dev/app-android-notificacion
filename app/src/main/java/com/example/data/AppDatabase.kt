package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.dao.CapturedNotificationDao
import com.example.data.dao.MonitoredAppDao
import com.example.data.dao.NoteDao
import com.example.data.entity.CapturedNotification
import com.example.data.entity.MonitoredApp
import com.example.data.entity.NoteItem

@Database(
    entities = [
        MonitoredApp::class,
        CapturedNotification::class,
        NoteItem::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun monitoredAppDao(): MonitoredAppDao
    abstract fun capturedNotificationDao(): CapturedNotificationDao
    abstract fun noteDao(): NoteDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "notinotas_database"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
