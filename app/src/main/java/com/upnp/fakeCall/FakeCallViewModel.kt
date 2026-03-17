package com.upnp.fakeCall

import android.app.Application
import android.os.Build
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.Settings
import android.telecom.TelecomManager
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
import java.util.UUID

enum class ScheduleKind {
    PRESET,
    CUSTOM_COUNTDOWN,
    CUSTOM_EXACT
}

data class CustomPreset(
    val kind: ScheduleKind,
    val minutes: Int = 0,
    val seconds: Int = 0,
    val hour: Int = 0,
    val minute: Int = 0
)

data class FakeCallUiState(
    val isOnboardingComplete: Boolean = false,
    val providerName: String = "Fake Call Provider",
    val callerName: String = "",
    val callerNumber: String = "",
    val selectedDelaySeconds: Int = 10,
    val scheduleKind: ScheduleKind = ScheduleKind.PRESET,
    val customCountdownMinutes: Int = 2,
    val customCountdownSeconds: Int = 30,
    val customExactHour: Int = 14,
    val customExactMinute: Int = 45,
    val customPresets: List<CustomPreset> = emptyList(),
    val ivrConfig: IvrConfig? = null,
    val selectedAudioUri: String = "",
    val selectedAudioName: String = "Default",
    val hasRequiredPermissions: Boolean = false,
    val isProviderEnabled: Boolean = false,
    val isTimerRunning: Boolean = false,
    val timerEndsAtMillis: Long = 0L,
    val statusMessage: String = "",
    val isRecordingEnabled: Boolean = true,
    val recordingsFolderName: String = "Downloads/FakeCall",
    val startupUpdate: ReleaseInfo? = null
)

class FakeCallViewModel(application: Application) : AndroidViewModel(application) {

    private val telecomHelper = TelecomHelper(application)
    private val prefs = application.getSharedPreferences(PREFS_NAME, 0)
    private val ivrStore = IvrConfigStore()
    private val updateChecker = UpdateChecker()

    private val _uiState = MutableStateFlow(
        FakeCallUiState(
            isOnboardingComplete = prefs.getBoolean(KEY_ONBOARDING_COMPLETE, false),
            providerName = prefs.getString(KEY_PROVIDER_NAME, "Fake Call Provider").orEmpty(),
            callerName = prefs.getString(KEY_CALLER_NAME, "").orEmpty(),
            callerNumber = prefs.getString(KEY_CALLER_NUMBER, "").orEmpty(),
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
            selectedAudioName = prefs.getString(KEY_AUDIO_NAME, "Default").orEmpty(),
            timerEndsAtMillis = prefs.getLong(KEY_TIMER_ENDS_AT, 0L),
            isRecordingEnabled = prefs.getBoolean(KEY_RECORDING_ENABLED, true),
            recordingsFolderName = prefs.getString(KEY_RECORDINGS_FOLDER_NAME, "Downloads/FakeCall").orEmpty()
        )
    )
    val uiState: StateFlow<FakeCallUiState> = _uiState.asStateFlow()

    val delayOptionsSeconds: List<Int> = listOf(0, 10, 30, 60, 120, 300)

    init {
        viewModelScope.launch {
            while (isActive) {
                syncRunningTimerState()
                if (uiState.value.hasRequiredPermissions) {
                    refreshProviderStatus()
                }
                delay(1_000L)
            }
        }

        viewModelScope.launch {
            checkForUpdatesOnStartup()
        }
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
            _uiState.update { it.copy(statusMessage = "Preset already saved.") }
            return
        }
        existing.add(0, preset)

        prefs.edit()
            .putString(KEY_CUSTOM_PRESETS, serializeCustomPresets(existing))
            .apply()

