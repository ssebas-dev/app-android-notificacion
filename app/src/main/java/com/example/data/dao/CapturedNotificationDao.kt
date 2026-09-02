package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.entity.CapturedNotification
import kotlinx.coroutines.flow.Flow

@Dao
interface CapturedNotificationDao {
    @Query("SELECT * FROM captured_notifications ORDER BY timestamp DESC")
    fun getAllNotifications(): Flow<List<CapturedNotification>>

    @Query("SELECT * FROM captured_notifications WHERE packageName = :packageName ORDER BY timestamp DESC")
    fun getNotificationsByPackage(packageName: String): Flow<List<CapturedNotification>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(notification: CapturedNotification): Long

    @Update
    suspend fun update(notification: CapturedNotification)

    @Query("UPDATE captured_notifications SET isSavedAsNote = 1 WHERE id = :id")
    suspend fun markAsSaved(id: Long)

    @Query("DELETE FROM captured_notifications WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM captured_notifications")
    suspend fun clearAll()
}
