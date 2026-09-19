package com.focusbyrj.app.ui.viewmodels

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.focusbyrj.app.data.AppRepository
import com.focusbyrj.app.data.AppRestriction
import com.focusbyrj.app.data.FocusSchedule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

class FocusViewModel(private val repository: AppRepository, application: Application) : AndroidViewModel(application) {
    override fun onCleared() {
        super.onCleared()
        if (_isSessionActive.value) {
            _isSessionActive.value = false
            prefs.edit().putBoolean("isSessionActive", false).apply()
            com.focusbyrj.app.util.DndHelper.setDndMode(getApplication(), false)
        }
    }

    private val _pendingNavigationRoute = MutableStateFlow<String?>(null)
    val pendingNavigationRoute: StateFlow<String?> = _pendingNavigationRoute

    private val _pendingOpenAddDialog = MutableStateFlow(false)
    val pendingOpenAddDialog: StateFlow<Boolean> = _pendingOpenAddDialog

    private val _pendingOpenNoteId = MutableStateFlow<Long?>(null)
    val pendingOpenNoteId: StateFlow<Long?> = _pendingOpenNoteId

    private val _pendingOpenNewNote = MutableStateFlow<Boolean?>(null) // true if checklist, false if standard note
    val pendingOpenNewNote: StateFlow<Boolean?> = _pendingOpenNewNote

    private val _pendingOpenVoiceNote = MutableStateFlow(false)
    val pendingOpenVoiceNote: StateFlow<Boolean> = _pendingOpenVoiceNote

    fun triggerNavigation(route: String) {
        _pendingNavigationRoute.value = route
    }

    fun clearNavigation() {
        _pendingNavigationRoute.value = null
    }

    fun triggerOpenAddDialog() {
        _pendingOpenAddDialog.value = true
    }

    fun clearOpenAddDialog() {
        _pendingOpenAddDialog.value = false
    }

    fun triggerOpenNote(noteId: Long) {
        _pendingNavigationRoute.value = "empty"
        _pendingOpenNoteId.value = noteId
    }

    fun clearOpenNote() {
        _pendingOpenNoteId.value = null
    }

    fun triggerOpenNewNote(asChecklist: Boolean = false) {
        _pendingNavigationRoute.value = "empty"
        _pendingOpenNewNote.value = asChecklist
    }

    fun clearOpenNewNote() {
        _pendingOpenNewNote.value = null
    }

    fun triggerOpenVoiceNote() {
        _pendingNavigationRoute.value = "empty"
        _pendingOpenVoiceNote.value = true
    }

