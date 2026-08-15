package com.voxa.app.ui.viewmodel

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.viewModelScope
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig
import com.voxa.app.BuildConfig
import com.voxa.app.data.local.VoxaDatabase
import com.voxa.app.data.local.entity.ItineraryEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
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
    val leadTimeMins: Int = 0,
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
    val timerIntervalMins: Int = 0, // Global Default set to On Time
    val isNotificationsEnabled: Boolean = true,
    val isHapticFeedbackEnabled: Boolean = true,
    val isAlarmVibrationEnabled: Boolean = true,
    val snoozeLengthMins: Int = 10,
    val pendingActionTitle: String = "",
    val pendingActionDate: String = "",
    val pendingActionTime: String = "",
    val pendingLeadTime: Int = 0,
    val assistantSuggestion: String = "Traffic is heavy on I-95. Consider leaving 15 minutes earlier.",
    val activeAlertItem: ItineraryItem? = null,
    val isTriggeredOnLockScreen: Boolean = false
)

class VoxaViewModel(application: android.app.Application) : androidx.lifecycle.AndroidViewModel(application) {
    private val dataStore = application.dataStore
    private val database = VoxaDatabase.getDatabase(application)
    private val itineraryDao = database.itineraryDao()

    private var speechRecognizer: SpeechRecognizer? = null
    private val generativeModel = GenerativeModel(
        modelName = "gemini-3-flash-preview",
        apiKey = BuildConfig.GEMINI_API_KEY,
        generationConfig = generationConfig {
            temperature = 0.1f
            topK = 1
            topP = 1f
        }
    )

    private val systemPrompt = """
        You are a highly capable scheduling assistant. The user will speak in English, Urdu, or Roman Urdu.
        Your tasks:
        1. Translate and Refine: Convert the user's input into a clean, professional English sentence (e.g., "I want to meet Sarah tomorrow at 2" -> "Schedule a meeting with Sarah tomorrow at 2:00 PM").
        2. Extract Data: Provide a JSON object with:
           - "refinedText": The clean English version.
           - "title": A concise title for the event (e.g., "Meeting with Sarah").
           - "date": Date in "d MMM, yyyy" format.
           - "time": Time in "hh:mm AM/PM" format.
           - "leadTime": Alert lead time in minutes (default 0).

        If the user doesn't specify a time or date, use:
        - Date: "${Calendar.getInstance().get(Calendar.DAY_OF_MONTH)} ${Calendar.getInstance().getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.getDefault())}, ${Calendar.getInstance().get(Calendar.YEAR)}"
        - Time: "${String.format(Locale.getDefault(), "%02d:%02d %s", if (Calendar.getInstance().get(Calendar.HOUR_OF_DAY) % 12 == 0) 12 else Calendar.getInstance().get(Calendar.HOUR_OF_DAY) % 12, Calendar.getInstance().get(Calendar.MINUTE), if (Calendar.getInstance().get(Calendar.HOUR_OF_DAY) < 12) "AM" else "PM")}"

        Return ONLY the JSON object.
    """.trimIndent()

    private val notificationsEnabledKey = androidx.datastore.preferences.core.booleanPreferencesKey("notifications_enabled")
    private val hapticEnabledKey = androidx.datastore.preferences.core.booleanPreferencesKey("haptic_enabled")
    private val vibrationEnabledKey = androidx.datastore.preferences.core.booleanPreferencesKey("vibration_enabled")
    private val snoozeLengthKey = androidx.datastore.preferences.core.intPreferencesKey("snooze_length")
    private val onboardingCompletedKey = androidx.datastore.preferences.core.booleanPreferencesKey("onboarding_completed")

    private val _uiState = MutableStateFlow(VoxaUiState())
    val uiState: StateFlow<VoxaUiState> = _uiState.asStateFlow()
    
