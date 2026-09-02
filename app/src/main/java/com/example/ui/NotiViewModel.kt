package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.AppRepository
import com.example.data.entity.CapturedNotification
import com.example.data.entity.MonitoredApp
import com.example.data.entity.NoteItem
import com.example.util.AppInfoItem
import com.example.util.InstalledAppsHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NotiViewModel(
    application: Application,
    private val repository: AppRepository
) : AndroidViewModel(application) {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _appFilter = MutableStateFlow<String?>(null)
    val appFilter: StateFlow<String?> = _appFilter.asStateFlow()

    private val _isPermissionGranted = MutableStateFlow(false)
    val isPermissionGranted: StateFlow<Boolean> = _isPermissionGranted.asStateFlow()

    private val _rawInstalledApps = MutableStateFlow<List<AppInfoItem>>(emptyList())
    private val _appsSearchQuery = MutableStateFlow("")
    val appsSearchQuery: StateFlow<String> = _appsSearchQuery.asStateFlow()

    // Notes list with search query & app filter applied
    val notes: StateFlow<List<NoteItem>> = combine(
        repository.allNotes,
        _searchQuery,
        _appFilter
    ) { allNotes, query, filter ->
        allNotes.filter { note ->
            val matchesQuery = query.isBlank() ||
                    note.title.contains(query, ignoreCase = true) ||
                    note.content.contains(query, ignoreCase = true) ||
                    (note.sourceAppName?.contains(query, ignoreCase = true) == true)

            val matchesFilter = filter == null || note.sourcePackage == filter
            matchesQuery && matchesFilter
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Captured notifications stream
    val capturedNotifications: StateFlow<List<CapturedNotification>> = repository.allNotifications
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Monitored apps
    val monitoredApps: StateFlow<List<MonitoredApp>> = repository.allMonitoredApps
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Installed apps mapped with monitoring status and filtered by search
    val installedAppsWithStatus: StateFlow<List<AppInfoItem>> = combine(
        _rawInstalledApps,
        monitoredApps,
        _appsSearchQuery
    ) { installed, monitored, query ->
        val monitoredMap = monitored.associateBy { it.packageName }
        installed
            .map { app ->
                val record = monitoredMap[app.packageName]
                app.copy(
                    isMonitored = record?.isEnabled == true,
                    autoSaveToNotes = record?.autoSaveToNotes == true
                )
            }
            .filter { app ->
                query.isBlank() ||
                        app.appName.contains(query, ignoreCase = true) ||
                        app.packageName.contains(query, ignoreCase = true)
            }
            // Sort: monitored first, then alphabetically
            .sortedWith(compareByDescending<AppInfoItem> { it.isMonitored }.thenBy { it.appName.lowercase() })
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        checkPermission()
        loadInstalledApps()
    }

    fun checkPermission() {
        _isPermissionGranted.value = InstalledAppsHelper.isNotificationAccessGranted(getApplication())
    }

    fun loadInstalledApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val apps = InstalledAppsHelper.getInstalledLaunchableApps(getApplication())
            _rawInstalledApps.value = apps

            // If it's the very first time and no monitored apps exist yet, pre-populate monitored status
            // with common messaging/mail applications if found on device
            val existing = repository.allMonitoredApps
            // check if empty
            // we will let user select freely
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun onAppFilterChange(packageName: String?) {
        _appFilter.value = packageName
    }

    fun onAppsSearchQueryChange(query: String) {
        _appsSearchQuery.value = query
    }

    fun saveNotificationToNotes(notification: CapturedNotification, customTitle: String? = null) {
        viewModelScope.launch {
            repository.saveNotificationAsNote(notification, customTitle)
        }
    }

    fun deleteNotification(id: Long) {
        viewModelScope.launch {
            repository.deleteNotificationById(id)
        }
    }

    fun clearAllNotifications() {
        viewModelScope.launch {
            repository.clearAllNotifications()
        }
    }

    fun saveNote(
        id: Long = 0,
        title: String,
        content: String,
        sourcePackage: String? = null,
        sourceAppName: String? = null,
        colorIndex: Int = 0
    ) {
        viewModelScope.launch {
            if (id == 0L) {
                val newNote = NoteItem(
                    title = title.trim(),
                    content = content.trim(),
                    sourcePackage = sourcePackage,
                    sourceAppName = sourceAppName,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    colorTagIndex = colorIndex
                )
                repository.insertNote(newNote)
            } else {
                val updatedNote = NoteItem(
                    id = id,
                    title = title.trim(),
                    content = content.trim(),
                    sourcePackage = sourcePackage,
                    sourceAppName = sourceAppName,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    colorTagIndex = colorIndex
                )
                repository.updateNote(updatedNote)
            }
        }
    }

    fun deleteNote(note: NoteItem) {
        viewModelScope.launch {
            repository.deleteNote(note)
        }
    }

    fun togglePinNote(note: NoteItem) {
        viewModelScope.launch {
            repository.togglePinNote(note.id, !note.isPinned)
        }
    }

    fun toggleAppMonitored(appItem: AppInfoItem) {
        viewModelScope.launch {
            val newMonitored = !appItem.isMonitored
            if (newMonitored) {
                repository.insertMonitoredApp(
                    MonitoredApp(
                        packageName = appItem.packageName,
                        appName = appItem.appName,
                        isEnabled = true,
                        autoSaveToNotes = appItem.autoSaveToNotes
                    )
                )
            } else {
                repository.deleteMonitoredAppByPackage(appItem.packageName)
            }
        }
    }

    fun toggleAppAutoSave(packageName: String, appName: String, autoSave: Boolean) {
        viewModelScope.launch {
            val existing = repository.getMonitoredApp(packageName)
            if (existing != null) {
                repository.updateMonitoredApp(existing.copy(autoSaveToNotes = autoSave, isEnabled = true))
            } else {
                repository.insertMonitoredApp(
                    MonitoredApp(
                        packageName = packageName,
                        appName = appName,
                        isEnabled = true,
                        autoSaveToNotes = autoSave
                    )
                )
            }
        }
    }

    fun selectAllApps(enable: Boolean) {
        viewModelScope.launch {
            if (enable) {
                val list = _rawInstalledApps.value.map { app ->
                    MonitoredApp(
                        packageName = app.packageName,
                        appName = app.appName,
                        isEnabled = true,
                        autoSaveToNotes = false
                    )
                }
                repository.insertMonitoredApps(list)
            } else {
                _rawInstalledApps.value.forEach { app ->
                    repository.deleteMonitoredAppByPackage(app.packageName)
                }
            }
        }
    }

    // Helper for testing in emulator / preview environments
    fun simulateTestNotification(appName: String = "WhatsApp", sampleTitle: String = "Juan Pérez", sampleText: String = "Hola! Te confirmo la reunión para mañana a las 3:00 PM.") {
        viewModelScope.launch {
            val pkg = "com.test.${appName.lowercase()}"
            // Ensure app is in monitored apps list
            repository.insertMonitoredApp(
                MonitoredApp(
                    packageName = pkg,
                    appName = appName,
                    isEnabled = true,
                    autoSaveToNotes = false
                )
            )

            val captured = CapturedNotification(
                packageName = pkg,
                appName = appName,
                title = sampleTitle,
                text = sampleText,
                timestamp = System.currentTimeMillis()
            )
            repository.insertNotification(captured)
        }
    }

    companion object {
        fun provideFactory(application: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val db = AppDatabase.getDatabase(application)
                    val repository = AppRepository(
                        db.monitoredAppDao(),
                        db.capturedNotificationDao(),
                        db.noteDao()
                    )
                    return NotiViewModel(application, repository) as T
                }
            }
    }
}
