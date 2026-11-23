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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

class FocusViewModel(private val repository: AppRepository, application: Application) : AndroidViewModel(application) {
    private val _pendingNavigationRoute = MutableStateFlow<String?>(null)
    val pendingNavigationRoute: StateFlow<String?> = _pendingNavigationRoute

    private val _pendingOpenAddDialog = MutableStateFlow<String?>(null)
    val pendingOpenAddDialog: StateFlow<String?> = _pendingOpenAddDialog

    private val _pendingOpenNoteId = MutableStateFlow<Long?>(null)
    val pendingOpenNoteId: StateFlow<Long?> = _pendingOpenNoteId
    private val _pendingNoteReturnRoute = MutableStateFlow<String?>(null)
    val pendingNoteReturnRoute: StateFlow<String?> = _pendingNoteReturnRoute

    private val _pendingOpenNewNote = MutableStateFlow<Boolean?>(null) // true if checklist, false if standard note
    val pendingOpenNewNote: StateFlow<Boolean?> = _pendingOpenNewNote

    private val _pendingOpenVoiceNote = MutableStateFlow(false)
    val pendingOpenVoiceNote: StateFlow<Boolean> = _pendingOpenVoiceNote

    fun triggerNavigation(route: String) {
        clearPendingEditors()
        _pendingNavigationRoute.value = route
    }

    fun cancelPendingLaunchRequests() {
        clearPendingEditors()
        _pendingNavigationRoute.value = null
    }

    fun clearNavigation() {
        _pendingNavigationRoute.value = null
    }

    fun triggerOpenAddDialog(route: String = "todos") {
        _pendingOpenAddDialog.value = if (route == "habits") "habits" else "todos"
    }

    fun clearOpenAddDialog() {
        _pendingOpenAddDialog.value = null
    }

    private val _voiceInputRequested = MutableStateFlow(false)
    val voiceInputRequested: StateFlow<Boolean> = _voiceInputRequested
    fun requestVoiceInput() { _voiceInputRequested.value = true }
    fun clearVoiceInput() { _voiceInputRequested.value = false }

    private val _launchRequestSerial = MutableStateFlow(0L)
    val launchRequestSerial: StateFlow<Long> = _launchRequestSerial

    private fun clearPendingEditors() {
        _launchRequestSerial.value++
        _pendingOpenAddDialog.value = null
        _pendingOpenNoteId.value = null
        _pendingNoteReturnRoute.value = null
        _pendingOpenNewNote.value = null
        _pendingOpenVoiceNote.value = false
        _voiceInputRequested.value = false
    }

    fun triggerOpenNote(noteId: Long, returnRoute: String? = null) {
        clearPendingEditors()
        _pendingNavigationRoute.value = "empty"
        _pendingNoteReturnRoute.value = returnRoute
        _pendingOpenNoteId.value = noteId
    }

    fun clearOpenNote() {
        _pendingOpenNoteId.value = null
        _pendingNoteReturnRoute.value = null
    }

    fun triggerOpenNewNote(asChecklist: Boolean = false) {
        clearPendingEditors()
        _pendingNavigationRoute.value = "empty"
        _pendingOpenNewNote.value = asChecklist
    }

    fun clearOpenNewNote() {
        _pendingOpenNewNote.value = null
    }

    fun triggerOpenVoiceNote() {
        clearPendingEditors()
        _pendingNavigationRoute.value = "empty"
        _pendingOpenVoiceNote.value = true
    }

    fun clearOpenVoiceNote() {
        _pendingOpenVoiceNote.value = false
    }

    
    private val prefs = application.getSharedPreferences("focus_prefs", Context.MODE_PRIVATE)