        _uiState.update {
            it.copy(
                customPresets = existing,
                statusMessage = "Custom preset saved."
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
                statusMessage = "Preset removed."
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
                statusMessage = "Disabling audio output on Call."
            )
        }
    }


    fun onRecordingEnabledChange(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_RECORDING_ENABLED, enabled).apply()
        _uiState.update {
            it.copy(
                isRecordingEnabled = enabled,
                statusMessage = if (enabled) "Call recording enabled." else "Call recording disabled."
            )
        }
    }

    fun addIvrNode(title: String) {
        val safeTitle = title.trim().ifBlank { "Menu" }
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
            _uiState.update { it.copy(statusMessage = "Mailbox exported.") }
        }.onFailure {
            _uiState.update { it.copy(statusMessage = "Export failed.") }
        }
    }

    fun importIvrConfig(uri: Uri?) {
        if (uri == null) return
        val resolver = getApplication<Application>().contentResolver
        runCatching {
            val xml = resolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
            val parsed = ivrStore.parse(xml) ?: error("Invalid IVR config")
            ivrStore.save(getApplication(), parsed)
            _uiState.update { it.copy(ivrConfig = parsed, statusMessage = "Mailbox imported.") }
        }.onFailure {
            _uiState.update { it.copy(statusMessage = "Import failed.") }
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

        val folderName = runCatching { readableTreeLabel(uri) }.getOrDefault("Selected folder")

        prefs.edit()
            .putString(KEY_RECORDINGS_TREE_URI, uri.toString())
            .putString(KEY_RECORDINGS_FOLDER_NAME, folderName)
            .apply()

        _uiState.update {
            it.copy(
                recordingsFolderName = folderName,
                statusMessage = "Recording folder set to: $folderName"
            )
        }
    }

    fun clearRecordingFolderSelection() {
        prefs.edit()
            .remove(KEY_RECORDINGS_TREE_URI)
            .putString(KEY_RECORDINGS_FOLDER_NAME, "Downloads/FakeCall")
            .apply()

        _uiState.update {
            it.copy(
                recordingsFolderName = "Downloads/FakeCall",
                statusMessage = "Recording folder reset to Downloads/FakeCall."
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
                telecomHelper.triggerIncomingCall(state.callerName, number)
            } else {
                false
            }

            prefs.edit().remove(KEY_TIMER_ENDS_AT).apply()
            _uiState.update {
                it.copy(
                    isTimerRunning = false,
                    timerEndsAtMillis = 0L,
                    statusMessage = if (triggered) {
                        "Triggering incoming call now."
                    } else {
                        "Could not trigger call. Enable provider in Calling Accounts."
                    }
                )
            }
            return
        }

        FakeCallAlarmScheduler.cancel(getApplication())
        val scheduled = FakeCallAlarmScheduler.scheduleExact(
            context = getApplication(),
            triggerAtMillis = triggerAtMillis,
            callerName = state.callerName,
            callerNumber = number,
            providerName = state.providerName
        )

        if (!scheduled) {
            _uiState.update {
                it.copy(statusMessage = "Enable exact alarms to schedule precise calls.")
            }
            return
        }

        prefs.edit().putLong(KEY_TIMER_ENDS_AT, triggerAtMillis).apply()
        _uiState.update {
            it.copy(
                isTimerRunning = true,
                timerEndsAtMillis = triggerAtMillis,
                statusMessage = buildScheduleStatus(state.scheduleKind, selectedDelaySeconds, triggerAtMillis)
            )
        }
    }

    private fun cancelTimer() {
        FakeCallAlarmScheduler.cancel(getApplication())
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

    private fun customCountdownSeconds(state: FakeCallUiState): Int {
        val minutes = state.customCountdownMinutes.coerceAtLeast(0)
        val seconds = state.customCountdownSeconds.coerceAtLeast(0)
        return minutes * 60 + seconds
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
                val formatter = DateTimeFormatter.ofPattern("HH:mm")
                "Call scheduled for ${time.format(formatter)}."
            }
            else -> "Timer started for ${formatDelay(delaySeconds)}."
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
            uri.lastPathSegment ?: "Selected audio"
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
        if (treeDocId.isBlank()) return "Selected folder"
        val parts = treeDocId.split(':', limit = 2)
        return when {
            parts.size == 2 && parts[0] == "primary" && parts[1].isNotBlank() -> parts[1]
            parts.size == 2 && parts[1].isNotBlank() -> parts[1]
            else -> treeDocId
        }
    }

    companion object {
        private const val PREFS_NAME = "fake_call_prefs"
        private const val KEY_PROVIDER_NAME = "provider_name"
        private const val KEY_CALLER_NAME = "caller_name"
        private const val KEY_CALLER_NUMBER = "caller_number"
        private const val KEY_DELAY_SECONDS = "delay_seconds"
        private const val KEY_SCHEDULE_KIND = "schedule_kind"
        private const val KEY_CUSTOM_COUNTDOWN_MINUTES = "custom_countdown_minutes"
        private const val KEY_CUSTOM_COUNTDOWN_SECONDS = "custom_countdown_seconds"
        private const val KEY_CUSTOM_EXACT_HOUR = "custom_exact_hour"
        private const val KEY_CUSTOM_EXACT_MINUTE = "custom_exact_minute"
        private const val KEY_CUSTOM_PRESETS = "custom_presets"
        private const val KEY_TIMER_ENDS_AT = "timer_ends_at"
        private const val KEY_AUDIO_URI = "audio_uri"
        private const val KEY_AUDIO_NAME = "audio_name"
        private const val KEY_RECORDING_ENABLED = "recording_enabled"
        private const val KEY_RECORDINGS_TREE_URI = "recordings_tree_uri"
        private const val KEY_RECORDINGS_FOLDER_NAME = "recordings_folder_name"
        private const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"

        fun formatDelay(seconds: Int): String {
            return when {
                seconds <= 0 -> "Now"
                seconds < 60 -> "$seconds seconds"
                seconds % 60 == 0 -> "${seconds / 60} minute${if (seconds >= 120) "s" else ""}"
                else -> "${seconds / 60}m ${seconds % 60}s"
            }
        }
    }
}
