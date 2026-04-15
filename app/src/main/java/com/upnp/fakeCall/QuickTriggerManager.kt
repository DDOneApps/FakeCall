package com.upnp.fakeCall

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.TileService
import org.json.JSONArray
import org.json.JSONObject

data class QuickTriggerDefaults(
    val callerName: String = "",
    val callerNumber: String = "",
    val delaySeconds: Int = QuickTriggerManager.DEFAULT_DELAY_SECONDS,
    val useCustomAudioOverride: Boolean = false,
    val customAudioUri: String = "",
    val customAudioName: String = ""
)

data class TriggerScheduleRequest(
    val callerName: String,
    val callerNumber: String,
    val delaySeconds: Int,
    val providerName: String,
    val usePresetAudioOverride: Boolean = false,
    val presetAudioUri: String = "",
    val presetAudioName: String = ""
)

data class QuickTriggerPreset(
    val id: Long,
    val title: String,
    val callerName: String,
    val callerNumber: String,
    val delaySeconds: Int,
    val useCustomAudio: Boolean = false,
    val customAudioUri: String = "",
    val customAudioName: String = ""
)

enum class QuickTriggerExecution {
    IMMEDIATE,
    SCHEDULED,
    FAILED
}

enum class QuickTriggerPresetSaveResult {
    SAVED,
    LIMIT_REACHED,
    INVALID_DATA
}

object QuickTriggerManager {
    private const val PREFS_NAME = "fake_call_prefs"
    private const val KEY_PROVIDER_NAME = "provider_name"
    private const val KEY_CALLER_NAME = "caller_name"
    private const val KEY_CALLER_NUMBER = "caller_number"
    private const val KEY_DELAY_SECONDS = "delay_seconds"
    private const val KEY_TIMER_ENDS_AT = "timer_ends_at"
    private const val KEY_QUICK_TRIGGER_CALLER_NAME = "quick_trigger_caller_name"
    private const val KEY_QUICK_TRIGGER_CALLER_NUMBER = "quick_trigger_caller_number"
    private const val KEY_QUICK_TRIGGER_DELAY_SECONDS = "quick_trigger_delay_seconds"
    private const val KEY_QUICK_TRIGGER_USE_CUSTOM_AUDIO = "quick_trigger_use_custom_audio"
    private const val KEY_QUICK_TRIGGER_CUSTOM_AUDIO_URI = "quick_trigger_custom_audio_uri"
    private const val KEY_QUICK_TRIGGER_CUSTOM_AUDIO_NAME = "quick_trigger_custom_audio_name"
    private const val KEY_QUICK_TRIGGER_PRESETS = "quick_trigger_presets_v1"
    private const val KEY_ACTIVE_PRESET_SLOT = "quick_trigger_active_preset_slot"
    private const val KEY_DEFAULT_PRESET_SLOT = "quick_trigger_default_preset_slot"
    private const val KEY_RUNTIME_AUDIO_OVERRIDE_ENABLED = "runtime_audio_override_enabled"
    private const val KEY_RUNTIME_AUDIO_OVERRIDE_URI = "runtime_audio_override_uri"
    private const val KEY_RUNTIME_AUDIO_OVERRIDE_NAME = "runtime_audio_override_name"
    private const val SHORTCUT_ID_PREFIX = "quick_trigger_preset_"
    const val DEFAULT_DELAY_SECONDS = 10
    const val MAX_PRESETS = 5
    const val ACTION_TRIGGER_PRESET = "com.upnp.fakeCall.action.TRIGGER_PRESET"
    const val EXTRA_PRESET_SLOT = "preset_slot"

