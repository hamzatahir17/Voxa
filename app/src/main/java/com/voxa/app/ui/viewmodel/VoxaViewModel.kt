package com.voxa.app.ui.viewmodel

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.viewModelScope
import com.voxa.app.VoxaAlarmReceiver
import com.voxa.app.data.local.VoxaDatabase
import com.voxa.app.data.local.entity.ItineraryEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private val Context.dataStore by preferencesDataStore(name = "voxa_prefs")

enum class AssistantState {
    IDLE, LISTENING, PROCESSING, THINKING, ERROR
}

data class ItineraryItem(
    val id: Int,
    val time: String,
    val title: String,
    val subtitle: String,
    val isCompleted: Boolean = false,
    val isActive: Boolean = false,
    val leadTimeMins: Int = 15,
)

data class NotificationItem(
    val id: Int,
    val title: String,
    val body: String,
    val time: String,
    val isActive: Boolean
)

data class VoxaUiState(
    val assistantState: AssistantState = AssistantState.IDLE,
    val transcription: String = "",
    val volume: Float = 0f,
    val userName: String = "Alex",
    val countdown: String = "14:14",
    val isMicPermissionGranted: Boolean = false,
    val isOnboardingCompleted: Boolean = false,
    val isDataLoaded: Boolean = false,
    val itinerary: List<ItineraryItem> = emptyList(),
    val notifications: List<NotificationItem> = emptyList(),
    val nextMeetingTitle: String? = null,
    val nextMeetingTime: String? = null,
    val timerIntervalMins: Int = 15, // Global Default
    val isNotificationsEnabled: Boolean = true,
    val isHapticFeedbackEnabled: Boolean = true,
    val isAlarmVibrationEnabled: Boolean = true,
    val snoozeLengthMins: Int = 10,
    val pendingActionTitle: String = "",
    val pendingActionDate: String = "",
    val pendingActionTime: String = "",
    val pendingLeadTime: Int = 15,
    val assistantSuggestion: String = "Traffic is heavy on I-95. Consider leaving 15 minutes earlier.",
    val activeAlertItem: ItineraryItem? = null,
    val isTriggeredOnLockScreen: Boolean = false
)

class VoxaViewModel(application: android.app.Application) : androidx.lifecycle.AndroidViewModel(application) {
    private val dataStore = application.dataStore
    private val database = VoxaDatabase.getDatabase(application)
    private val itineraryDao = database.itineraryDao()

    private val notificationsEnabledKey = androidx.datastore.preferences.core.booleanPreferencesKey("notifications_enabled")
    private val hapticEnabledKey = androidx.datastore.preferences.core.booleanPreferencesKey("haptic_enabled")
    private val vibrationEnabledKey = androidx.datastore.preferences.core.booleanPreferencesKey("vibration_enabled")
    private val snoozeLengthKey = androidx.datastore.preferences.core.intPreferencesKey("snooze_length")
    private val onboardingCompletedKey = androidx.datastore.preferences.core.booleanPreferencesKey("onboarding_completed")

    private val _uiState = MutableStateFlow(VoxaUiState())
    val uiState: StateFlow<VoxaUiState> = _uiState.asStateFlow()
    
    private var countdownJob: kotlinx.coroutines.Job? = null
    private var pendingAlarmId: Int? = null

    private val sampleCommands = listOf(
        "Schedule a meeting with Sarah for tomorrow at 3 PM",
        "Set a reminder to buy groceries at 6 PM",
        "Call the Design Team at 11:30 AM",
        "Book a flight to New York for Friday morning",
        "Check my emails for urgent notifications"
    )

    private val suggestions = listOf(
        "Traffic is heavy on I-95. Consider leaving 15 minutes earlier.",
        "You have a free slot at 4 PM. Want to schedule a workout?",
        "Don't forget your umbrella, it's expected to rain at 5 PM.",
        "Your meeting with Asif is confirmed for tomorrow.",
        "Battery is low on your smart watch. Charge it before the Sync."
    )

    init {
        loadData()
        startSystemTick()
        startDynamicSuggestions()
    }

    private fun loadData() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            // Load Settings
            launch {
                dataStore.data.collect { prefs ->
                    _uiState.value = _uiState.value.copy(
                        isNotificationsEnabled = prefs[notificationsEnabledKey] ?: true,
                        isHapticFeedbackEnabled = prefs[hapticEnabledKey] ?: true,
                        isAlarmVibrationEnabled = prefs[vibrationEnabledKey] ?: true,
                        snoozeLengthMins = prefs[snoozeLengthKey] ?: 10,
                        isOnboardingCompleted = prefs[onboardingCompletedKey] ?: false,
                        isMicPermissionGranted = prefs[onboardingCompletedKey] ?: false,
                        isDataLoaded = true
                    )
                }
            }

