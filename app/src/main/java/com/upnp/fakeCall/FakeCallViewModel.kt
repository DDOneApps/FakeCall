package com.upnp.fakeCall

import android.app.Application
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.telecom.TelecomManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class FakeCallUiState(
    val providerName: String = "Fake Call Provider",
    val callerName: String = "",
    val callerNumber: String = "",
    val selectedDelaySeconds: Int = 10,
    val selectedAudioUri: String = "",
    val selectedAudioName: String = "Default",
    val hasRequiredPermissions: Boolean = false,
    val isProviderEnabled: Boolean = false,
    val isTimerRunning: Boolean = false,
    val timerEndsAtMillis: Long = 0L,
    val exactScheduledAtMillis: Long = 0L,
    val statusMessage: String = ""
)

class FakeCallViewModel(application: Application) : AndroidViewModel(application) {

    private val telecomHelper = TelecomHelper(application)
    private val prefs = application.getSharedPreferences(PREFS_NAME, 0)

    private val _uiState = MutableStateFlow(
        FakeCallUiState(
            providerName = prefs.getString(KEY_PROVIDER_NAME, "Fake Call Provider").orEmpty(),
            callerName = prefs.getString(KEY_CALLER_NAME, "").orEmpty(),
            callerNumber = prefs.getString(KEY_CALLER_NUMBER, "").orEmpty(),
            selectedDelaySeconds = prefs.getInt(KEY_DELAY_SECONDS, 10),
            selectedAudioUri = prefs.getString(KEY_AUDIO_URI, "").orEmpty(),
            selectedAudioName = prefs.getString(KEY_AUDIO_NAME, "Default").orEmpty(),
            timerEndsAtMillis = prefs.getLong(KEY_TIMER_ENDS_AT, 0L),
            exactScheduledAtMillis = prefs.getLong(KEY_EXACT_SCHEDULED_AT, 0L)
        )
    )
    val uiState: StateFlow<FakeCallUiState> = _uiState.asStateFlow()

    val delayOptionsSeconds: List<Int> = listOf(0, 10, 30, 60, 120, 300)

    init {
        viewModelScope.launch {
            while (isActive) {
                syncRunningTimerState()
                syncExactScheduleState()
                if (uiState.value.hasRequiredPermissions) {
                    refreshProviderStatus()
                }
                delay(1_000L)
            }
        }
    }