    init {
        com.focusbyrj.app.util.FocusSessionManager.init(application)
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

        val routineRules = com.focusbyrj.app.data.BlockingRules.routines(scheds, Calendar.getInstance()).groupBy { it.packageName }
        for ((pkg, rules) in routineRules) {
            val info = try { pm.getApplicationInfo(pkg, 0) } catch (_: Exception) { continue }
            val manual = map[pkg]
            val active = rules + listOfNotNull(manual?.takeIf { it.isRestricted }?.let(com.focusbyrj.app.data.BlockingRules::manual))
            if (manual != null) {
                // Keep the manual row intact so editing/toggling it cannot overwrite a routine.
                map[pkg] = manual.copy().also { it.ruleSummary = "Active: " + com.focusbyrj.app.data.BlockingRules.summary(active) }
            } else {
                val selected = com.focusbyrj.app.data.BlockingRules.preview(rules) ?: continue
                map[pkg] = AppRestriction(pkg, pm.getApplicationLabel(info).toString(), true,
                    selected.mode, selected.restrictionMode, selected.minutes, selected.clicks).also {
                    it.isFromRoutine = true
                    it.ruleSummary = "Active: " + com.focusbyrj.app.data.BlockingRules.summary(active) + ". Manage in Routines."
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

    private val _savingSchedule = kotlinx.coroutines.flow.MutableStateFlow(false)
    val savingSchedule: StateFlow<Boolean> = _savingSchedule
    data class ScheduleRecord(val loaded: Boolean = false, val schedule: FocusSchedule? = null)
    fun observeSchedule(id: Int) = repository.observeSchedule(id).map { ScheduleRecord(true, it) }

    fun saveSchedule(schedule: FocusSchedule, expected: FocusSchedule? = null, onStored: () -> Unit = {}) {
        if (!_savingSchedule.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
                try {
                    if (expected == null) {
                        require(schedule.id == 0) { "Missing original routine; your draft is kept." }
                        repository.insertSchedule(schedule)
                    } else repository.updateScheduleChecked(schedule, expected)
                } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (error: Exception) {
                    android.widget.Toast.makeText(getApplication(), error.message ?: "Couldn't save routine. Your draft is kept.", android.widget.Toast.LENGTH_LONG).show()
                    return@launch
                }
                onStored()
                if (expected == null) try {
                    com.focusbyrj.app.util.FocusStatsManager.addRoutineActivity(getApplication(), 15L)
                } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (_: Exception) {
                    android.widget.Toast.makeText(getApplication(), "Routine saved. Activity refresh failed.", android.widget.Toast.LENGTH_LONG).show()
                }
            } finally { _savingSchedule.value = false }
        }
    }

    fun toggleSchedule(schedule: FocusSchedule) {
        saveSchedule(schedule.copy(isEnabled = !schedule.isEnabled), schedule)
    }

    fun updateSchedule(schedule: FocusSchedule, expected: FocusSchedule = schedule) {
        saveSchedule(schedule, expected)
    }

    fun deleteSchedule(schedule: FocusSchedule) {
        viewModelScope.launch {
            try { repository.deleteScheduleChecked(schedule) }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (error: Exception) {
                android.widget.Toast.makeText(getApplication(), error.message ?: "Couldn't delete routine. Try again.", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    val isSessionActive = com.focusbyrj.app.util.FocusSessionManager.active
    val timeRemaining = com.focusbyrj.app.util.FocusSessionManager.remainingSeconds
    val initialTime = com.focusbyrj.app.util.FocusSessionManager.initialSeconds

    fun setTimeRemaining(minutes: Int) = com.focusbyrj.app.util.FocusSessionManager.selectDuration(minutes)

    fun toggleFocusSession() {
        val manager = com.focusbyrj.app.util.FocusSessionManager
        manager.refresh(getApplication())
        if (manager.active.value) manager.stop(getApplication())
        else manager.start(getApplication(), (manager.initialSeconds.value / 60).toInt())
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

    fun deleteBoundaries(apps: List<AppRestriction>) {
        viewModelScope.launch { repository.removeBoundaryPackages(apps.map { it.packageName }.toSet()) }
    }

    fun deleteRestriction(app: AppRestriction) {
        viewModelScope.launch {
            repository.removeBoundaryPackages(setOf(app.packageName))
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