    fun loadDefaults(context: Context): QuickTriggerDefaults {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val callerName = prefs.getString(KEY_QUICK_TRIGGER_CALLER_NAME, null)
            ?: prefs.getString(KEY_CALLER_NAME, "").orEmpty()
        val callerNumber = prefs.getString(KEY_QUICK_TRIGGER_CALLER_NUMBER, null)
            ?: prefs.getString(KEY_CALLER_NUMBER, "").orEmpty()
        val delaySeconds = prefs.getInt(
            KEY_QUICK_TRIGGER_DELAY_SECONDS,
            prefs.getInt(KEY_DELAY_SECONDS, DEFAULT_DELAY_SECONDS)
        ).coerceAtLeast(0)
        val useCustomAudioOverride = prefs.getBoolean(KEY_QUICK_TRIGGER_USE_CUSTOM_AUDIO, false)
        val customAudioUri = prefs.getString(KEY_QUICK_TRIGGER_CUSTOM_AUDIO_URI, "").orEmpty()
        val customAudioName = prefs.getString(KEY_QUICK_TRIGGER_CUSTOM_AUDIO_NAME, "").orEmpty()
        return QuickTriggerDefaults(
            callerName = callerName,
            callerNumber = callerNumber,
            delaySeconds = delaySeconds,
            useCustomAudioOverride = useCustomAudioOverride,
            customAudioUri = customAudioUri,
            customAudioName = customAudioName
        )
    }