    private var countdownJob: kotlinx.coroutines.Job? = null
    private var pendingAlarmId: Int? = null
    private val scheduledAlarmIds = mutableSetOf<Int>()

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
                itineraryDao.getAllItems()
                    .distinctUntilChanged() // Prevents processing if data hasn't changed
                    .collectLatest { entities ->
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
                            items.filter { !it.isCompleted && !scheduledAlarmIds.contains(it.id) }
                                 .forEach { scheduleAlarm(it) }
                        }
                    }
                }
            }
        }
    }

    private fun scheduleAlarm(item: ItineraryItem) {
        // Prevent redundant scheduling for the same item in the same session
        // unless it's a new or modified item.
        val entity = ItineraryEntity(
            id = item.id,
            time = item.time,
            title = item.title,
            subtitle = item.subtitle,
            isCompleted = item.isCompleted,
            leadTimeMins = item.leadTimeMins
        )
        com.voxa.app.AlarmUtils.scheduleAlarm(getApplication(), entity)
        scheduledAlarmIds.add(item.id)
    }

    private fun cancelAlarm(itemId: Int) {
        val alarmManager = getApplication<android.app.Application>().getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(getApplication(), com.voxa.app.VoxaAlarmService::class.java).apply {
            action = "ACTION_TRIGGER_ALARM"
        }
        
        val pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(
                getApplication(),
                itemId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            PendingIntent.getService(
                getApplication(),
                itemId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
        
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.d("VoxaAlarm", "Alarm cancelled for ID: $itemId")
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
        } catch (_: Exception) {}
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
        val context = getApplication<android.app.Application>()
        
        // Ensure we have RECORD_AUDIO permission
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            _uiState.value = _uiState.value.copy(assistantState = AssistantState.ERROR, transcription = "Mic permission not granted.")
            return
        }

        // Integrated approach
        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                _uiState.value = _uiState.value.copy(assistantState = AssistantState.LISTENING, transcription = "Listening...")
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {
                val vol = (rmsdB + 2f) / 15f
                _uiState.value = _uiState.value.copy(volume = vol.coerceIn(0.1f, 1.0f))
            }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                _uiState.value = _uiState.value.copy(assistantState = AssistantState.THINKING)
            }
            override fun onError(error: Int) {
                val errorMessage = when(error) {
                    SpeechRecognizer.ERROR_NETWORK -> "Network issue."
                    SpeechRecognizer.ERROR_NO_MATCH -> "Didn't hear anything."
                    else -> "Speech Error: $error"
                }
                Log.e("VoxaAI", errorMessage)
                _uiState.value = _uiState.value.copy(assistantState = AssistantState.IDLE)
            }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val text = matches[0]
                    _uiState.value = _uiState.value.copy(transcription = text)
                    processWithAI(text)
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    _uiState.value = _uiState.value.copy(transcription = matches[0])
                }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        speechRecognizer?.startListening(intent)
    }

    private fun processWithAI(userInput: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(assistantState = AssistantState.THINKING)
            try {
                val response = generativeModel.generateContent("$systemPrompt\n\nUser Input: \"$userInput\"")
                val jsonString = response.text ?: "{}"
                
                val refinedText = extractJsonValue(jsonString, "refinedText")
                val title = extractJsonValue(jsonString, "title")
                val date = extractJsonValue(jsonString, "date")
                val time = extractJsonValue(jsonString, "time")
                val leadTime = extractJsonValue(jsonString, "leadTime").replace(Regex("[^0-9]"), "").toIntOrNull() ?: 0

                _uiState.value = _uiState.value.copy(
                    assistantState = AssistantState.PROCESSING,
                    transcription = refinedText.ifEmpty { userInput }, // Proper English translation
                    pendingActionTitle = title.ifEmpty { "New Meeting" },
                    pendingActionDate = date.ifEmpty { getCurrentDate() },
                    pendingActionTime = time.ifEmpty { getCurrentTime() },
                    pendingLeadTime = leadTime
                )
                
                delay(800.milliseconds)
                _uiState.value = _uiState.value.copy(assistantState = AssistantState.IDLE)
            } catch (e: Exception) {
                Log.e("VoxaAI", "AI Error", e)
                _uiState.value = _uiState.value.copy(assistantState = AssistantState.ERROR, transcription = "AI Processing failed.")
            }
        }
    }

    private fun getCurrentDate(): String {
        val cal = Calendar.getInstance()
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val month = cal.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.getDefault())
        val year = cal.get(Calendar.YEAR)
        return "$day $month, $year"
    }

    private fun getCurrentTime(): String {
        val cal = Calendar.getInstance()
        val hour = if (cal.get(Calendar.HOUR_OF_DAY) % 12 == 0) 12 else cal.get(Calendar.HOUR_OF_DAY) % 12
        val min = String.format(Locale.getDefault(), "%02d", cal.get(Calendar.MINUTE))
        val amPm = if (cal.get(Calendar.HOUR_OF_DAY) < 12) "AM" else "PM"
        return "$hour:$min $amPm"
    }

    private fun extractJsonValue(json: String, key: String): String {
        val pattern = "\"$key\"\\s*:\\s*\"?([^\",}]+)\"?".toRegex()
        return pattern.find(json)?.groupValues?.get(1)?.trim() ?: ""
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
        _uiState.value = _uiState.value.copy(assistantState = AssistantState.IDLE)
    }

    fun processWithAIExternally(text: String) {
        _uiState.value = _uiState.value.copy(transcription = text)
        processWithAI(text)
    }

    fun resetToError(message: String) {
        _uiState.value = _uiState.value.copy(
            assistantState = AssistantState.ERROR,
            transcription = message
        )
    }

    override fun onCleared() {
        super.onCleared()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    fun deleteItineraryItem(id: Int) {
        viewModelScope.launch {
            itineraryDao.deleteItemById(id)
            scheduledAlarmIds.remove(id)
            cancelAlarm(id)
        }
    }

    fun dismissNotification(id: Int) {
        val updatedList = _uiState.value.notifications.filter { it.id != id }
        _uiState.value = _uiState.value.copy(notifications = updatedList)
    }

    fun triggerAlarmFromIntent(itemId: Int, isLocked: Boolean = false) {
        Log.d("VoxaAlarm", "Trigger request received for ID: $itemId, Locked: $isLocked")
        val item = uiState.value.itinerary.find { it.id == itemId }
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
