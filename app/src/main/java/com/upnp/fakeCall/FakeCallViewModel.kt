package com.upnp.fakeCall

import android.app.Application
import android.content.ContentUris
import android.os.Build
import com.upnp.fakeCall.R
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.ContactsContract
import android.provider.DocumentsContract
import android.provider.Settings
import android.telecom.TelecomManager
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.upnp.fakeCall.ivr.IvrConfig
import com.upnp.fakeCall.ivr.IvrConfigStore
import com.upnp.fakeCall.ivr.IvrNode
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

enum class ScheduleKind {
    PRESET,
    CUSTOM_COUNTDOWN,
    CUSTOM_EXACT
}

enum class CallerInputMode {
    MANUAL,
    CONTACT
}

data class CallContact(
    val id: Long,
    val displayName: String,
    val phoneNumber: String,
    val photoUri: String = "",
    val avatarBase64: String = ""
)

data class CustomPreset(
    val kind: ScheduleKind,
    val minutes: Int = 0,
    val seconds: Int = 0,
    val hour: Int = 0,
    val minute: Int = 0
)

data class FakeCallUiState(
    val isOnboardingComplete: Boolean = false,
    val providerName: String = "",
    val callerName: String = "",
    val callerNumber: String = "",
    val callerInputMode: CallerInputMode = CallerInputMode.MANUAL,
    val selectedContact: CallContact? = null,
    val pinnedContacts: List<CallContact> = emptyList(),
    val recentContacts: List<CallContact> = emptyList(),
    val selectedDelaySeconds: Int = 10,
    val scheduleKind: ScheduleKind = ScheduleKind.PRESET,
    val customCountdownMinutes: Int = 2,
    val customCountdownSeconds: Int = 30,
    val customExactHour: Int = 14,
    val customExactMinute: Int = 45,
    val customPresets: List<CustomPreset> = emptyList(),
    val ivrConfig: IvrConfig? = null,
    val selectedAudioUri: String = "",
    val selectedAudioName: String = "",
    val hasRequiredPermissions: Boolean = false,
    val isProviderEnabled: Boolean = false,
    val isTimerRunning: Boolean = false,
    val timerEndsAtMillis: Long = 0L,
    val statusMessage: String = "",
    val isRecordingEnabled: Boolean = true,
    val recordingsFolderName: String = "",
    val quickTriggerCallerName: String = "",
    val quickTriggerCallerNumber: String = "",
    val quickTriggerDelaySeconds: Int = QuickTriggerManager.DEFAULT_DELAY_SECONDS,
    val quickTriggerPresetName: String = "",
    val quickTriggerPresets: List<QuickTriggerPreset> = emptyList(),
    val quickTriggerDefaultPresetSlot: Int? = null,
    val callRingTimeoutSeconds: Int = 45,
    val alarmRingTimeoutSeconds: Int = 60,
    val alarmModeItems: List<AlarmModeItem> = emptyList(),
    val isMp3IvrModeEnabled: Boolean = false,
    val mp3IvrFolderUri: String = "",
    val mp3IvrFolderName: String = "",
    val startupUpdate: ReleaseInfo? = null
)

class FakeCallViewModel(application: Application) : AndroidViewModel(application) {

    private fun str(id: Int, vararg args: Any): String =
        getApplication<Application>().getString(id, *args)

    private val telecomHelper = TelecomHelper(application)
    private val prefs = application.getSharedPreferences(PREFS_NAME, 0)
    private val ivrStore = IvrConfigStore()
    private val updateChecker = UpdateChecker()
    private val quickTriggerDefaults = QuickTriggerManager.loadDefaults(application)
    private val quickTriggerPresets = QuickTriggerManager.loadPresets(application)
    private val initialAlarmModeItems = AlarmModeRepository.load(application)
    private val initialPinnedContacts = parseContactList(prefs.getString(KEY_PINNED_CONTACTS, "").orEmpty())
    private val initialRecentContacts = pruneRecentContacts(
        recentContacts = parseContactList(prefs.getString(KEY_RECENT_CONTACTS, "").orEmpty()),
        pinnedContacts = initialPinnedContacts
    )

    private val _uiState = MutableStateFlow(
        FakeCallUiState(
            isOnboardingComplete = prefs.getBoolean(KEY_ONBOARDING_COMPLETE, false),
            providerName = prefs.getString(KEY_PROVIDER_NAME, application.getString(R.string.default_provider_name)).orEmpty(),
            callerName = prefs.getString(KEY_CALLER_NAME, "").orEmpty(),
            callerNumber = prefs.getString(KEY_CALLER_NUMBER, "").orEmpty(),
            callerInputMode = runCatching {
                CallerInputMode.valueOf(
                    prefs.getString(KEY_CALLER_INPUT_MODE, CallerInputMode.MANUAL.name).orEmpty()
                )
            }.getOrDefault(CallerInputMode.MANUAL),
            selectedContact = parseContact(prefs.getString(KEY_SELECTED_CONTACT, "").orEmpty()),
            pinnedContacts = initialPinnedContacts,
            recentContacts = initialRecentContacts,
            selectedDelaySeconds = prefs.getInt(KEY_DELAY_SECONDS, 10),
            scheduleKind = runCatching {
                ScheduleKind.valueOf(
                    prefs.getString(KEY_SCHEDULE_KIND, ScheduleKind.PRESET.name).orEmpty()
                )
            }.getOrDefault(ScheduleKind.PRESET),
            customCountdownMinutes = prefs.getInt(KEY_CUSTOM_COUNTDOWN_MINUTES, 2),
            customCountdownSeconds = prefs.getInt(KEY_CUSTOM_COUNTDOWN_SECONDS, 30),
            customExactHour = prefs.getInt(KEY_CUSTOM_EXACT_HOUR, 14),
            customExactMinute = prefs.getInt(KEY_CUSTOM_EXACT_MINUTE, 45),
            customPresets = parseCustomPresets(prefs.getString(KEY_CUSTOM_PRESETS, "").orEmpty()),
            ivrConfig = ivrStore.load(application),
            selectedAudioUri = prefs.getString(KEY_AUDIO_URI, "").orEmpty(),
            selectedAudioName = prefs.getString(KEY_AUDIO_NAME, application.getString(R.string.default_audio_name)).orEmpty(),
            timerEndsAtMillis = prefs.getLong(KEY_TIMER_ENDS_AT, 0L),
            isRecordingEnabled = prefs.getBoolean(KEY_RECORDING_ENABLED, true),
            recordingsFolderName = prefs.getString(KEY_RECORDINGS_FOLDER_NAME, application.getString(R.string.default_recordings_folder)).orEmpty(),
            quickTriggerCallerName = quickTriggerDefaults.callerName,
            quickTriggerCallerNumber = quickTriggerDefaults.callerNumber,
            quickTriggerDelaySeconds = quickTriggerDefaults.delaySeconds,
            quickTriggerPresetName = prefs.getString(KEY_QUICK_TRIGGER_PRESET_NAME, "").orEmpty(),
            quickTriggerPresets = quickTriggerPresets,
            quickTriggerDefaultPresetSlot = QuickTriggerManager.loadDefaultPresetSlot(application),
            callRingTimeoutSeconds = prefs.getInt(KEY_CALL_RING_TIMEOUT_SECONDS, DEFAULT_CALL_RING_TIMEOUT_SECONDS)
                .coerceAtLeast(0),
            alarmRingTimeoutSeconds = prefs.getInt(KEY_ALARM_RING_TIMEOUT_SECONDS, DEFAULT_ALARM_RING_TIMEOUT_SECONDS)
                .coerceAtLeast(0),
            alarmModeItems = initialAlarmModeItems,
            isMp3IvrModeEnabled = prefs.getBoolean(KEY_MP3_IVR_MODE_ENABLED, false),
            mp3IvrFolderUri = prefs.getString(KEY_MP3_IVR_FOLDER_URI, "").orEmpty(),
            mp3IvrFolderName = prefs.getString(
                KEY_MP3_IVR_FOLDER_NAME,
                application.getString(R.string.settings_mp3_ivr_no_folder_selected)
            ).orEmpty()
        )
    )
    val uiState: StateFlow<FakeCallUiState> = _uiState.asStateFlow()

