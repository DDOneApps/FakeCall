package com.upnp.fakeCall

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmModeAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, 0L)
        if (alarmId == 0L) return

        val callerNumber = intent.getStringExtra(EXTRA_CALLER_NUMBER).orEmpty().trim()
        if (callerNumber.isBlank()) return
        val callerName = intent.getStringExtra(EXTRA_CALLER_NAME).orEmpty()
        val providerName = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_PROVIDER_NAME, context.getString(R.string.default_provider_name))
            .orEmpty()

        val messageMode = runCatching {
            AlarmMessageMode.valueOf(
                intent.getStringExtra(EXTRA_MESSAGE_MODE).orEmpty().ifBlank { AlarmMessageMode.APP_VOICE_TTS.name }
            )
        }.getOrDefault(AlarmMessageMode.APP_VOICE_TTS)
        val ttsMessage = intent.getStringExtra(EXTRA_TTS_MESSAGE).orEmpty()
        val repeatTtsMessage = intent.getBooleanExtra(EXTRA_REPEAT_TTS_MESSAGE, false)
        val customAudioUri = intent.getStringExtra(EXTRA_CUSTOM_AUDIO_URI).orEmpty()
        val customAudioName = intent.getStringExtra(EXTRA_CUSTOM_AUDIO_NAME).orEmpty()
        val snoozeEnabled = intent.getBooleanExtra(EXTRA_SNOOZE_ENABLED, false)
        val snoozeMinutes = intent.getIntExtra(EXTRA_SNOOZE_MINUTES, 5).coerceIn(1, 30)
        val speakerDefault = runCatching {
            AlarmSpeakerDefault.valueOf(
                intent.getStringExtra(EXTRA_SPEAKER_DEFAULT).orEmpty().ifBlank { AlarmSpeakerDefault.EARPIECE.name }
            )
        }.getOrDefault(AlarmSpeakerDefault.EARPIECE)

        applyRuntimeOverrides(
            context = context,
            messageMode = messageMode,
            ttsMessage = ttsMessage,
            repeatTtsMessage = repeatTtsMessage,
            customAudioUri = customAudioUri,
            customAudioName = customAudioName,
            speakerDefault = speakerDefault,
            snoozeEnabled = snoozeEnabled,
            snoozeMinutes = snoozeMinutes,
            alarmId = alarmId,
            callerName = callerName,
            callerNumber = callerNumber,
            providerName = providerName
        )

        val telecomHelper = TelecomHelper(context)
        telecomHelper.registerOrUpdatePhoneAccount(providerName.ifBlank { context.getString(R.string.default_provider_name) })
        if (telecomHelper.isAccountEnabled()) {
            telecomHelper.triggerIncomingCall(
                callerName = callerName,
                callerNumber = callerNumber,
                source = IncomingCallSource.ALARM
            )
        }

        val repeatDays = (intent.getIntArrayExtra(EXTRA_REPEAT_DAYS) ?: intArrayOf())
            .toSet()
            .filter { day -> day in 1..7 }
            .toSet()
        if (repeatDays.isEmpty()) {
            AlarmModeRepository.disable(context, alarmId)
            AlarmModeRepository.updateNextTrigger(context, alarmId, 0L)
            AlarmModeScheduler.cancel(context, alarmId)
            return
        }

        val alarm = AlarmModeRepository.find(context, alarmId)?.copy(
            callerName = callerName,
            callerNumber = callerNumber,
            hour = intent.getIntExtra(EXTRA_HOUR, 8).coerceIn(0, 23),
            minute = intent.getIntExtra(EXTRA_MINUTE, 0).coerceIn(0, 59),
            repeatDays = repeatDays,
            messageMode = messageMode,
            ttsMessage = ttsMessage,
            repeatTtsMessage = repeatTtsMessage,
            customAudioUri = customAudioUri,
            customAudioName = customAudioName,
            snoozeEnabled = snoozeEnabled,
            snoozeMinutes = snoozeMinutes,
            speakerDefault = speakerDefault,
            enabled = true
        )
        if (alarm != null) {
            val next = AlarmModeScheduler.schedule(context, alarm)
            AlarmModeRepository.upsert(context, alarm.copy(nextTriggerAtMillis = next))
        }
    }

    private fun applyRuntimeOverrides(
        context: Context,
        messageMode: AlarmMessageMode,
        ttsMessage: String,
        repeatTtsMessage: Boolean,
        customAudioUri: String,
        customAudioName: String,
        speakerDefault: AlarmSpeakerDefault,
        snoozeEnabled: Boolean,
        snoozeMinutes: Int,
        alarmId: Long,
        callerName: String,
        callerNumber: String,
        providerName: String
    ) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .apply {
                when (messageMode) {
                    AlarmMessageMode.CUSTOM_AUDIO -> {
                        if (customAudioUri.isNotBlank()) {
                            putBoolean(KEY_RUNTIME_AUDIO_OVERRIDE_ENABLED, true)
                            putString(KEY_RUNTIME_AUDIO_OVERRIDE_URI, customAudioUri)
                            putString(KEY_RUNTIME_AUDIO_OVERRIDE_NAME, customAudioName)
                        } else {
                            putBoolean(KEY_RUNTIME_AUDIO_OVERRIDE_ENABLED, false)
                            remove(KEY_RUNTIME_AUDIO_OVERRIDE_URI)
                            remove(KEY_RUNTIME_AUDIO_OVERRIDE_NAME)
                        }
                        putString(KEY_RUNTIME_MESSAGE_MODE, RUNTIME_MESSAGE_MODE_CUSTOM)
                        remove(KEY_RUNTIME_TTS_MESSAGE)
                        remove(KEY_RUNTIME_REPEAT_TTS_MESSAGE)
                    }
                    AlarmMessageMode.APP_VOICE_TTS -> {
                        putBoolean(KEY_RUNTIME_AUDIO_OVERRIDE_ENABLED, false)
                        remove(KEY_RUNTIME_AUDIO_OVERRIDE_URI)
                        remove(KEY_RUNTIME_AUDIO_OVERRIDE_NAME)
                        putString(KEY_RUNTIME_MESSAGE_MODE, RUNTIME_MESSAGE_MODE_TTS)
                        putBoolean(KEY_RUNTIME_REPEAT_TTS_MESSAGE, repeatTtsMessage)
                        putString(
                            KEY_RUNTIME_TTS_MESSAGE,
                            ttsMessage.ifBlank {
                                if (callerName.isNotBlank()) {
                                    context.getString(R.string.alarm_tts_default_message_with_name, callerName)
                                } else {
                                    context.getString(R.string.alarm_tts_default_message)
                                }
                            }
                        )
                    }
                }
                putString(KEY_RUNTIME_SPEAKER_DEFAULT, speakerDefault.name)
                putBoolean(KEY_RUNTIME_SNOOZE_ENABLED, snoozeEnabled)
                putInt(KEY_RUNTIME_SNOOZE_MINUTES, snoozeMinutes)
                putLong(KEY_RUNTIME_SNOOZE_ALARM_ID, alarmId)
                putString(KEY_RUNTIME_SNOOZE_CALLER_NAME, callerName)
                putString(KEY_RUNTIME_SNOOZE_CALLER_NUMBER, callerNumber)
                putString(KEY_RUNTIME_SNOOZE_PROVIDER_NAME, providerName)
            }
            .apply()
    }

    companion object {
        const val EXTRA_ALARM_ID = "extra_alarm_id"
        const val EXTRA_CALLER_NAME = "extra_alarm_caller_name"
        const val EXTRA_CALLER_NUMBER = "extra_alarm_caller_number"
        const val EXTRA_HOUR = "extra_alarm_hour"
        const val EXTRA_MINUTE = "extra_alarm_minute"
        const val EXTRA_REPEAT_DAYS = "extra_alarm_repeat_days"
        const val EXTRA_MESSAGE_MODE = "extra_alarm_message_mode"
        const val EXTRA_TTS_MESSAGE = "extra_alarm_tts_message"
        const val EXTRA_REPEAT_TTS_MESSAGE = "extra_alarm_repeat_tts_message"
        const val EXTRA_CUSTOM_AUDIO_URI = "extra_alarm_custom_audio_uri"
        const val EXTRA_CUSTOM_AUDIO_NAME = "extra_alarm_custom_audio_name"
        const val EXTRA_SNOOZE_ENABLED = "extra_alarm_snooze_enabled"
        const val EXTRA_SNOOZE_MINUTES = "extra_alarm_snooze_minutes"
        const val EXTRA_SPEAKER_DEFAULT = "extra_alarm_speaker_default"

        private const val PREFS_NAME = "fake_call_prefs"
        private const val KEY_PROVIDER_NAME = "provider_name"
        private const val KEY_RUNTIME_AUDIO_OVERRIDE_ENABLED = "runtime_audio_override_enabled"
        private const val KEY_RUNTIME_AUDIO_OVERRIDE_URI = "runtime_audio_override_uri"
        private const val KEY_RUNTIME_AUDIO_OVERRIDE_NAME = "runtime_audio_override_name"
        private const val KEY_RUNTIME_MESSAGE_MODE = "runtime_message_mode"
        private const val KEY_RUNTIME_TTS_MESSAGE = "runtime_tts_message"
        private const val KEY_RUNTIME_REPEAT_TTS_MESSAGE = "runtime_repeat_tts_message"
        private const val KEY_RUNTIME_SPEAKER_DEFAULT = "runtime_speaker_default"
        private const val KEY_RUNTIME_SNOOZE_ENABLED = "runtime_snooze_enabled"
        private const val KEY_RUNTIME_SNOOZE_MINUTES = "runtime_snooze_minutes"
        private const val KEY_RUNTIME_SNOOZE_ALARM_ID = "runtime_snooze_alarm_id"
        private const val KEY_RUNTIME_SNOOZE_CALLER_NAME = "runtime_snooze_caller_name"
        private const val KEY_RUNTIME_SNOOZE_CALLER_NUMBER = "runtime_snooze_caller_number"
        private const val KEY_RUNTIME_SNOOZE_PROVIDER_NAME = "runtime_snooze_provider_name"
        private const val RUNTIME_MESSAGE_MODE_CUSTOM = "custom_audio"
        private const val RUNTIME_MESSAGE_MODE_TTS = "tts"
    }
}