    fun saveDefaults(context: Context, defaults: QuickTriggerDefaults) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_QUICK_TRIGGER_CALLER_NAME, defaults.callerName)
            .putString(KEY_QUICK_TRIGGER_CALLER_NUMBER, defaults.callerNumber)
            .putInt(KEY_QUICK_TRIGGER_DELAY_SECONDS, defaults.delaySeconds.coerceAtLeast(0))
            .putBoolean(KEY_QUICK_TRIGGER_USE_CUSTOM_AUDIO, defaults.useCustomAudioOverride)
            .putString(KEY_QUICK_TRIGGER_CUSTOM_AUDIO_URI, defaults.customAudioUri)
            .putString(KEY_QUICK_TRIGGER_CUSTOM_AUDIO_NAME, defaults.customAudioName)
            .apply()
    }

    fun saveCurrentDefaultsAsPreset(
        context: Context,
        customTitle: String?
    ): QuickTriggerPresetSaveResult {
        val defaults = loadDefaults(context)
        if (defaults.callerNumber.isBlank()) return QuickTriggerPresetSaveResult.INVALID_DATA
        val current = loadPresets(context).toMutableList()
        if (current.size >= MAX_PRESETS) return QuickTriggerPresetSaveResult.LIMIT_REACHED

        val title = customTitle.orEmpty()
            .trim()
            .ifBlank { defaults.callerName.ifBlank { defaults.callerNumber } }
            .take(30)

        val preset = QuickTriggerPreset(
            id = System.currentTimeMillis(),
            title = title,
            callerName = defaults.callerName,
            callerNumber = defaults.callerNumber,
            delaySeconds = defaults.delaySeconds,
            useCustomAudio = defaults.useCustomAudioOverride,
            customAudioUri = defaults.customAudioUri,
            customAudioName = defaults.customAudioName
        )
        current.add(preset)
        savePresets(context, current)
        if (current.size == 1) {
            saveDefaultPresetSlot(context, 1)
        }
        updateLauncherShortcuts(context)
        requestTileRefresh(context)
        return QuickTriggerPresetSaveResult.SAVED
    }

    fun loadPresets(context: Context): List<QuickTriggerPreset> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_QUICK_TRIGGER_PRESETS, "").orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val callerNumber = item.optString("callerNumber").orEmpty()
                    if (callerNumber.isBlank()) continue
                    add(
                        QuickTriggerPreset(
                            id = item.optLong("id", index.toLong()),
                            title = item.optString("title").orEmpty().ifBlank { callerNumber }.take(30),
                            callerName = item.optString("callerName").orEmpty(),
                            callerNumber = callerNumber,
                            delaySeconds = item.optInt("delaySeconds", DEFAULT_DELAY_SECONDS).coerceAtLeast(0),
                            useCustomAudio = item.optBoolean("useCustomAudio", false),
                            customAudioUri = item.optString("customAudioUri").orEmpty(),
                            customAudioName = item.optString("customAudioName").orEmpty()
                        )
                    )
                }
            }.take(MAX_PRESETS)
        }.getOrDefault(emptyList())
    }

    fun removePreset(context: Context, slot: Int): Boolean {
        val index = slot - 1
        val current = loadPresets(context).toMutableList()
        if (index !in current.indices) return false
        current.removeAt(index)
        savePresets(context, current)
        val activeSlot = loadActivePresetSlot(context)
        when {
            activeSlot == null -> Unit
            activeSlot == slot -> saveActivePresetSlot(context, null)
            activeSlot > slot -> saveActivePresetSlot(context, activeSlot - 1)
        }
        val defaultSlot = loadDefaultPresetSlot(context, current)
        when {
            defaultSlot == null -> Unit
            defaultSlot == slot -> saveDefaultPresetSlot(context, null)
            defaultSlot > slot -> saveDefaultPresetSlot(context, defaultSlot - 1)
        }
        updateLauncherShortcuts(context)
        requestTileRefresh(context)
        return true
    }

    fun applyPresetToDefaults(context: Context, slot: Int): Boolean {
        val preset = getPresetBySlot(context, slot) ?: return false
        saveDefaults(
            context = context,
            defaults = QuickTriggerDefaults(
                callerName = preset.callerName,
                callerNumber = preset.callerNumber,
                delaySeconds = preset.delaySeconds,
                useCustomAudioOverride = preset.useCustomAudio,
                customAudioUri = preset.customAudioUri,
                customAudioName = preset.customAudioName
            )
        )
        return true
    }

    fun getPresetBySlot(context: Context, slot: Int): QuickTriggerPreset? {
        val index = slot - 1
        val presets = loadPresets(context)
        return presets.getOrNull(index)
    }

    fun setDefaultPresetSlot(context: Context, slot: Int?): Boolean {
        if (slot == null) {
            saveDefaultPresetSlot(context, null)
            return true
        }
        val presets = loadPresets(context)
        val index = slot - 1
        if (index !in presets.indices) return false
        saveDefaultPresetSlot(context, slot)
        return true
    }

    fun loadDefaultPresetSlot(context: Context): Int? {
        return loadDefaultPresetSlot(context, loadPresets(context))
    }

    fun updatePresetAudioMode(context: Context, slot: Int, enabled: Boolean): Boolean {
        return updatePreset(context, slot) { preset ->
            preset.copy(useCustomAudio = enabled)
        }
    }

    fun updatePresetAudio(context: Context, slot: Int, audioUri: String, audioName: String): Boolean {
        return updatePreset(context, slot) { preset ->
            preset.copy(
                useCustomAudio = true,
                customAudioUri = audioUri,
                customAudioName = audioName
            )
        }
    }

    fun clearPresetAudio(context: Context, slot: Int): Boolean {
        return updatePreset(context, slot) { preset ->
            preset.copy(customAudioUri = "", customAudioName = "")
        }
    }

    fun loadActivePresetSlot(context: Context): Int? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val slot = prefs.getInt(KEY_ACTIVE_PRESET_SLOT, -1)
        if (slot <= 0) return null
        val endsAt = prefs.getLong(KEY_TIMER_ENDS_AT, 0L)
        return if (endsAt > System.currentTimeMillis()) slot else null
    }

    fun executePreset(context: Context, slot: Int): QuickTriggerExecution {
        val preset = getPresetBySlot(context, slot) ?: return QuickTriggerExecution.FAILED
        val request = resolveRequest(
            context = context,
            callerName = preset.callerName,
            callerNumber = preset.callerNumber,
            delaySeconds = preset.delaySeconds
        )?.copy(
            usePresetAudioOverride = preset.useCustomAudio,
            presetAudioUri = preset.customAudioUri,
            presetAudioName = preset.customAudioName
        )
            ?: return QuickTriggerExecution.FAILED
        return execute(
            context = context,
            request = request,
            presetSlot = slot
        )
    }

    fun executeFromInputs(
        context: Context,
        callerName: String?,
        callerNumber: String?,
        delaySeconds: Int?,
        presetSlot: Int? = null
    ): QuickTriggerExecution {
        val request = resolveRequest(context, callerName, callerNumber, delaySeconds)
            ?: return QuickTriggerExecution.FAILED
        return execute(context, request, presetSlot)
    }

    fun executeFromDefaults(context: Context): QuickTriggerExecution {
        val defaultSlot = loadDefaultPresetSlot(context)
        if (defaultSlot != null) {
            return executePreset(context, defaultSlot)
        }
        val defaults = loadDefaults(context)
        return executeFromInputs(
            context = context,
            callerName = defaults.callerName,
            callerNumber = defaults.callerNumber,
            delaySeconds = defaults.delaySeconds
        )
    }

    fun resolveRequest(
        context: Context,
        callerName: String?,
        callerNumber: String?,
        delaySeconds: Int?
    ): TriggerScheduleRequest? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val defaults = loadDefaults(context)
        val resolvedName = callerName?.takeIf { it.isNotBlank() } ?: defaults.callerName
        val resolvedNumber = callerNumber?.takeIf { it.isNotBlank() } ?: defaults.callerNumber
        val resolvedDelay = delaySeconds ?: defaults.delaySeconds
        val defaultProviderName = context.getString(R.string.default_provider_name)
        val providerName = prefs.getString(KEY_PROVIDER_NAME, defaultProviderName).orEmpty()
            .ifBlank { defaultProviderName }

        if (resolvedNumber.isBlank()) return null

        return TriggerScheduleRequest(
            callerName = resolvedName,
            callerNumber = resolvedNumber,
            delaySeconds = resolvedDelay.coerceAtLeast(0),
            providerName = providerName,
            usePresetAudioOverride = defaults.useCustomAudioOverride,
            presetAudioUri = defaults.customAudioUri,
            presetAudioName = defaults.customAudioName
        )
    }

    fun updateLauncherShortcuts(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return
        val shortcutManager = context.getSystemService(ShortcutManager::class.java) ?: return
        val presets = loadPresets(context)
        val shortcuts = presets.mapIndexed { index, preset ->
            val slot = index + 1
            val shortLabel = preset.title.ifBlank { context.getString(R.string.tile_preset_label, slot) }.take(10)
            val longLabel = "${preset.title} • ${formatDelay(context, preset.delaySeconds)}"
            val intent = Intent(context, ShortcutTriggerActivity::class.java).apply {
                action = ACTION_TRIGGER_PRESET
                putExtra(EXTRA_PRESET_SLOT, slot)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            ShortcutInfo.Builder(context, "$SHORTCUT_ID_PREFIX$slot")
                .setShortLabel(shortLabel)
                .setLongLabel(longLabel)
                .setIcon(Icon.createWithResource(context, R.drawable.ic_quick_trigger_phone))
                .setIntent(intent)
                .build()
        }
        shortcutManager.dynamicShortcuts = shortcuts
    }

    fun refreshQuickSettingsTiles(context: Context) {
        requestTileRefresh(context)
    }

    private fun execute(
        context: Context,
        request: TriggerScheduleRequest,
        presetSlot: Int?
    ): QuickTriggerExecution {
        val now = System.currentTimeMillis()
        val triggerAtMillis = now + request.delaySeconds * 1_000L
        val runtimeAudioUri = request.presetAudioUri.takeIf {
            request.usePresetAudioOverride && it.isNotBlank()
        }
        val runtimeAudioName = request.presetAudioName.takeIf { runtimeAudioUri != null }

        return if (request.delaySeconds == 0 || triggerAtMillis < now + 100L) {
            if (triggerImmediately(context, request, runtimeAudioUri, runtimeAudioName)) {
                saveActivePresetSlot(context, null)
                requestTileRefresh(context)
                QuickTriggerExecution.IMMEDIATE
            } else {
                QuickTriggerExecution.FAILED
            }
        } else {
            if (scheduleAlarm(context, request, triggerAtMillis, runtimeAudioUri, runtimeAudioName)) {
                saveActivePresetSlot(context, presetSlot)
                requestTileRefresh(context)
                QuickTriggerExecution.SCHEDULED
            } else {
                QuickTriggerExecution.FAILED
            }
        }
    }

    private fun scheduleAlarm(
        context: Context,
        request: TriggerScheduleRequest,
        triggerAtMillis: Long,
        runtimeAudioUri: String?,
        runtimeAudioName: String?
    ): Boolean {
        FakeCallAlarmScheduler.cancel(context)
        configureRuntimeAudioOverride(context, null, null)
        val scheduled = FakeCallAlarmScheduler.scheduleExact(
            context = context,
            triggerAtMillis = triggerAtMillis,
            callerName = request.callerName,
            callerNumber = request.callerNumber,
            providerName = request.providerName,
            runtimeAudioUri = runtimeAudioUri,
            runtimeAudioName = runtimeAudioName
        )

        if (scheduled) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_CALLER_NAME, request.callerName)
                .putString(KEY_CALLER_NUMBER, request.callerNumber)
                .putLong(KEY_TIMER_ENDS_AT, triggerAtMillis)
                .apply()
        }

        return scheduled
    }

    private fun triggerImmediately(
        context: Context,
        request: TriggerScheduleRequest,
        runtimeAudioUri: String?,
        runtimeAudioName: String?
    ): Boolean {
        FakeCallAlarmScheduler.cancel(context)
        configureRuntimeAudioOverride(context, runtimeAudioUri, runtimeAudioName)
        val telecomHelper = TelecomHelper(context)
        telecomHelper.registerOrUpdatePhoneAccount(request.providerName)
        val triggered = if (telecomHelper.isAccountEnabled()) {
            telecomHelper.triggerIncomingCall(request.callerName, request.callerNumber)
        } else {
            false
        }

        if (triggered) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_CALLER_NAME, request.callerName)
                .putString(KEY_CALLER_NUMBER, request.callerNumber)
                .remove(KEY_TIMER_ENDS_AT)
                .apply()
        } else {
            configureRuntimeAudioOverride(context, null, null)
        }

        return triggered
    }

    private fun savePresets(context: Context, presets: List<QuickTriggerPreset>) {
        val array = JSONArray()
        presets.take(MAX_PRESETS).forEach { preset ->
            array.put(
                JSONObject()
                    .put("id", preset.id)
                    .put("title", preset.title)
                    .put("callerName", preset.callerName)
                    .put("callerNumber", preset.callerNumber)
                    .put("delaySeconds", preset.delaySeconds)
                    .put("useCustomAudio", preset.useCustomAudio)
                    .put("customAudioUri", preset.customAudioUri)
                    .put("customAudioName", preset.customAudioName)
            )
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_QUICK_TRIGGER_PRESETS, array.toString())
            .apply()
    }

    private fun saveActivePresetSlot(context: Context, slot: Int?) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_ACTIVE_PRESET_SLOT, slot ?: -1)
            .apply()
    }

    private fun saveDefaultPresetSlot(context: Context, slot: Int?) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_DEFAULT_PRESET_SLOT, slot ?: -1)
            .apply()
    }

    private fun requestTileRefresh(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        val services = listOf(
            QuickTriggerTile1Service::class.java,
            QuickTriggerTile2Service::class.java,
            QuickTriggerTile3Service::class.java,
            QuickTriggerTile4Service::class.java,
            QuickTriggerTile5Service::class.java
        )
        services.forEach { service ->
            TileService.requestListeningState(context, ComponentName(context, service))
        }
    }

    private fun formatDelay(context: Context, seconds: Int): String {
        return DelayFormatter.formatShort(context, seconds)
    }

    private fun updatePreset(
        context: Context,
        slot: Int,
        updater: (QuickTriggerPreset) -> QuickTriggerPreset
    ): Boolean {
        val index = slot - 1
        val current = loadPresets(context).toMutableList()
        if (index !in current.indices) return false
        current[index] = updater(current[index])
        savePresets(context, current)
        updateLauncherShortcuts(context)
        requestTileRefresh(context)
        return true
    }

    private fun configureRuntimeAudioOverride(
        context: Context,
        runtimeAudioUri: String?,
        runtimeAudioName: String?
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            if (runtimeAudioUri.isNullOrBlank()) {
                putBoolean(KEY_RUNTIME_AUDIO_OVERRIDE_ENABLED, false)
                remove(KEY_RUNTIME_AUDIO_OVERRIDE_URI)
                remove(KEY_RUNTIME_AUDIO_OVERRIDE_NAME)
            } else {
                putBoolean(KEY_RUNTIME_AUDIO_OVERRIDE_ENABLED, true)
                putString(KEY_RUNTIME_AUDIO_OVERRIDE_URI, runtimeAudioUri)
                putString(KEY_RUNTIME_AUDIO_OVERRIDE_NAME, runtimeAudioName.orEmpty())
            }
        }.apply()
    }

    private fun loadDefaultPresetSlot(context: Context, presets: List<QuickTriggerPreset>): Int? {
        if (presets.isEmpty()) return null
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stored = prefs.getInt(KEY_DEFAULT_PRESET_SLOT, -1)
        val storedValid = stored in 1..presets.size
        return when {
            storedValid -> stored
            presets.size == 1 -> 1
            else -> null
        }
    }
}