    val delayOptionsSeconds: List<Int> = listOf(0, 10, 30, 60, 120, 300)
    val ringTimeoutOptionsSeconds: List<Int> = listOf(0, 15, 30, 45, 60, 90, 120, 180)

    init {
        viewModelScope.launch {
            while (isActive) {
                syncRunningTimerState()
                if (uiState.value.hasRequiredPermissions) {
                    refreshProviderStatus()
                }
                syncAlarmModeState()
                delay(1_000L)
            }
        }

        viewModelScope.launch {
            checkForUpdatesOnStartup()
        }

        QuickTriggerManager.updateLauncherShortcuts(application)
    }

    suspend fun checkForUpdatesManual(): UpdateCheckResult {
        return updateChecker.checkForUpdate(BuildConfig.VERSION_NAME)
    }

    fun dismissStartupUpdate() {
        _uiState.update { it.copy(startupUpdate = null) }
    }

    fun onPermissionStateChanged(granted: Boolean) {
        _uiState.update {
            it.copy(
                hasRequiredPermissions = granted,
                isProviderEnabled = if (granted) it.isProviderEnabled else false,
                statusMessage = if (granted) it.statusMessage else str(R.string.status_grant_permissions)
            )
        }

        if (granted) {
            ensurePhoneAccountRegistered()
            refreshProviderStatus()
        }
    }

    fun onProviderNameChange(value: String) {
        _uiState.update { it.copy(providerName = value) }
    }

    fun onQuickTriggerCallerNameChange(value: String) {
        saveQuickTriggerDefaults(uiState.value.copy(quickTriggerCallerName = value))
    }

    fun onQuickTriggerCallerNumberChange(value: String) {
        saveQuickTriggerDefaults(uiState.value.copy(quickTriggerCallerNumber = value))
    }

    fun onQuickTriggerDelayChange(delaySeconds: Int) {
        saveQuickTriggerDefaults(uiState.value.copy(quickTriggerDelaySeconds = delaySeconds))
    }

    fun onCallRingTimeoutChange(timeoutSeconds: Int) {
        val normalized = timeoutSeconds.coerceAtLeast(0)
        prefs.edit().putInt(KEY_CALL_RING_TIMEOUT_SECONDS, normalized).apply()
        _uiState.update { it.copy(callRingTimeoutSeconds = normalized) }
    }

    fun onAlarmRingTimeoutChange(timeoutSeconds: Int) {
        val normalized = timeoutSeconds.coerceAtLeast(0)
        prefs.edit().putInt(KEY_ALARM_RING_TIMEOUT_SECONDS, normalized).apply()
        _uiState.update { it.copy(alarmRingTimeoutSeconds = normalized) }
    }

    fun onQuickTriggerPresetNameChange(value: String) {
        prefs.edit().putString(KEY_QUICK_TRIGGER_PRESET_NAME, value).apply()
        _uiState.update { it.copy(quickTriggerPresetName = value) }
    }

    fun newAlarmModeDraft(): AlarmModeDraft {
        val state = uiState.value
        val now = ZonedDateTime.now().plusMinutes(5)
        return AlarmModeDraft(
            callerName = state.callerName,
            callerNumber = state.callerNumber,
            hour = now.hour,
            minute = now.minute,
            repeatDays = emptySet(),
            messageMode = if (state.selectedAudioUri.isBlank()) {
                AlarmMessageMode.APP_VOICE_TTS
            } else {
                AlarmMessageMode.CUSTOM_AUDIO
            },
            ttsMessage = str(R.string.alarm_tts_default_message),
            customAudioUri = state.selectedAudioUri,
            customAudioName = state.selectedAudioName,
            snoozeEnabled = true,
            snoozeMinutes = 5,
            speakerDefault = AlarmSpeakerDefault.EARPIECE
        )
    }

    fun draftForAlarm(alarmId: Long): AlarmModeDraft? {
        val alarm = AlarmModeRepository.find(getApplication(), alarmId) ?: return null
        return AlarmModeDraft(
            callerName = alarm.callerName,
            callerNumber = alarm.callerNumber,
            hour = alarm.hour,
            minute = alarm.minute,
            repeatDays = alarm.repeatDays,
            messageMode = alarm.messageMode,
            ttsMessage = alarm.ttsMessage.ifBlank { str(R.string.alarm_tts_default_message) },
            customAudioUri = alarm.customAudioUri,
            customAudioName = alarm.customAudioName,
            snoozeEnabled = alarm.snoozeEnabled,
            snoozeMinutes = alarm.snoozeMinutes,
            speakerDefault = alarm.speakerDefault
        )
    }

    fun saveAlarmMode(draft: AlarmModeDraft): Boolean {
        val alarmId = System.currentTimeMillis()
        val item = createAlarmModeItem(alarmId, draft) ?: return false
        return persistAlarmModeItem(item, isUpdate = false)
    }

    fun updateAlarmMode(alarmId: Long, draft: AlarmModeDraft): Boolean {
        val existing = AlarmModeRepository.find(getApplication(), alarmId) ?: return false
        val item = createAlarmModeItem(alarmId, draft, enabled = existing.enabled) ?: return false
        return persistAlarmModeItem(item, isUpdate = true)
    }

    fun deleteAlarmMode(alarmId: Long) {
        AlarmModeScheduler.cancel(getApplication(), alarmId)
        val updated = AlarmModeRepository.delete(getApplication(), alarmId)
        _uiState.update {
            it.copy(
                alarmModeItems = updated,
                statusMessage = str(R.string.status_alarm_deleted)
            )
        }
    }

    private fun createAlarmModeItem(
        alarmId: Long,
        draft: AlarmModeDraft,
        enabled: Boolean = true
    ): AlarmModeItem? {
        if (!uiState.value.hasRequiredPermissions) {
            _uiState.update { it.copy(statusMessage = str(R.string.status_grant_permissions_scheduling)) }
            return null
        }
        if (!uiState.value.isProviderEnabled) {
            _uiState.update { it.copy(statusMessage = str(R.string.status_enable_calling_accounts)) }
            return null
        }
        val number = draft.callerNumber.trim()
        if (number.isBlank()) {
            _uiState.update { it.copy(statusMessage = str(R.string.status_enter_caller_number_scheduling)) }
            return null
        }
        val callerName = draft.callerName.trim()
        val normalizedDraft = draft.copy(
            callerName = callerName,
            callerNumber = number,
            hour = draft.hour.coerceIn(0, 23),
            minute = draft.minute.coerceIn(0, 59),
            snoozeMinutes = draft.snoozeMinutes.coerceIn(1, 30),
            ttsMessage = draft.ttsMessage.trim()
        )
        return AlarmModeItem(
            id = alarmId,
            callerName = normalizedDraft.callerName,
            callerNumber = normalizedDraft.callerNumber,
            hour = normalizedDraft.hour,
            minute = normalizedDraft.minute,
            repeatDays = normalizedDraft.repeatDays.filter { it in 1..7 }.toSet(),
            messageMode = normalizedDraft.messageMode,
            ttsMessage = normalizedDraft.ttsMessage,
            customAudioUri = normalizedDraft.customAudioUri,
            customAudioName = normalizedDraft.customAudioName,
            snoozeEnabled = normalizedDraft.snoozeEnabled,
            snoozeMinutes = normalizedDraft.snoozeMinutes,
            speakerDefault = normalizedDraft.speakerDefault,
            enabled = enabled
        )
    }