    fun clearOpenVoiceNote() {
        _pendingOpenVoiceNote.value = false
    }

    
    private val prefs = application.getSharedPreferences("focus_prefs", Context.MODE_PRIVATE)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            repository.cleanUninstalledPackages(application.packageManager)
        }
    }

    private val minuteTicker = flow {
        while (true) {
            emit(Unit)
            delay(60000)
        }
    }

    val combinedRestrictions: StateFlow<List<AppRestriction>> = combine(
        repository.allRestrictions, 
        repository.allSchedules,
        minuteTicker
    ) { rests, scheds, _ ->
        val pm = application.packageManager
        val map = rests.filter {
            try {
                pm.getApplicationInfo(it.packageName, 0)
                true
            } catch (e: Exception) {
                false
            }
        }.associateBy { it.packageName }.toMutableMap()

        val calendar = Calendar.getInstance()
        val currentDay = calendar.get(Calendar.DAY_OF_WEEK)
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val currentMinute = calendar.get(Calendar.MINUTE)
        val currentTotalMinutes = currentHour * 60 + currentMinute

        for (s in scheds) {
            if (!s.isEnabled) continue
            val isActiveNow = s.isActiveAt(calendar)

            val entries = s.appsToBlock.split(",").filter { it.isNotBlank() }
            for (entry in entries) {
                val parts = entry.split("|")
                val pkg = parts[0].trim()
                if (pkg.isEmpty()) continue

                val appInfo = try { pm.getApplicationInfo(pkg, 0) } catch (e: Exception) { null }
                if (appInfo == null) continue // Skip uninstalled apps from routine

                val appMode = if (parts.size > 1) parts[1] else s.mode
                if (!map.containsKey(pkg)) {
                    val appName = try { pm.getApplicationLabel(appInfo).toString() } catch(e: Exception) { pkg }
                    val newRest = AppRestriction(
                        packageName = pkg,
                        appName = appName,
                        isRestricted = isActiveNow,
                        mode = appMode,
                        restrictionMode = s.restrictionMode,
                        timeLimitMinutes = s.timeLimitMinutes,
                        clickLimitCount = s.clickLimitCount,
                        customQuote = ""
                    )
                    newRest.isFromRoutine = true
                    map[pkg] = newRest
                } else if (isActiveNow) {
                    val existing = map[pkg]!!
                    val updated = existing.copy(
                        isRestricted = true,
                        mode = appMode,
                        restrictionMode = s.restrictionMode,
                        timeLimitMinutes = s.timeLimitMinutes,
                        clickLimitCount = s.clickLimitCount
                    )
                    updated.isFromRoutine = existing.isFromRoutine
                    map[pkg] = updated
                }
            }
        }
        map.values.toList().sortedBy { it.appName }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )
    
    val schedules: StateFlow<List<FocusSchedule>> = repository.allSchedules.stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = emptyList())

    fun addSchedule(
        name: String,
        startHour: Int,
        startMinute: Int,
        endHour: Int,
        endMinute: Int,
        daysOfWeek: String,
        mode: String,
        appsToBlock: String,
        restrictionMode: String = "SIMPLE",
        timeLimitMinutes: Int = 0,
        clickLimitCount: Int = 0,
        isEnabled: Boolean = true
    ) {
        viewModelScope.launch {
            repository.insertSchedule(
                FocusSchedule(
                    name = name,
                    startHour = startHour,
                    startMinute = startMinute,
                    endHour = endHour,
                    endMinute = endMinute,
                    daysOfWeek = daysOfWeek,
                    mode = mode,
                    appsToBlock = appsToBlock,
                    restrictionMode = restrictionMode,
                    timeLimitMinutes = timeLimitMinutes,
                    clickLimitCount = clickLimitCount,
                    isEnabled = isEnabled
                )
            )
            com.focusbyrj.app.util.FocusStatsManager.addRoutineActivity(getApplication(), 15L)
        }
    }
    
    fun toggleSchedule(schedule: FocusSchedule) {
        updateSchedule(schedule.copy(isEnabled = !schedule.isEnabled))
    }

    fun updateSchedule(schedule: FocusSchedule) {
        viewModelScope.launch {
            repository.insertSchedule(schedule)
        }
    }
    
    fun deleteSchedule(schedule: FocusSchedule) {
        viewModelScope.launch {
            repository.deleteSchedule(schedule)
        }
    }

    private val _isSessionActive = kotlinx.coroutines.flow.MutableStateFlow(false)
    val isSessionActive: StateFlow<Boolean> = _isSessionActive

    private val _timeRemaining = kotlinx.coroutines.flow.MutableStateFlow(25 * 60L)
    val timeRemaining: StateFlow<Long> = _timeRemaining

    private val _initialTime = kotlinx.coroutines.flow.MutableStateFlow(25 * 60L)
    val initialTime: StateFlow<Long> = _initialTime

    private var timerJob: kotlinx.coroutines.Job? = null

    fun setTimeRemaining(minutes: Int) {
        if (!_isSessionActive.value) {
            val seconds = minutes * 60L
            _timeRemaining.value = seconds
            _initialTime.value = seconds
        }
    }

    fun toggleFocusSession() {
        val currentlyActive = _isSessionActive.value
        timerJob?.cancel()
        timerJob = null

        if (currentlyActive) {
            _isSessionActive.value = false
            _timeRemaining.value = _initialTime.value
            prefs.edit().putBoolean("isSessionActive", false).apply()
            com.focusbyrj.app.util.DndHelper.setDndMode(getApplication(), false)
            com.focusbyrj.app.util.FocusStatsManager.refreshStats(getApplication())
        } else {
            _isSessionActive.value = true
            prefs.edit().putBoolean("isSessionActive", true).apply()
            timerJob = viewModelScope.launch {
                var totalElapsedMins = 0
                var secondsAccumulator = 0
                var statsSecs = 0
                
                while (_timeRemaining.value > 0 && _isSessionActive.value) {
                    kotlinx.coroutines.delay(1000)
                    if (!_isSessionActive.value) break
                    _timeRemaining.value -= 1
                    secondsAccumulator++
                    statsSecs++
                    
                    if (statsSecs >= 10) {
                        com.focusbyrj.app.util.FocusStatsManager.addFocusSessionTime(getApplication(), statsSecs.toLong())
                        statsSecs = 0
                    }
                    
                    if (secondsAccumulator >= 300) { // 5 minutes
                        secondsAccumulator = 0
                        totalElapsedMins += 5
                        com.focusbyrj.app.util.FocusEconomyManager.addDurationBasedRewards(totalElapsedMins)
                        com.focusbyrj.app.util.FocusEconomyManager.addLifetimeFocusMins(5)
                    }
                }
                
                if (statsSecs > 0) {
                    com.focusbyrj.app.util.FocusStatsManager.addFocusSessionTime(getApplication(), statsSecs.toLong())
                }
                if (_timeRemaining.value <= 0L) {
                    _isSessionActive.value = false
                    _timeRemaining.value = _initialTime.value
                    prefs.edit().putBoolean("isSessionActive", false).apply()
                    com.focusbyrj.app.util.DndHelper.setDndMode(getApplication(), false)
                    com.focusbyrj.app.util.FocusStatsManager.refreshStats(getApplication())
                }
            }
        }
    }

    fun toggleRestriction(app: AppRestriction) {
        viewModelScope.launch {
            repository.toggleRestriction(app)
            if (!app.isRestricted) {
                com.focusbyrj.app.util.FocusStatsManager.addAppRestrictionActivity(getApplication(), 1)
            }
        }
    }

    fun addRestriction(app: AppRestriction) {
        viewModelScope.launch {
            repository.saveApp(app)
            com.focusbyrj.app.util.FocusStatsManager.addAppRestrictionActivity(getApplication(), 1)
        }
    }

    fun addRestrictions(apps: List<AppRestriction>) {
        viewModelScope.launch {
            apps.forEach { app -> repository.saveApp(app) }
            com.focusbyrj.app.util.FocusStatsManager.addAppRestrictionActivity(getApplication(), apps.size)
        }
    }

    fun updateRestriction(app: AppRestriction) {
        viewModelScope.launch {
            repository.saveApp(app)
        }
    }

    fun deleteRestriction(app: AppRestriction) {
        viewModelScope.launch {
            repository.deleteRestriction(app)
        }
    }
}

class FocusViewModelFactory(private val repository: AppRepository, private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FocusViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return FocusViewModel(repository, application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