    fun onPermissionStateChanged(granted: Boolean) {
        _uiState.update {
            it.copy(
                hasRequiredPermissions = granted,
                isProviderEnabled = if (granted) it.isProviderEnabled else false,
                statusMessage = if (granted) it.statusMessage else "Grant phone permissions to continue."
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

    fun onCallerNameChange(value: String) {
        _uiState.update { it.copy(callerName = value) }
    }

    fun onCallerNumberChange(value: String) {
        _uiState.update { it.copy(callerNumber = value) }
    }

    fun onDelaySelected(delaySeconds: Int) {
        _uiState.update { it.copy(selectedDelaySeconds = delaySeconds) }
        prefs.edit().putInt(KEY_DELAY_SECONDS, delaySeconds).apply()
    }

    fun onAudioFileSelected(uri: Uri?) {
        if (uri == null) return

        val app = getApplication<Application>()
        val resolver = app.contentResolver

        runCatching {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val displayName = runCatching {
            resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
                }
        }.getOrNull() ?: fallbackNameFromUri(uri)

        prefs.edit()
            .putString(KEY_AUDIO_URI, uri.toString())
            .putString(KEY_AUDIO_NAME, displayName)
            .apply()

        _uiState.update {
            it.copy(
                selectedAudioUri = uri.toString(),
                selectedAudioName = displayName,
                statusMessage = "Selected audio file: $displayName"
            )
        }
    }

    fun clearAudioSelection() {
        prefs.edit()
            .remove(KEY_AUDIO_URI)
            .putString(KEY_AUDIO_NAME, "Default")
            .apply()

        _uiState.update {
            it.copy(
                selectedAudioUri = "",
                selectedAudioName = "Default",
                statusMessage = "Using default bundled audio."
            )
        }
    }

    fun saveProvider() {
        if (!uiState.value.hasRequiredPermissions) {
            _uiState.update { it.copy(statusMessage = "Grant phone permissions first.") }
            return
        }

        val providerName = uiState.value.providerName.trim().ifBlank { "Fake Call Provider" }
        val registered = telecomHelper.registerOrUpdatePhoneAccount(providerName)

        prefs.edit().putString(KEY_PROVIDER_NAME, providerName).apply()

        _uiState.update {
            it.copy(
                providerName = providerName,
                statusMessage = if (registered) {
                    "Provider saved. Verify it is enabled in Calling Accounts."
                } else {
                    "Could not register provider. Check phone permissions and try again."
                }
            )
        }

        refreshProviderStatus()
    }

    fun scheduleExactCall(hourOfDay: Int, minute: Int): Boolean {
        val state = uiState.value
        if (!state.hasRequiredPermissions) {
            _uiState.update { it.copy(statusMessage = "Grant phone permissions before scheduling.") }
            return false
        }
        if (!state.isProviderEnabled) {
            _uiState.update { it.copy(statusMessage = "Enable provider in Calling Accounts first.") }
            return false
        }

        val number = state.callerNumber.trim()
        if (number.isBlank()) {
            _uiState.update { it.copy(statusMessage = "Enter a caller number before scheduling.") }
            return false
        }

        val app = getApplication<Application>()
        if (!ExactCallScheduler.canScheduleExactAlarms(app)) {
            _uiState.update {
                it.copy(statusMessage = "Exact alarm permission required. Grant it in system settings.")
            }
            return false
        }

        val triggerAtMillis = computeNextTriggerMillis(hourOfDay, minute)
        val scheduled = ExactCallScheduler.scheduleExactCall(
            context = app,
            triggerAtMillis = triggerAtMillis,
            callerName = state.callerName,
            callerNumber = number,
            providerName = state.providerName
        )

        if (scheduled) {
            prefs.edit().putLong(KEY_EXACT_SCHEDULED_AT, triggerAtMillis).apply()
            _uiState.update {
                it.copy(
                    exactScheduledAtMillis = triggerAtMillis,
                    statusMessage = "Call scheduled for ${formatExactTime(triggerAtMillis)}"
                )
            }
        } else {
            _uiState.update {
                it.copy(statusMessage = "Could not schedule exact alarm. Check system settings.")
            }
        }

        return scheduled
    }

    fun cancelExactSchedule() {
        ExactCallScheduler.cancel(getApplication())
        prefs.edit().remove(KEY_EXACT_SCHEDULED_AT).apply()
        _uiState.update {
            it.copy(
                exactScheduledAtMillis = 0L,
                statusMessage = "Exact scheduled call cancelled."
            )
        }
    }

    fun needsExactAlarmPermission(): Boolean {
        return !ExactCallScheduler.canScheduleExactAlarms(getApplication())
    }

    fun openCallingAccountsIntent(): Intent {
        return Intent(TelecomManager.ACTION_CHANGE_PHONE_ACCOUNTS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun openExactAlarmSettingsIntent(): Intent {
        val app = getApplication<Application>()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.parse("package:${app.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${app.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
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
            _uiState.update { it.copy(statusMessage = "Grant phone permissions before scheduling.") }
            return
        }
        if (!state.isProviderEnabled) {
            _uiState.update {
                it.copy(statusMessage = "Enable this app in system Calling Accounts first.")
            }
            return
        }

        val number = state.callerNumber.trim()
        if (number.isBlank()) {
            _uiState.update { it.copy(statusMessage = "Enter a caller number before scheduling.") }
            return
        }

        prefs.edit()
            .putString(KEY_CALLER_NAME, state.callerName)
            .putString(KEY_CALLER_NUMBER, number)
            .apply()

        FakeCallSchedulerService.start(
            context = getApplication(),
            delaySeconds = state.selectedDelaySeconds,
            callerName = state.callerName,
            callerNumber = number,
            providerName = state.providerName
        )

        if (state.selectedDelaySeconds > 0) {
            val endsAt = System.currentTimeMillis() + state.selectedDelaySeconds * 1_000L
            prefs.edit().putLong(KEY_TIMER_ENDS_AT, endsAt).apply()
            _uiState.update {
                it.copy(
                    isTimerRunning = true,
                    timerEndsAtMillis = endsAt,
                    statusMessage = "Timer started for ${formatDelay(state.selectedDelaySeconds)}."
                )
            }
        } else {
            prefs.edit().remove(KEY_TIMER_ENDS_AT).apply()
            _uiState.update {
                it.copy(
                    isTimerRunning = false,
                    timerEndsAtMillis = 0L,
                    statusMessage = "Triggering incoming call now."
                )
            }
        }
    }

    private fun cancelTimer() {
        FakeCallSchedulerService.cancel(getApplication())
        prefs.edit().remove(KEY_TIMER_ENDS_AT).apply()
        _uiState.update {
            it.copy(
                isTimerRunning = false,
                timerEndsAtMillis = 0L,
                statusMessage = "Timer cancelled."
            )
        }
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

    private fun syncExactScheduleState() {
        val exactAt = prefs.getLong(KEY_EXACT_SCHEDULED_AT, 0L)
        if (exactAt <= 0L) {
            if (uiState.value.exactScheduledAtMillis != 0L) {
                _uiState.update { it.copy(exactScheduledAtMillis = 0L) }
            }
            return
        }

        if (System.currentTimeMillis() >= exactAt) {
            prefs.edit().remove(KEY_EXACT_SCHEDULED_AT).apply()
            _uiState.update { it.copy(exactScheduledAtMillis = 0L) }
        } else if (uiState.value.exactScheduledAtMillis != exactAt) {
            _uiState.update { it.copy(exactScheduledAtMillis = exactAt) }
        }
    }

    private fun computeNextTriggerMillis(hourOfDay: Int, minute: Int): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hourOfDay)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (!target.after(now)) {
            target.add(Calendar.DAY_OF_YEAR, 1)
        }
        return target.timeInMillis
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

    private fun fallbackNameFromUri(uri: Uri): String {
        val retriever = MediaMetadataRetriever()
        return runCatching {
            retriever.setDataSource(getApplication(), uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
        }.getOrNull().orEmpty().ifBlank {
            uri.lastPathSegment ?: "Selected audio"
        }.also {
            runCatching { retriever.release() }
        }
    }

    companion object {
        private const val PREFS_NAME = "fake_call_prefs"
        private const val KEY_PROVIDER_NAME = "provider_name"
        private const val KEY_CALLER_NAME = "caller_name"
        private const val KEY_CALLER_NUMBER = "caller_number"
        private const val KEY_DELAY_SECONDS = "delay_seconds"
        private const val KEY_TIMER_ENDS_AT = "timer_ends_at"
        private const val KEY_EXACT_SCHEDULED_AT = "exact_scheduled_at"
        private const val KEY_AUDIO_URI = "audio_uri"
        private const val KEY_AUDIO_NAME = "audio_name"

        fun formatDelay(seconds: Int): String {
            return when {
                seconds <= 0 -> "Now"
                seconds < 60 -> "$seconds seconds"
                seconds % 60 == 0 -> "${seconds / 60} minute${if (seconds >= 120) "s" else ""}"
                else -> "${seconds / 60}m ${seconds % 60}s"
            }
        }

        fun formatExactTime(timeMillis: Long): String {
            return DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(timeMillis))
        }
    }
}