    private fun persistAlarmModeItem(item: AlarmModeItem, isUpdate: Boolean): Boolean {
        if (!AlarmModeScheduler.canScheduleExact(getApplication())) {
            _uiState.update { it.copy(statusMessage = str(R.string.status_enable_exact_alarms)) }
            return false
        }

        AlarmModeScheduler.cancel(getApplication(), item.id)
        val triggerAtMillis = AlarmModeScheduler.schedule(getApplication(), item)
        if (triggerAtMillis <= 0L) {
            _uiState.update { it.copy(statusMessage = str(R.string.status_alarm_schedule_failed)) }
            return false
        }
        val persisted = item.copy(nextTriggerAtMillis = triggerAtMillis)
        val updated = AlarmModeRepository.upsert(getApplication(), persisted)
        _uiState.update {
            it.copy(
                alarmModeItems = updated,
                statusMessage = str(
                    if (isUpdate) R.string.status_alarm_updated_for else R.string.status_alarm_saved_for,
                    formatAlarmClockTime(persisted.hour, persisted.minute),
                    formatAlarmRepeatDays(getApplication(), persisted.repeatDays)
                )
            )
        }
        return true
    }

    fun onAlarmModeEnabledChanged(alarmId: Long, enabled: Boolean) {
        val current = AlarmModeRepository.find(getApplication(), alarmId) ?: return
        if (!enabled) {
            AlarmModeScheduler.cancel(getApplication(), alarmId)
            val updated = AlarmModeRepository.upsert(
                getApplication(),
                current.copy(enabled = false, nextTriggerAtMillis = 0L)
            )
            _uiState.update { it.copy(alarmModeItems = updated) }
            return
        }
        if (!uiState.value.hasRequiredPermissions) {
            _uiState.update { it.copy(statusMessage = str(R.string.status_grant_permissions_scheduling)) }
            return
        }
        if (!uiState.value.isProviderEnabled) {
            _uiState.update { it.copy(statusMessage = str(R.string.status_enable_calling_accounts)) }
            return
        }
        if (!AlarmModeScheduler.canScheduleExact(getApplication())) {
            _uiState.update { it.copy(statusMessage = str(R.string.status_enable_exact_alarms)) }
            return
        }
        val enabledItem = current.copy(enabled = true)
        val triggerAtMillis = AlarmModeScheduler.schedule(getApplication(), enabledItem)
        if (triggerAtMillis <= 0L) {
            _uiState.update { it.copy(statusMessage = str(R.string.status_alarm_schedule_failed)) }
            return
        }
        val updated = AlarmModeRepository.upsert(
            getApplication(),
            enabledItem.copy(nextTriggerAtMillis = triggerAtMillis)
        )
        _uiState.update { it.copy(alarmModeItems = updated) }
    }

    fun saveQuickTriggerPreset() {
        val customName = uiState.value.quickTriggerPresetName.trim()
        val result = QuickTriggerManager.saveCurrentDefaultsAsPreset(
            context = getApplication(),
            customTitle = customName
        )
        val status = when (result) {
            QuickTriggerPresetSaveResult.SAVED -> str(R.string.status_quick_trigger_preset_saved)
            QuickTriggerPresetSaveResult.LIMIT_REACHED -> str(R.string.status_quick_trigger_preset_limit)
            QuickTriggerPresetSaveResult.INVALID_DATA -> str(R.string.status_enter_caller_number_preset)
        }
        refreshQuickTriggerPresets(status)
        if (result == QuickTriggerPresetSaveResult.SAVED) {
            prefs.edit().putString(KEY_QUICK_TRIGGER_PRESET_NAME, "").apply()
            _uiState.update { it.copy(quickTriggerPresetName = "") }
        }
    }

    fun applyQuickTriggerPreset(slot: Int) {
        val applied = QuickTriggerManager.applyPresetToDefaults(getApplication(), slot)
        if (!applied) {
            _uiState.update { it.copy(statusMessage = str(R.string.status_preset_not_found)) }
            return
        }
        val defaults = QuickTriggerManager.loadDefaults(getApplication())
        _uiState.update {
            it.copy(
                quickTriggerCallerName = defaults.callerName,
                quickTriggerCallerNumber = defaults.callerNumber,
                quickTriggerDelaySeconds = defaults.delaySeconds,
                statusMessage = str(R.string.status_preset_applied)
            )
        }
    }

    fun removeQuickTriggerPreset(slot: Int) {
        val removed = QuickTriggerManager.removePreset(getApplication(), slot)
        val status = if (removed) {
            str(R.string.status_quick_trigger_preset_removed)
        } else {
            str(R.string.status_preset_not_found)
        }
        refreshQuickTriggerPresets(status)
    }

    fun setQuickTriggerDefaultPreset(slot: Int) {
        val updated = QuickTriggerManager.setDefaultPresetSlot(getApplication(), slot)
        val status = if (updated) {
            str(R.string.status_quick_trigger_default_preset_set, slot)
        } else {
            str(R.string.status_preset_not_found)
        }
        refreshQuickTriggerPresets(status)
    }

    fun onQuickTriggerPresetUseCustomAudioChange(slot: Int, enabled: Boolean) {
        val updated = QuickTriggerManager.updatePresetAudioMode(getApplication(), slot, enabled)
        val status = if (updated) {
            if (enabled) {
                str(R.string.status_quick_trigger_preset_audio_enabled)
            } else {
                str(R.string.status_quick_trigger_preset_audio_disabled)
            }
        } else {
            str(R.string.status_preset_not_found)
        }
        refreshQuickTriggerPresets(status)
    }

