package com.upnp.fakeCall

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object AlarmModeRepository {
    private const val PREFS_NAME = "fake_call_prefs"
    private const val KEY_ALARM_MODE_ITEMS = "alarm_mode_items"

    fun load(context: Context): List<AlarmModeItem> {
        val raw = prefs(context).getString(KEY_ALARM_MODE_ITEMS, "").orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val obj = array.optJSONObject(index) ?: continue
                    parseItem(obj)?.let(::add)
                }
            }
        }.getOrDefault(emptyList())
    }

    fun find(context: Context, alarmId: Long): AlarmModeItem? {
        return load(context).firstOrNull { it.id == alarmId }
    }

    fun upsert(context: Context, item: AlarmModeItem): List<AlarmModeItem> {
        val existing = load(context)
        val updated = buildList {
            var replaced = false
            existing.forEach { current ->
                if (current.id == item.id) {
                    add(item)
                    replaced = true
                } else {
                    add(current)
                }
            }
            if (!replaced) add(item)
        }.sortedBy { it.hour * 60 + it.minute }
        save(context, updated)
        return updated
    }

    fun updateEnabled(context: Context, alarmId: Long, enabled: Boolean): List<AlarmModeItem> {
        val updated = load(context).map { item ->
            if (item.id == alarmId) item.copy(enabled = enabled) else item
        }
        save(context, updated)
        return updated
    }

    fun updateNextTrigger(context: Context, alarmId: Long, nextTriggerAtMillis: Long): List<AlarmModeItem> {
        val updated = load(context).map { item ->
            if (item.id == alarmId) item.copy(nextTriggerAtMillis = nextTriggerAtMillis) else item
        }
        save(context, updated)
        return updated
    }

    fun disable(context: Context, alarmId: Long): List<AlarmModeItem> {
        return updateEnabled(context, alarmId, false)
    }

    fun delete(context: Context, alarmId: Long): List<AlarmModeItem> {
        val updated = load(context).filterNot { it.id == alarmId }
        save(context, updated)
        return updated
    }

    fun replaceAll(context: Context, items: List<AlarmModeItem>) {
        save(context, items.sortedBy { it.hour * 60 + it.minute })
    }

    private fun save(context: Context, items: List<AlarmModeItem>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("callerName", item.callerName)
                    put("callerNumber", item.callerNumber)
                    put("hour", item.hour)
                    put("minute", item.minute)
                    put("repeatDays", JSONArray(item.repeatDays.sorted()))
                    put("messageMode", item.messageMode.name)
                    put("ttsMessage", item.ttsMessage)
                    put("repeatTtsMessage", item.repeatTtsMessage)
                    put("customAudioUri", item.customAudioUri)
                    put("customAudioName", item.customAudioName)
                    put("snoozeEnabled", item.snoozeEnabled)
                    put("snoozeMinutes", item.snoozeMinutes)
                    put("speakerDefault", item.speakerDefault.name)
                    put("enabled", item.enabled)
                    put("nextTriggerAtMillis", item.nextTriggerAtMillis)
                }
            )
        }
        prefs(context).edit().putString(KEY_ALARM_MODE_ITEMS, array.toString()).apply()
    }

    private fun parseItem(obj: JSONObject): AlarmModeItem? {
        val id = obj.optLong("id", 0L)
        val callerNumber = obj.optString("callerNumber").orEmpty().trim()
        if (id == 0L || callerNumber.isBlank()) return null
        val daysArray = obj.optJSONArray("repeatDays")
        val repeatDays = buildSet {
            for (index in 0 until (daysArray?.length() ?: 0)) {
                val day = daysArray?.optInt(index) ?: continue
                if (day in 1..7) add(day)
            }
        }
        val messageMode = runCatching {
            AlarmMessageMode.valueOf(obj.optString("messageMode", AlarmMessageMode.APP_VOICE_TTS.name))
        }.getOrDefault(AlarmMessageMode.APP_VOICE_TTS)
        val speakerDefault = runCatching {
            AlarmSpeakerDefault.valueOf(obj.optString("speakerDefault", AlarmSpeakerDefault.EARPIECE.name))
        }.getOrDefault(AlarmSpeakerDefault.EARPIECE)
        return AlarmModeItem(
            id = id,
            callerName = obj.optString("callerName").orEmpty(),
            callerNumber = callerNumber,
            hour = obj.optInt("hour", 8).coerceIn(0, 23),
            minute = obj.optInt("minute", 0).coerceIn(0, 59),
            repeatDays = repeatDays,
            messageMode = messageMode,
            ttsMessage = obj.optString("ttsMessage").orEmpty(),
            repeatTtsMessage = obj.optBoolean("repeatTtsMessage", false),
            customAudioUri = obj.optString("customAudioUri").orEmpty(),
            customAudioName = obj.optString("customAudioName").orEmpty(),
            snoozeEnabled = obj.optBoolean("snoozeEnabled", false),
            snoozeMinutes = obj.optInt("snoozeMinutes", 5).coerceIn(1, 30),
            speakerDefault = speakerDefault,
            enabled = obj.optBoolean("enabled", true),
            nextTriggerAtMillis = obj.optLong("nextTriggerAtMillis", 0L)
        )
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