            // Load Itinerary from Room
            launch {
                itineraryDao.getAllItems().collectLatest { entities ->
                    val items = entities.map { entity ->
                        ItineraryItem(
                            id = entity.id,
                            time = entity.time,
                            title = entity.title,
                            subtitle = entity.subtitle,
                            isCompleted = entity.isCompleted,
                            isActive = false,
                            leadTimeMins = entity.leadTimeMins
                        )
                    }
                    
                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                        updateStateWithItinerary(items)

                        // Robust check for pending alarms
                        val currentPendingId = pendingAlarmId
                        if (currentPendingId != null) {
                            val item = items.find { it.id == currentPendingId }
                            if (item != null && !item.isCompleted) {
                                Log.d("VoxaAlarm", "Pending alarm resolved for ID: $currentPendingId")
                                _uiState.value = _uiState.value.copy(activeAlertItem = item)
                                pendingAlarmId = null
                            }
                        }

                        // Re-schedule future alarms only if app is NOT in alert mode
                        if (_uiState.value.activeAlertItem == null) {
                            items.filter { !it.isCompleted }.forEach { scheduleAlarm(it) }
                        }
                    }
                }
            }
        }
    }

    private fun scheduleAlarm(item: ItineraryItem) {
        val entity = ItineraryEntity(
            id = item.id,
            time = item.time,
            title = item.title,
            subtitle = item.subtitle,
            isCompleted = item.isCompleted,
            leadTimeMins = item.leadTimeMins
        )
        com.voxa.app.AlarmUtils.scheduleAlarm(getApplication(), entity)
    }

    private fun cancelAlarm(itemId: Int) {
        val alarmManager = getApplication<android.app.Application>().getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(getApplication(), VoxaAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            getApplication(),
            itemId,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
        }
    }

    private fun saveSettings() {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[notificationsEnabledKey] = _uiState.value.isNotificationsEnabled
                prefs[hapticEnabledKey] = _uiState.value.isHapticFeedbackEnabled
                prefs[vibrationEnabledKey] = _uiState.value.isAlarmVibrationEnabled
                prefs[snoozeLengthKey] = _uiState.value.snoozeLengthMins
                prefs[onboardingCompletedKey] = _uiState.value.isOnboardingCompleted
            }
        }
    }

    private fun startSystemTick() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            while (true) {
                updateNextMeetingCountdown()
                delay(1.seconds)
            }
        }
    }

    private fun updateNextMeetingCountdown() {
        val nextItem = _uiState.value.itinerary.firstOrNull { !it.isCompleted }
        if (nextItem == null) {
            _uiState.value = _uiState.value.copy(countdown = "00:00", nextMeetingTitle = "No Events Left")
            return
        }

        val now = Calendar.getInstance()
        // We use a separate parser for display that DOESN'T auto-advance to tomorrow
        // unless we want it to for UI purposes
        val eventTime = parseTimeForCountdown(nextItem.time)

        val diffMillis = eventTime.timeInMillis - now.timeInMillis
        val leadTimeMillis = nextItem.leadTimeMins * 60 * 1000L

        if (diffMillis > 0) {
            if (diffMillis <= leadTimeMillis && _uiState.value.activeAlertItem == null && !nextItem.isCompleted) {
                _uiState.value = _uiState.value.copy(activeAlertItem = nextItem)
            }

            val totalSecs = diffMillis / 1000
            val mins = totalSecs / 60
            val secs = totalSecs % 60
            _uiState.value = _uiState.value.copy(
                countdown = String.format(Locale.getDefault(), "%02d:%02d", mins, secs),
                nextMeetingTitle = nextItem.title,
                nextMeetingTime = nextItem.time
            )
        } else {
            _uiState.value = _uiState.value.copy(
                countdown = "Live",
                nextMeetingTitle = nextItem.title,
                nextMeetingTime = nextItem.time
            )
        }
    }

    private fun parseTimeForCountdown(timeStr: String): Calendar {
        val cal = Calendar.getInstance()
        try {
            val cleanTime = timeStr.uppercase().trim()
            val amPm = if (cleanTime.contains("PM")) "PM" else "AM"
            val timeDigits = cleanTime.replace("AM", "").replace("PM", "").trim()
            val timeParts = timeDigits.split(":")
            var hour = timeParts[0].toInt()
            val min = if (timeParts.size > 1) timeParts[1].toInt() else 0
            if (amPm == "PM" && hour < 12) hour += 12
            if (amPm == "AM" && hour == 12) hour = 0
            cal.set(Calendar.HOUR_OF_DAY, hour)
            cal.set(Calendar.MINUTE, min)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
        } catch (e: Exception) {}
        return cal
    }

    private fun startDynamicSuggestions() {
        viewModelScope.launch {
            while (true) {
                delay(30.seconds)
                _uiState.value = _uiState.value.copy(
                    assistantSuggestion = suggestions.random()
                )
            }
        }
    }

    fun grantMicPermission() {
        _uiState.value = _uiState.value.copy(
            isMicPermissionGranted = true,
            isOnboardingCompleted = true
        )
        saveSettings()
    }

    fun startListening() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(assistantState = AssistantState.LISTENING, transcription = "", volume = 0.5f)

            val volumeJob = launch {
                var tick = 0f
                var targetVolume = 0.5f
                var currentVolume = 0.5f
                while (true) {
                    delay(32.milliseconds)
                    tick += 0.2f
                    if (Random.nextFloat() > 0.8f) targetVolume = Random.nextFloat() * 0.7f + 0.3f
                    currentVolume += (targetVolume - currentVolume) * 0.15f
                    val microPulse = (kotlin.math.sin(tick * 2f) * 0.05f)
                    _uiState.value = _uiState.value.copy(
                        volume = (currentVolume + microPulse).coerceIn(0.1f, 1.0f)
                    )
                }
            }

            val command = sampleCommands.random()
            val words = command.split(" ")
            var currentText = ""
            for (word in words) {
                delay(Random.nextLong(150, 450).milliseconds)
                currentText += "$word "
                _uiState.value = _uiState.value.copy(transcription = currentText.trim())
            }

            volumeJob.cancel()
            _uiState.value = _uiState.value.copy(assistantState = AssistantState.THINKING, volume = 0f)
            delay(1500.milliseconds)
            
            // Smart testing: Always set a time 2-5 minutes in the future
            val testCal = Calendar.getInstance()
            testCal.add(Calendar.MINUTE, 2)
            val hour = if (testCal.get(Calendar.HOUR_OF_DAY) % 12 == 0) 12 else testCal.get(Calendar.HOUR_OF_DAY) % 12
            val min = String.format(Locale.getDefault(), "%02d", testCal.get(Calendar.MINUTE))
            val amPm = if (testCal.get(Calendar.HOUR_OF_DAY) < 12) "AM" else "PM"
            val futureTime = "$hour:$min $amPm"

            val hasSarah = command.contains("Sarah", ignoreCase = true)

            _uiState.value = _uiState.value.copy(
                assistantState = AssistantState.PROCESSING,
                pendingActionTitle = if (hasSarah) "Meeting with Sarah" else "New Event: " + command.take(15) + "...",
                pendingActionDate = "Today",
                pendingActionTime = futureTime
            )

            delay(1000.milliseconds)
            _uiState.value = _uiState.value.copy(assistantState = AssistantState.IDLE)
        }
    }

    fun deleteItineraryItem(id: Int) {
        viewModelScope.launch {
            itineraryDao.deleteItemById(id)
            cancelAlarm(id)
        }
    }

    fun dismissNotification(id: Int) {
        val updatedList = _uiState.value.notifications.filter { it.id != id }
        _uiState.value = _uiState.value.copy(notifications = updatedList)
    }

    fun triggerAlarmFromIntent(itemId: Int, isLocked: Boolean = false) {
        Log.d("VoxaAlarm", "Trigger request received for ID: $itemId, Locked: $isLocked")
        val item = _uiState.value.itinerary.find { it.id == itemId }
        if (item != null && !item.isCompleted) {
            _uiState.value = _uiState.value.copy(
                activeAlertItem = item,
                isTriggeredOnLockScreen = isLocked
            )
        } else {
            pendingAlarmId = itemId
            _uiState.value = _uiState.value.copy(isTriggeredOnLockScreen = isLocked)
            Log.d("VoxaAlarm", "Item not found in current state, set as pending ID")
        }
    }

    fun dismissAlert() {
        val currentAlert = _uiState.value.activeAlertItem
        if (currentAlert != null) {
            viewModelScope.launch {
                val updated = ItineraryEntity(
                    id = currentAlert.id,
                    time = currentAlert.time,
                    title = currentAlert.title,
                    subtitle = currentAlert.subtitle,
                    isCompleted = true,
                    leadTimeMins = currentAlert.leadTimeMins
                )
                itineraryDao.updateItem(updated)
                _uiState.value = _uiState.value.copy(
                    activeAlertItem = null,
                    isTriggeredOnLockScreen = false
                )
            }
        }
    }

    fun snoozeAlert() {
        val currentAlert = _uiState.value.activeAlertItem
        val snoozeMins = _uiState.value.snoozeLengthMins
        if (currentAlert != null) {
            viewModelScope.launch {
                val calendar = com.voxa.app.AlarmUtils.parseTime(currentAlert.time)
                calendar.add(Calendar.MINUTE, snoozeMins)

                val hour = if (calendar.get(Calendar.HOUR_OF_DAY) % 12 == 0) 12 else calendar.get(Calendar.HOUR_OF_DAY) % 12
                val min = calendar.get(Calendar.MINUTE)
                val amPm = if (calendar.get(Calendar.HOUR_OF_DAY) < 12) "AM" else "PM"

                val newTime = String.format(Locale.getDefault(), "%02d:%02d %s", hour, min, amPm)

                val snoozedEntity = ItineraryEntity(
                    id = currentAlert.id,
                    time = newTime,
                    title = currentAlert.title,
                    subtitle = currentAlert.subtitle,
                    isCompleted = false,
                    leadTimeMins = 0
                )
                itineraryDao.updateItem(snoozedEntity)

                _uiState.value = _uiState.value.copy(
                    activeAlertItem = null,
                    isTriggeredOnLockScreen = false
                )
            }
        }
    }

    fun toggleNotifications(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(isNotificationsEnabled = enabled)
        saveSettings()
    }

    fun toggleHapticFeedback(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(isHapticFeedbackEnabled = enabled)
        saveSettings()
    }

    fun toggleAlarmVibration(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(isAlarmVibrationEnabled = enabled)
        saveSettings()
    }

    fun updateSnoozeLength(mins: Int) {
        _uiState.value = _uiState.value.copy(snoozeLengthMins = mins)
        saveSettings()
    }

    fun updatePendingAction(
        title: String? = null,
        date: String? = null,
        time: String? = null,
        leadTime: Int? = null
    ) {
        _uiState.value = _uiState.value.copy(
            pendingActionTitle = title ?: _uiState.value.pendingActionTitle,
            pendingActionDate = date ?: _uiState.value.pendingActionDate,
            pendingActionTime = time ?: _uiState.value.pendingActionTime,
            pendingLeadTime = leadTime ?: _uiState.value.pendingLeadTime
        )
    }

    fun confirmAction() {
        viewModelScope.launch {
            val newItem = ItineraryEntity(
                time = _uiState.value.pendingActionTime,
                title = _uiState.value.pendingActionTitle,
                subtitle = "Scheduled via Voxa",
                isCompleted = false,
                leadTimeMins = _uiState.value.pendingLeadTime
            )

            val newId = itineraryDao.insertItem(newItem).toInt()
            
            val scheduledItem = ItineraryItem(
                id = newId,
                time = newItem.time,
                title = newItem.title,
                subtitle = newItem.subtitle,
                isCompleted = false,
                leadTimeMins = newItem.leadTimeMins
            )
            scheduleAlarm(scheduledItem)

            val newNotification = NotificationItem(
                id = Random.nextInt(100, 1000),
                title = "Event Added",
                body = _uiState.value.pendingActionTitle,
                time = "Just now",
                isActive = true
            )

            _uiState.value = _uiState.value.copy(
                notifications = listOf(newNotification) + _uiState.value.notifications,
                assistantSuggestion = "New event added to your calendar."
            )

            resetState()
        }
    }

    private fun updateStateWithItinerary(list: List<ItineraryItem>) {
        val nextItem = list.firstOrNull { !it.isCompleted }
        _uiState.value = _uiState.value.copy(
            itinerary = list,
            nextMeetingTitle = nextItem?.title,
            nextMeetingTime = nextItem?.time
        )
    }

    fun resetState() {
        _uiState.value = _uiState.value.copy(
            assistantState = AssistantState.IDLE,
            transcription = ""
        )
    }
}