    fun onQuickTriggerPresetAudioSelected(slot: Int, uri: Uri?) {
        if (uri == null) return
        val app = getApplication<Application>()
        val resolver = app.contentResolver
        runCatching {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val label = resolveDisplayName(uri)
        val updated = QuickTriggerManager.updatePresetAudio(
            context = app,
            slot = slot,
            audioUri = uri.toString(),
            audioName = label
        )
        val status = if (updated) {
            str(R.string.status_quick_trigger_preset_audio_selected, label)
        } else {
            str(R.string.status_preset_not_found)
        }
        refreshQuickTriggerPresets(status)
    }

    fun clearQuickTriggerPresetAudio(slot: Int) {
        val cleared = QuickTriggerManager.clearPresetAudio(getApplication(), slot)
        val status = if (cleared) {
            str(R.string.status_quick_trigger_preset_audio_cleared)
        } else {
            str(R.string.status_preset_not_found)
        }
        refreshQuickTriggerPresets(status)
    }

    fun onCallerNameChange(value: String) {
        _uiState.update { it.copy(callerName = value) }
    }

    fun onCallerNumberChange(value: String) {
        _uiState.update { it.copy(callerNumber = value) }
    }

    fun onCallerInputModeChange(mode: CallerInputMode) {
        prefs.edit().putString(KEY_CALLER_INPUT_MODE, mode.name).apply()
        _uiState.update {
            it.copy(
                callerInputMode = mode,
                statusMessage = if (mode == CallerInputMode.CONTACT && it.selectedContact == null) {
                    str(R.string.status_select_contact_scheduling)
                } else {
                    it.statusMessage
                }
            )
        }
    }

    fun onContactPicked(uri: Uri?) {
        if (uri == null) return
        val contact = resolveContactFromUri(uri) ?: run {
            _uiState.update { it.copy(statusMessage = str(R.string.status_contact_pick_failed)) }
            return
        }
        selectContact(contact)
    }

    fun selectContact(contact: CallContact) {
        val state = uiState.value
        val updatedPinned = state.pinnedContacts.map {
            if (sameContact(it, contact)) contact else it
        }
        val updatedRecentBase = buildList {
            var replaced = false
            state.recentContacts.forEach { existing ->
                if (sameContact(existing, contact)) {
                    add(contact)
                    replaced = true
                } else {
                    add(existing)
                }
            }
            if (!replaced) {
                add(contact)
            }
        }.takeLast(MAX_RECENT_CONTACTS)
        val updatedRecent = pruneRecentContacts(
            recentContacts = updatedRecentBase,
            pinnedContacts = updatedPinned
        )

        persistContactState(
            selectedContact = contact,
            pinned = updatedPinned,
            recent = updatedRecent
        )
        _uiState.update {
            it.copy(
                callerInputMode = CallerInputMode.CONTACT,
                selectedContact = contact,
                pinnedContacts = updatedPinned,
                recentContacts = updatedRecent,
                callerName = contact.displayName,
                callerNumber = contact.phoneNumber
            )
        }
    }

    fun togglePinnedContact(contact: CallContact) {
        val state = uiState.value
        val isPinned = state.pinnedContacts.any { sameContact(it, contact) }
        val updatedPinned = if (isPinned) {
            state.pinnedContacts.filterNot { sameContact(it, contact) }
        } else {
            buildList {
                add(contact)
                addAll(state.pinnedContacts.filterNot { sameContact(it, contact) })
            }.take(MAX_PINNED_CONTACTS)
        }
        val updatedRecent = if (isPinned) {
            pruneRecentContacts(
                recentContacts = state.recentContacts,
                pinnedContacts = updatedPinned
            )
        } else {
            val pinnedIndexInRecent = state.recentContacts.indexOfLast { sameContact(it, contact) }
            val trimmedRecent = if (pinnedIndexInRecent >= 0) {
                state.recentContacts.drop(pinnedIndexInRecent + 1)
            } else {
                state.recentContacts
            }
            pruneRecentContacts(
                recentContacts = trimmedRecent,
                pinnedContacts = updatedPinned
            )
        }
        persistContactState(
            selectedContact = state.selectedContact,
            pinned = updatedPinned,
            recent = updatedRecent
        )
        _uiState.update { it.copy(pinnedContacts = updatedPinned, recentContacts = updatedRecent) }
    }

    fun onDelaySelected(delaySeconds: Int) {
        _uiState.update {
            it.copy(
                selectedDelaySeconds = delaySeconds,
                scheduleKind = ScheduleKind.PRESET
            )
        }
        prefs.edit()
            .putInt(KEY_DELAY_SECONDS, delaySeconds)
            .putString(KEY_SCHEDULE_KIND, ScheduleKind.PRESET.name)
            .apply()
    }

    fun onScheduleKindSelected(kind: ScheduleKind) {
        _uiState.update { it.copy(scheduleKind = kind) }
        prefs.edit().putString(KEY_SCHEDULE_KIND, kind.name).apply()
    }

    fun onCustomCountdownChange(minutes: Int, seconds: Int) {
        val normalizedMinutes = minutes.coerceIn(0, 59)
        val normalizedSeconds = seconds.coerceIn(0, 59)
        _uiState.update {
            it.copy(
                customCountdownMinutes = normalizedMinutes,
                customCountdownSeconds = normalizedSeconds
            )
        }
        prefs.edit()
            .putInt(KEY_CUSTOM_COUNTDOWN_MINUTES, normalizedMinutes)
            .putInt(KEY_CUSTOM_COUNTDOWN_SECONDS, normalizedSeconds)
            .apply()
    }

    fun onCustomExactTimeChange(hour: Int, minute: Int) {
        val normalizedHour = hour.coerceIn(0, 23)
        val normalizedMinute = minute.coerceIn(0, 59)
        _uiState.update {
            it.copy(
                customExactHour = normalizedHour,
                customExactMinute = normalizedMinute
            )
        }
        prefs.edit()
            .putInt(KEY_CUSTOM_EXACT_HOUR, normalizedHour)
            .putInt(KEY_CUSTOM_EXACT_MINUTE, normalizedMinute)
            .apply()
    }

    fun addCustomPreset(kind: ScheduleKind) {
        val state = uiState.value
        val preset = when (kind) {
            ScheduleKind.CUSTOM_COUNTDOWN -> {
                val minutes = state.customCountdownMinutes.coerceIn(0, 59)
                val seconds = state.customCountdownSeconds.coerceIn(0, 59)
                CustomPreset(kind = kind, minutes = minutes, seconds = seconds)
            }
            ScheduleKind.CUSTOM_EXACT -> {
                val hour = state.customExactHour.coerceIn(0, 23)
                val minute = state.customExactMinute.coerceIn(0, 59)
                CustomPreset(kind = kind, hour = hour, minute = minute)
            }
            ScheduleKind.PRESET -> return
        }

        val existing = state.customPresets.toMutableList()
        if (existing.any { it == preset }) {
            _uiState.update { it.copy(statusMessage = str(R.string.status_preset_already_saved)) }
            return
        }
        existing.add(0, preset)

        prefs.edit()
            .putString(KEY_CUSTOM_PRESETS, serializeCustomPresets(existing))
            .apply()

        _uiState.update {
            it.copy(
                customPresets = existing,
                statusMessage = str(R.string.status_custom_preset_saved)
            )
        }
    }

    fun onCustomPresetSelected(preset: CustomPreset) {
        when (preset.kind) {
            ScheduleKind.CUSTOM_COUNTDOWN -> {
                onCustomCountdownChange(preset.minutes, preset.seconds)
                onScheduleKindSelected(ScheduleKind.CUSTOM_COUNTDOWN)
            }
            ScheduleKind.CUSTOM_EXACT -> {
                onCustomExactTimeChange(preset.hour, preset.minute)
                onScheduleKindSelected(ScheduleKind.CUSTOM_EXACT)
            }
            else -> Unit
        }
    }

    fun removeCustomPreset(preset: CustomPreset) {
        val updated = uiState.value.customPresets.filterNot { it == preset }
        prefs.edit()
            .putString(KEY_CUSTOM_PRESETS, serializeCustomPresets(updated))
            .apply()
        _uiState.update {
            it.copy(
                customPresets = updated,
                statusMessage = str(R.string.status_preset_removed)
            )
        }
    }

    fun onAudioFileSelected(uri: Uri?) {
        if (uri == null) {
            return
        }

        val app = getApplication<Application>()
        val resolver = app.contentResolver

        runCatching {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val displayName = resolveDisplayName(uri)

        prefs.edit()
            .putString(KEY_AUDIO_URI, uri.toString())
            .putString(KEY_AUDIO_NAME, displayName)
            .apply()

        _uiState.update {
            it.copy(
                selectedAudioUri = uri.toString(),
                selectedAudioName = displayName,
                statusMessage = str(R.string.status_audio_selected, displayName)
            )
        }
    }

    fun clearAudioSelection() {
        prefs.edit()
            .remove(KEY_AUDIO_URI)
            .putString(KEY_AUDIO_NAME, str(R.string.default_audio_name))
            .apply()

        _uiState.update {
            it.copy(
                selectedAudioUri = "",
                selectedAudioName = str(R.string.default_audio_name),
                statusMessage = str(R.string.status_audio_disabled)
            )
        }
    }


    fun onRecordingEnabledChange(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_RECORDING_ENABLED, enabled).apply()
        _uiState.update {
            it.copy(
                isRecordingEnabled = enabled,
                statusMessage = if (enabled) str(R.string.status_recording_enabled) else str(R.string.status_recording_disabled)
            )
        }
    }

    fun addIvrNode(title: String) {
        val safeTitle = title.trim().ifBlank { str(R.string.default_ivr_node_title) }
        updateIvrConfig { config ->
            val id = UUID.randomUUID().toString()
            val node = IvrNode(id = id, title = safeTitle)
            val nodes = config?.nodes?.toMutableMap() ?: mutableMapOf()
            nodes[id] = node
            val root = config?.rootId ?: id
            IvrConfig(rootId = root, nodes = nodes)
        }
    }

    fun setIvrRoot(nodeId: String) {
        updateIvrConfig { config ->
            val nodes = config?.nodes ?: return@updateIvrConfig config
            if (!nodes.containsKey(nodeId)) return@updateIvrConfig config
            config.copy(rootId = nodeId)
        }
    }

    fun removeIvrNode(nodeId: String) {
        updateIvrConfig { config ->
            val current = config ?: return@updateIvrConfig null
            if (!current.nodes.containsKey(nodeId)) return@updateIvrConfig current

            val remaining = current.nodes.toMutableMap().apply { remove(nodeId) }
            val cleaned = remaining.mapValues { (_, node) ->
                node.copy(routes = node.routes.filterValues { it != nodeId })
            }

            val newRoot = if (current.rootId == nodeId) {
                cleaned.keys.firstOrNull().orEmpty()
            } else {
                current.rootId
            }

            if (cleaned.isEmpty() || newRoot.isBlank()) null else IvrConfig(newRoot, cleaned)
        }
    }

    fun onIvrNodeAudioSelected(nodeId: String, uri: Uri?) {
        if (uri == null) return
        val app = getApplication<Application>()
        val resolver = app.contentResolver

        runCatching {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val label = resolveDisplayName(uri)

        updateIvrConfig { config ->
            val current = config ?: return@updateIvrConfig config
            val node = current.nodes[nodeId] ?: return@updateIvrConfig current
            val updated = node.copy(audioUri = uri.toString(), audioLabel = label)
            val nodes = current.nodes.toMutableMap().apply { put(nodeId, updated) }
            current.copy(nodes = nodes)
        }
    }

    fun clearIvrNodeAudio(nodeId: String) {
        updateIvrConfig { config ->
            val current = config ?: return@updateIvrConfig config
            val node = current.nodes[nodeId] ?: return@updateIvrConfig current
            val updated = node.copy(audioUri = "", audioLabel = "")
            val nodes = current.nodes.toMutableMap().apply { put(nodeId, updated) }
            current.copy(nodes = nodes)
        }
    }

    fun addIvrRoute(parentId: String, digit: Char, childId: String) {
        if (digit == '0') return
        updateIvrConfig { config ->
            val current = config ?: return@updateIvrConfig config
            val parent = current.nodes[parentId] ?: return@updateIvrConfig current
            if (!current.nodes.containsKey(childId)) return@updateIvrConfig current
            val updated = parent.copy(routes = parent.routes.toMutableMap().apply { put(digit, childId) })
            val nodes = current.nodes.toMutableMap().apply { put(parentId, updated) }
            current.copy(nodes = nodes)
        }
    }

    fun removeIvrRoute(parentId: String, digit: Char) {
        updateIvrConfig { config ->
            val current = config ?: return@updateIvrConfig config
            val parent = current.nodes[parentId] ?: return@updateIvrConfig current
            if (!parent.routes.containsKey(digit)) return@updateIvrConfig current
            val updated = parent.copy(routes = parent.routes.toMutableMap().apply { remove(digit) })
            val nodes = current.nodes.toMutableMap().apply { put(parentId, updated) }
            current.copy(nodes = nodes)
        }
    }

    fun exportIvrConfig(uri: Uri?) {
        if (uri == null) return
        val config = uiState.value.ivrConfig ?: return
        val xml = ivrStore.serialize(config)
        runCatching {
            getApplication<Application>().contentResolver.openOutputStream(uri, "w")?.use { out ->
                out.write(xml.toByteArray())
            }
            _uiState.update { it.copy(statusMessage = str(R.string.status_mailbox_exported)) }
        }.onFailure {
            _uiState.update { it.copy(statusMessage = str(R.string.status_export_failed)) }
        }
    }

    fun importIvrConfig(uri: Uri?) {
        if (uri == null) return
        val resolver = getApplication<Application>().contentResolver
        runCatching {
            val xml = resolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
            val parsed = ivrStore.parse(xml) ?: error("Invalid IVR config")
            ivrStore.save(getApplication(), parsed)
            _uiState.update { it.copy(ivrConfig = parsed, statusMessage = str(R.string.status_mailbox_imported)) }
        }.onFailure {
            _uiState.update { it.copy(statusMessage = str(R.string.status_import_failed)) }
        }
    }

    fun onRecordingFolderSelected(uri: Uri?) {
        if (uri == null) return

        val app = getApplication<Application>()
        val resolver = app.contentResolver

        runCatching {
            resolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }

        val folderName = runCatching { readableTreeLabel(uri) }.getOrDefault(str(R.string.default_selected_folder))

        prefs.edit()
            .putString(KEY_RECORDINGS_TREE_URI, uri.toString())
            .putString(KEY_RECORDINGS_FOLDER_NAME, folderName)
            .apply()

        _uiState.update {
            it.copy(
                recordingsFolderName = folderName,
                statusMessage = str(R.string.status_recording_folder_set, folderName)
            )
        }
    }

    fun clearRecordingFolderSelection() {
        prefs.edit()
            .remove(KEY_RECORDINGS_TREE_URI)
            .putString(KEY_RECORDINGS_FOLDER_NAME, str(R.string.default_recordings_folder))
            .apply()

        _uiState.update {
            it.copy(
                recordingsFolderName = str(R.string.default_recordings_folder),
                statusMessage = str(R.string.status_recording_folder_reset)
            )
        }
    }

    fun onMp3IvrModeEnabledChange(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MP3_IVR_MODE_ENABLED, enabled).apply()
        _uiState.update {
            it.copy(
                isMp3IvrModeEnabled = enabled,
                statusMessage = if (enabled && it.mp3IvrFolderUri.isBlank()) {
                    str(R.string.status_select_mp3_ivr_folder)
                } else {
                    it.statusMessage
                }
            )
        }
    }

    fun onMp3IvrFolderSelected(uri: Uri?) {
        if (uri == null) return
        val resolver = getApplication<Application>().contentResolver
        runCatching {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val folderName = readableTreeLabel(uri)
        prefs.edit()
            .putString(KEY_MP3_IVR_FOLDER_URI, uri.toString())
            .putString(KEY_MP3_IVR_FOLDER_NAME, folderName)
            .apply()
        _uiState.update {
            it.copy(
                mp3IvrFolderUri = uri.toString(),
                mp3IvrFolderName = folderName
            )
        }
    }

    fun clearMp3IvrFolderSelection() {
        prefs.edit()
            .remove(KEY_MP3_IVR_FOLDER_URI)
            .putString(KEY_MP3_IVR_FOLDER_NAME, str(R.string.settings_mp3_ivr_no_folder_selected))
            .apply()
        _uiState.update {
            it.copy(
                mp3IvrFolderUri = "",
                mp3IvrFolderName = str(R.string.settings_mp3_ivr_no_folder_selected)
            )
        }
    }

    fun saveProvider() {
        if (!uiState.value.hasRequiredPermissions) {
            _uiState.update { it.copy(statusMessage = str(R.string.status_grant_permissions_first)) }
            return
        }

        val providerName = uiState.value.providerName.trim().ifBlank { str(R.string.default_provider_name) }
        val registered = telecomHelper.registerOrUpdatePhoneAccount(providerName)

        prefs.edit().putString(KEY_PROVIDER_NAME, providerName).apply()

        _uiState.update {
            it.copy(
                providerName = providerName,
                statusMessage = if (registered) {
                    str(R.string.status_provider_saved)
                } else {
                    str(R.string.status_provider_register_failed)
                }
            )
        }

        refreshProviderStatus()
    }

    fun openCallingAccountsIntent(): Intent {
        return Intent(TelecomManager.ACTION_CHANGE_PHONE_ACCOUNTS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun completeOnboarding() {
        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETE, true).apply()
        _uiState.update { it.copy(isOnboardingComplete = true) }
    }

    fun canScheduleExactAlarms(): Boolean {
        return FakeCallAlarmScheduler.canScheduleExact(getApplication())
    }

    fun openExactAlarmSettingsIntent(): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.parse("package:${getApplication<Application>().packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            Intent()
        }
    }

    fun onTriggerOrCancelClicked() {
        if (uiState.value.isTimerRunning) {
            cancelTimer()
        } else {
            scheduleFakeCall()
        }
    }

    private fun scheduleFakeCall() {
        val state = uiState.value
        if (!state.hasRequiredPermissions) {
            _uiState.update { it.copy(statusMessage = str(R.string.status_grant_permissions_scheduling)) }
            return
        }
        if (!state.isProviderEnabled) {
            _uiState.update {
                it.copy(statusMessage = str(R.string.status_enable_calling_accounts))
            }
            return
        }

        val (resolvedName, resolvedNumber) = resolveCaller(state)
        val number = resolvedNumber.trim()
        if (number.isBlank()) {
            _uiState.update {
                it.copy(
                    statusMessage = if (state.callerInputMode == CallerInputMode.CONTACT) {
                        str(R.string.status_select_contact_scheduling)
                    } else {
                        str(R.string.status_enter_caller_number_scheduling)
                    }
                )
            }
            return
        }

        prefs.edit()
            .putString(KEY_CALLER_NAME, resolvedName)
            .putString(KEY_CALLER_NUMBER, number)
            .apply()

        val now = System.currentTimeMillis()
        val selectedDelaySeconds = when (state.scheduleKind) {
            ScheduleKind.PRESET -> state.selectedDelaySeconds
            ScheduleKind.CUSTOM_COUNTDOWN -> customCountdownSeconds(state)
            ScheduleKind.CUSTOM_EXACT -> 0
        }

        val triggerAtMillis = when (state.scheduleKind) {
            ScheduleKind.CUSTOM_EXACT -> computeNextExactTriggerMillis(
                state.customExactHour,
                state.customExactMinute
            )
            else -> now + selectedDelaySeconds * 1_000L
        }

        if (triggerAtMillis <= now + 1_000L) {
            val telecomHelper = TelecomHelper(getApplication())
            telecomHelper.registerOrUpdatePhoneAccount(state.providerName)
            val triggered = if (telecomHelper.isAccountEnabled()) {
                telecomHelper.triggerIncomingCall(resolvedName, number)
            } else {
                false
            }

            prefs.edit().remove(KEY_TIMER_ENDS_AT).apply()
            prefs.edit().putInt(KEY_ACTIVE_PRESET_SLOT, -1).apply()
            _uiState.update {
                it.copy(
                    isTimerRunning = false,
                    timerEndsAtMillis = 0L,
                    statusMessage = if (triggered) {
                        str(R.string.status_triggering_now)
                    } else {
                        str(R.string.status_trigger_failed)
                    }
                )
            }
            QuickTriggerManager.refreshQuickSettingsTiles(getApplication())
            return
        }

        FakeCallAlarmScheduler.cancel(getApplication())
        val scheduled = FakeCallAlarmScheduler.scheduleExact(
            context = getApplication(),
            triggerAtMillis = triggerAtMillis,
            callerName = resolvedName,
            callerNumber = number,
            providerName = state.providerName
        )

        if (!scheduled) {
            _uiState.update {
                it.copy(statusMessage = str(R.string.status_enable_exact_alarms))
            }
            return
        }

        prefs.edit()
            .putLong(KEY_TIMER_ENDS_AT, triggerAtMillis)
            .putInt(KEY_ACTIVE_PRESET_SLOT, -1)
            .apply()
        _uiState.update {
            it.copy(
                isTimerRunning = true,
                timerEndsAtMillis = triggerAtMillis,
                statusMessage = buildScheduleStatus(state.scheduleKind, selectedDelaySeconds, triggerAtMillis)
            )
        }
        QuickTriggerManager.refreshQuickSettingsTiles(getApplication())
    }

    private fun cancelTimer() {
        FakeCallAlarmScheduler.cancel(getApplication())
        prefs.edit()
            .remove(KEY_TIMER_ENDS_AT)
            .putInt(KEY_ACTIVE_PRESET_SLOT, -1)
            .apply()
        _uiState.update {
            it.copy(
                isTimerRunning = false,
                timerEndsAtMillis = 0L,
                statusMessage = str(R.string.status_timer_cancelled)
            )
        }
        QuickTriggerManager.refreshQuickSettingsTiles(getApplication())
    }

    private fun syncRunningTimerState() {
        val endsAt = prefs.getLong(KEY_TIMER_ENDS_AT, 0L)
        if (endsAt <= 0L) {
            if (uiState.value.isTimerRunning || uiState.value.timerEndsAtMillis != 0L) {
                _uiState.update { it.copy(isTimerRunning = false, timerEndsAtMillis = 0L) }
            }
            return
        }

        val now = System.currentTimeMillis()
        if (now >= endsAt) {
            FakeCallAlarmScheduler.cancel(getApplication())
            prefs.edit().remove(KEY_TIMER_ENDS_AT).apply()
            _uiState.update {
                it.copy(isTimerRunning = false, timerEndsAtMillis = 0L)
            }
        } else {
            if (!uiState.value.isTimerRunning || uiState.value.timerEndsAtMillis != endsAt) {
                _uiState.update {
                    it.copy(isTimerRunning = true, timerEndsAtMillis = endsAt)
                }
            }
        }
    }

    private fun syncAlarmModeState() {
        val now = System.currentTimeMillis()
        var changed = false
        val updated = AlarmModeRepository.load(getApplication()).map { alarm ->
            if (!alarm.enabled) {
                if (alarm.nextTriggerAtMillis != 0L) {
                    changed = true
                    alarm.copy(nextTriggerAtMillis = 0L)
                } else {
                    alarm
                }
            } else if (alarm.nextTriggerAtMillis <= now + 1_000L) {
                val next = AlarmModeScheduler.computeNextTriggerAtMillis(alarm)
                if (next > 0L && next != alarm.nextTriggerAtMillis) {
                    changed = true
                    alarm.copy(nextTriggerAtMillis = next)
                } else {
                    alarm
                }
            } else {
                alarm
            }
        }
        if (changed) {
            AlarmModeRepository.replaceAll(getApplication(), updated)
        }
        if (uiState.value.alarmModeItems != updated) {
            _uiState.update { it.copy(alarmModeItems = updated) }
        }
    }

    private fun ensurePhoneAccountRegistered() {
        telecomHelper.registerOrUpdatePhoneAccount(uiState.value.providerName)
    }

    private fun refreshProviderStatus() {
        if (!uiState.value.hasRequiredPermissions) {
            _uiState.update { it.copy(isProviderEnabled = false) }
            return
        }
        val enabled = telecomHelper.isAccountEnabled()
        _uiState.update { it.copy(isProviderEnabled = enabled) }
    }

    private suspend fun checkForUpdatesOnStartup() {
        when (val result = updateChecker.checkForUpdate(BuildConfig.VERSION_NAME)) {
            is UpdateCheckResult.UpdateAvailable -> {
                _uiState.update { it.copy(startupUpdate = result.release) }
            }
            else -> Unit
        }
    }

    private fun updateIvrConfig(transform: (IvrConfig?) -> IvrConfig?) {
        val updated = transform(uiState.value.ivrConfig)
        ivrStore.save(getApplication(), updated)
        _uiState.update { it.copy(ivrConfig = updated) }
    }

    private fun saveQuickTriggerDefaults(state: FakeCallUiState) {
        val existingDefaults = QuickTriggerManager.loadDefaults(getApplication())
        QuickTriggerManager.saveDefaults(
            context = getApplication(),
            defaults = QuickTriggerDefaults(
                callerName = state.quickTriggerCallerName,
                callerNumber = state.quickTriggerCallerNumber,
                delaySeconds = state.quickTriggerDelaySeconds,
                useCustomAudioOverride = existingDefaults.useCustomAudioOverride,
                customAudioUri = existingDefaults.customAudioUri,
                customAudioName = existingDefaults.customAudioName
            )
        )
        _uiState.update {
            it.copy(
                quickTriggerCallerName = state.quickTriggerCallerName,
                quickTriggerCallerNumber = state.quickTriggerCallerNumber,
                quickTriggerDelaySeconds = state.quickTriggerDelaySeconds
            )
        }
    }

    private fun refreshQuickTriggerPresets(statusMessage: String) {
        val application = getApplication<Application>()
        _uiState.update {
            it.copy(
                quickTriggerPresets = QuickTriggerManager.loadPresets(application),
                quickTriggerDefaultPresetSlot = QuickTriggerManager.loadDefaultPresetSlot(application),
                statusMessage = statusMessage
            )
        }
    }

    private fun customCountdownSeconds(state: FakeCallUiState): Int {
        val minutes = state.customCountdownMinutes.coerceAtLeast(0)
        val seconds = state.customCountdownSeconds.coerceAtLeast(0)
        return minutes * 60 + seconds
    }

    private fun resolveCaller(state: FakeCallUiState): Pair<String, String> {
        return if (state.callerInputMode == CallerInputMode.CONTACT) {
            val contact = state.selectedContact
            if (contact != null) {
                contact.displayName to contact.phoneNumber
            } else {
                "" to ""
            }
        } else {
            state.callerName to state.callerNumber
        }
    }

    private fun computeNextExactTriggerMillis(hour: Int, minute: Int): Long {
        val now = ZonedDateTime.now()
        var target = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        if (!target.isAfter(now)) {
            target = target.plusDays(1)
        }
        return target.toInstant().toEpochMilli()
    }

    private fun buildScheduleStatus(
        kind: ScheduleKind,
        delaySeconds: Int,
        triggerAtMillis: Long
    ): String {
        return when (kind) {
            ScheduleKind.CUSTOM_EXACT -> {
                val time = Instant.ofEpochMilli(triggerAtMillis)
                    .atZone(ZoneId.systemDefault())
                    .toLocalTime()
                val locale = getApplication<Application>().resources.configuration.locales[0]
                    ?: Locale.getDefault()
                val formatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
                    .withLocale(locale)
                str(R.string.status_call_scheduled_for, time.format(formatter))
            }
            else -> str(
                R.string.status_timer_started_for,
                DelayFormatter.formatLong(getApplication(), delaySeconds)
            )
        }
    }

    private fun formatAlarmClockTime(hour: Int, minute: Int): String {
        val locale = getApplication<Application>().resources.configuration.locales[0] ?: Locale.getDefault()
        val formatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
        return ZonedDateTime.now()
            .withHour(hour.coerceIn(0, 23))
            .withMinute(minute.coerceIn(0, 59))
            .withSecond(0)
            .withNano(0)
            .toLocalTime()
            .format(formatter)
    }

    private fun formatAlarmRepeatDays(context: Context, days: Set<Int>): String {
        if (days.isEmpty()) return str(R.string.alarm_repeat_once)
        return days.sorted().joinToString(", ") { day ->
            AlarmModeScheduler.dayLabel(context, day)
        }
    }

    private fun parseCustomPresets(raw: String): List<CustomPreset> {
        if (raw.isBlank()) return emptyList()
        return raw.split("|").mapNotNull { token ->
            val parts = token.split(",")
            if (parts.isEmpty()) return@mapNotNull null
            return@mapNotNull when (parts[0]) {
                "C" -> {
                    if (parts.size < 3) return@mapNotNull null
                    val minutes = parts[1].toIntOrNull() ?: return@mapNotNull null
                    val seconds = parts[2].toIntOrNull() ?: return@mapNotNull null
                    CustomPreset(
                        kind = ScheduleKind.CUSTOM_COUNTDOWN,
                        minutes = minutes.coerceIn(0, 59),
                        seconds = seconds.coerceIn(0, 59)
                    )
                }
                "E" -> {
                    if (parts.size < 3) return@mapNotNull null
                    val hour = parts[1].toIntOrNull() ?: return@mapNotNull null
                    val minute = parts[2].toIntOrNull() ?: return@mapNotNull null
                    CustomPreset(
                        kind = ScheduleKind.CUSTOM_EXACT,
                        hour = hour.coerceIn(0, 23),
                        minute = minute.coerceIn(0, 59)
                    )
                }
                else -> null
            }
        }
    }

    private fun serializeCustomPresets(presets: List<CustomPreset>): String {
        return presets.joinToString("|") { preset ->
            when (preset.kind) {
                ScheduleKind.CUSTOM_COUNTDOWN -> "C,${preset.minutes},${preset.seconds}"
                ScheduleKind.CUSTOM_EXACT -> "E,${preset.hour},${preset.minute}"
                else -> ""
            }
        }.trim('|')
    }

    private fun fallbackNameFromUri(uri: Uri): String {
        val retriever = MediaMetadataRetriever()
        return runCatching {
            retriever.setDataSource(getApplication(), uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
        }.getOrNull().orEmpty().ifBlank {
            uri.lastPathSegment ?: str(R.string.default_selected_audio)
        }.also {
            runCatching { retriever.release() }
        }
    }

    private fun resolveDisplayName(uri: Uri): String {
        val resolver = getApplication<Application>().contentResolver
        val displayName = runCatching {
            resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
                }
        }.getOrNull()
        return displayName ?: fallbackNameFromUri(uri)
    }

    private fun readableTreeLabel(uri: Uri): String {
        val treeDocId = DocumentsContract.getTreeDocumentId(uri)
        if (treeDocId.isBlank()) return str(R.string.default_selected_folder)
        val parts = treeDocId.split(':', limit = 2)
        return when {
            parts.size == 2 && parts[0] == "primary" && parts[1].isNotBlank() -> parts[1]
            parts.size == 2 && parts[1].isNotBlank() -> parts[1]
            else -> treeDocId
        }
    }

    private fun resolveContactFromUri(uri: Uri): CallContact? {
        val resolver = getApplication<Application>().contentResolver
        val phoneProjection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI,
            ContactsContract.CommonDataKinds.Phone.PHOTO_URI
        )

        runCatching {
            resolver.query(uri, phoneProjection, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val idIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val lookupIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY)
                val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val thumbIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI)
                val photoIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)

                val contactId = if (idIndex >= 0) cursor.getLong(idIndex) else 0L
                val lookupKey = if (lookupIndex >= 0) cursor.getString(lookupIndex).orEmpty() else ""
                val number = if (numberIndex >= 0) cursor.getString(numberIndex).orEmpty().trim() else ""
                if (number.isBlank()) return@use null
                val name = if (nameIndex >= 0) cursor.getString(nameIndex).orEmpty() else ""
                val thumbnailUri = if (thumbIndex >= 0) cursor.getString(thumbIndex).orEmpty() else ""
                val photoUri = if (photoIndex >= 0) cursor.getString(photoIndex).orEmpty() else ""
                val resolvedPhotoUri = thumbnailUri.ifBlank { photoUri }
                val avatarBase64 = encodeContactAvatarBase64(
                    contactId = contactId,
                    lookupKey = lookupKey,
                    photoUri = resolvedPhotoUri
                )

                return CallContact(
                    id = contactId,
                    displayName = name.ifBlank { number },
                    phoneNumber = number,
                    photoUri = resolvedPhotoUri,
                    avatarBase64 = avatarBase64
                )
            }
        }

        val contactProjection = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.DISPLAY_NAME,
            ContactsContract.Contacts.PHOTO_URI
        )
        var id = 0L
        var name = ""
        var photoUri = ""
        runCatching {
            resolver.query(uri, contactProjection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idIndex = cursor.getColumnIndex(ContactsContract.Contacts._ID)
                    val nameIndex = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                    val photoIndex = cursor.getColumnIndex(ContactsContract.Contacts.PHOTO_URI)
                    if (idIndex >= 0) id = cursor.getLong(idIndex)
                    if (nameIndex >= 0) name = cursor.getString(nameIndex).orEmpty()
                    if (photoIndex >= 0) photoUri = cursor.getString(photoIndex).orEmpty()
                }
            }
        }
        if (id <= 0L) return null
        val number = resolvePrimaryNumberForContact(id).trim()
        if (number.isBlank()) return null
        return CallContact(
            id = id,
            displayName = name.ifBlank { number },
            phoneNumber = number,
            photoUri = photoUri,
            avatarBase64 = encodeContactAvatarBase64(contactId = id, lookupKey = "", photoUri = photoUri)
        )
    }

    private fun encodeContactAvatarBase64(
        contactId: Long,
        lookupKey: String,
        photoUri: String
    ): String {
        val resolver = getApplication<Application>().contentResolver

        fun decodeFromUri(uriString: String): Bitmap? {
            if (uriString.isBlank()) return null
            return runCatching {
                resolver.openInputStream(Uri.parse(uriString))?.use(BitmapFactory::decodeStream)
            }.getOrNull()
        }

        val directPhoto = decodeFromUri(photoUri)
        val lookupPhoto = if (directPhoto == null && contactId > 0L) {
            val lookupUri = if (lookupKey.isNotBlank()) {
                ContactsContract.Contacts.getLookupUri(contactId, lookupKey)
            } else {
                ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId)
            }
            runCatching {
                ContactsContract.Contacts.openContactPhotoInputStream(resolver, lookupUri, true)
                    ?.use(BitmapFactory::decodeStream)
            }.getOrNull()
        } else {
            null
        }

        val bitmap = directPhoto ?: lookupPhoto ?: return ""
        return bitmapToBase64(bitmap)
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val scaled = Bitmap.createScaledBitmap(bitmap, 128, 128, true)
        val bytes = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.PNG, 100, bytes)
        if (scaled !== bitmap) {
            scaled.recycle()
        }
        return Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP)
    }

    private fun resolvePrimaryNumberForContact(contactId: Long): String {
        val resolver = getApplication<Application>().contentResolver
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val selection = "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID}=?"
        val args = arrayOf(contactId.toString())
        return runCatching {
            resolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                selection,
                args,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    if (index >= 0) cursor.getString(index).orEmpty() else ""
                } else {
                    ""
                }
            }.orEmpty()
        }.getOrDefault("")
    }

    private fun persistContactState(
        selectedContact: CallContact?,
        pinned: List<CallContact>,
        recent: List<CallContact>
    ) {
        prefs.edit()
            .putString(KEY_SELECTED_CONTACT, serializeContact(selectedContact))
            .putString(KEY_PINNED_CONTACTS, serializeContactList(pinned))
            .putString(KEY_RECENT_CONTACTS, serializeContactList(recent))
            .apply()
    }

    private fun pruneRecentContacts(
        recentContacts: List<CallContact>,
        pinnedContacts: List<CallContact>
    ): List<CallContact> {
        val limit = if (pinnedContacts.isNotEmpty()) 1 else 3
        val deduped = buildList {
            recentContacts.forEach { contact ->
                if (none { existing -> sameContact(existing, contact) }) {
                    add(contact)
                }
            }
        }
        return deduped
            .filterNot { recent -> pinnedContacts.any { sameContact(it, recent) } }
            .takeLast(limit)
    }

    private fun sameContact(a: CallContact, b: CallContact): Boolean {
        return if (a.id > 0 && b.id > 0) a.id == b.id else a.phoneNumber == b.phoneNumber
    }

    private fun serializeContact(contact: CallContact?): String {
        if (contact == null) return ""
        return JSONObject().apply {
            put("id", contact.id)
            put("name", contact.displayName)
            put("number", contact.phoneNumber)
            put("photoUri", contact.photoUri)
            put("avatarBase64", contact.avatarBase64)
        }.toString()
    }

    private fun parseContact(raw: String): CallContact? {
        if (raw.isBlank()) return null
        return runCatching {
            val obj = JSONObject(raw)
            val number = obj.optString("number").orEmpty()
            if (number.isBlank()) return@runCatching null
            CallContact(
                id = obj.optLong("id", 0L),
                displayName = obj.optString("name").orEmpty().ifBlank { number },
                phoneNumber = number,
                photoUri = obj.optString("photoUri").orEmpty(),
                avatarBase64 = obj.optString("avatarBase64").orEmpty()
            )
        }.getOrNull()
    }

    private fun serializeContactList(contacts: List<CallContact>): String {
        return JSONArray().apply {
            contacts.forEach { contact ->
                put(
                    JSONObject().apply {
                        put("id", contact.id)
                        put("name", contact.displayName)
                        put("number", contact.phoneNumber)
                        put("photoUri", contact.photoUri)
                        put("avatarBase64", contact.avatarBase64)
                    }
                )
            }
        }.toString()
    }

    private fun parseContactList(raw: String): List<CallContact> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val obj = array.optJSONObject(index) ?: continue
                    val number = obj.optString("number").orEmpty().trim()
                    if (number.isBlank()) continue
                    add(
                        CallContact(
                            id = obj.optLong("id", 0L),
                            displayName = obj.optString("name").orEmpty().ifBlank { number },
                            phoneNumber = number,
                            photoUri = obj.optString("photoUri").orEmpty(),
                            avatarBase64 = obj.optString("avatarBase64").orEmpty()
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    companion object {
        private const val PREFS_NAME = "fake_call_prefs"
        private const val KEY_PROVIDER_NAME = "provider_name"
        private const val KEY_CALLER_NAME = "caller_name"
        private const val KEY_CALLER_NUMBER = "caller_number"
        private const val KEY_CALLER_INPUT_MODE = "caller_input_mode"
        private const val KEY_SELECTED_CONTACT = "selected_contact"
        private const val KEY_PINNED_CONTACTS = "pinned_contacts"
        private const val KEY_RECENT_CONTACTS = "recent_contacts"
        private const val KEY_DELAY_SECONDS = "delay_seconds"
        private const val KEY_SCHEDULE_KIND = "schedule_kind"
        private const val KEY_CUSTOM_COUNTDOWN_MINUTES = "custom_countdown_minutes"
        private const val KEY_CUSTOM_COUNTDOWN_SECONDS = "custom_countdown_seconds"
        private const val KEY_CUSTOM_EXACT_HOUR = "custom_exact_hour"
        private const val KEY_CUSTOM_EXACT_MINUTE = "custom_exact_minute"
        private const val KEY_CUSTOM_PRESETS = "custom_presets"
        private const val KEY_TIMER_ENDS_AT = "timer_ends_at"
        private const val KEY_ACTIVE_PRESET_SLOT = "quick_trigger_active_preset_slot"
        private const val KEY_AUDIO_URI = "audio_uri"
        private const val KEY_AUDIO_NAME = "audio_name"
        private const val KEY_RECORDING_ENABLED = "recording_enabled"
        private const val KEY_RECORDINGS_TREE_URI = "recordings_tree_uri"
        private const val KEY_RECORDINGS_FOLDER_NAME = "recordings_folder_name"
        private const val KEY_MP3_IVR_MODE_ENABLED = "mp3_ivr_mode_enabled"
        private const val KEY_MP3_IVR_FOLDER_URI = "mp3_ivr_folder_uri"
        private const val KEY_MP3_IVR_FOLDER_NAME = "mp3_ivr_folder_name"
        private const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
        private const val KEY_QUICK_TRIGGER_PRESET_NAME = "quick_trigger_preset_name"
        private const val KEY_CALL_RING_TIMEOUT_SECONDS = "call_ring_timeout_seconds"
        private const val KEY_ALARM_RING_TIMEOUT_SECONDS = "alarm_ring_timeout_seconds"
        private const val DEFAULT_CALL_RING_TIMEOUT_SECONDS = 45
        private const val DEFAULT_ALARM_RING_TIMEOUT_SECONDS = 60
        private const val MAX_RECENT_CONTACTS = 12
        private const val MAX_PINNED_CONTACTS = 8

        fun formatDelay(context: Context, seconds: Int): String {
            return DelayFormatter.formatLong(context, seconds)
        }

        fun formatRingTimeout(context: Context, seconds: Int): String {
            val safeSeconds = seconds.coerceAtLeast(0)
            if (safeSeconds == 0) {
                return context.getString(R.string.settings_ring_timeout_unlimited)
            }
            return DelayFormatter.formatLong(context, safeSeconds)
        }
    }
}
