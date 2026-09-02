package com.example.data

import com.example.data.dao.CapturedNotificationDao
import com.example.data.dao.MonitoredAppDao
import com.example.data.dao.NoteDao
import com.example.data.entity.CapturedNotification
import com.example.data.entity.MonitoredApp
import com.example.data.entity.NoteItem
import kotlinx.coroutines.flow.Flow

class AppRepository(
    private val monitoredAppDao: MonitoredAppDao,
    private val capturedNotificationDao: CapturedNotificationDao,
    private val noteDao: NoteDao
) {
    // Notes
    val allNotes: Flow<List<NoteItem>> = noteDao.getAllNotes()

    fun searchNotes(query: String): Flow<List<NoteItem>> = noteDao.searchNotes(query)

    suspend fun insertNote(note: NoteItem): Long = noteDao.insert(note)

    suspend fun updateNote(note: NoteItem) = noteDao.update(note)

    suspend fun deleteNote(note: NoteItem) = noteDao.delete(note)

    suspend fun deleteNoteById(id: Long) = noteDao.deleteById(id)

    suspend fun togglePinNote(id: Long, isPinned: Boolean) = noteDao.togglePin(id, isPinned)

    // Notifications
    val allNotifications: Flow<List<CapturedNotification>> = capturedNotificationDao.getAllNotifications()

    suspend fun insertNotification(notification: CapturedNotification): Long =
        capturedNotificationDao.insert(notification)

    suspend fun deleteNotificationById(id: Long) =
        capturedNotificationDao.deleteById(id)

    suspend fun clearAllNotifications() =
        capturedNotificationDao.clearAll()

    // Save a notification into notepad
    suspend fun saveNotificationAsNote(notification: CapturedNotification, customTitle: String? = null): Long {
        val title = if (!customTitle.isNullOrBlank()) {
            customTitle
        } else if (notification.title.isNotBlank()) {
            notification.title
        } else {
            "Nota de ${notification.appName}"
        }

        val content = buildString {
            append(notification.text)
            if (!notification.subText.isNullOrBlank()) {
                append("\n\n")
                append(notification.subText)
            }
        }

        val note = NoteItem(
            title = title,
            content = content,
            sourcePackage = notification.packageName,
            sourceAppName = notification.appName,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        val noteId = noteDao.insert(note)
        capturedNotificationDao.markAsSaved(notification.id)
        return noteId
    }

    // Monitored Apps
    val allMonitoredApps: Flow<List<MonitoredApp>> = monitoredAppDao.getAllMonitoredApps()

    suspend fun getMonitoredApp(packageName: String): MonitoredApp? =
        monitoredAppDao.getMonitoredApp(packageName)

    suspend fun insertMonitoredApp(app: MonitoredApp) =
        monitoredAppDao.insert(app)

    suspend fun insertMonitoredApps(apps: List<MonitoredApp>) =
        monitoredAppDao.insertAll(apps)

    suspend fun updateMonitoredApp(app: MonitoredApp) =
        monitoredAppDao.update(app)

    suspend fun deleteMonitoredApp(app: MonitoredApp) =
        monitoredAppDao.delete(app)

    suspend fun deleteMonitoredAppByPackage(packageName: String) =
        monitoredAppDao.deleteByPackage(packageName)
}
