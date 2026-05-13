package com.upnp.fakeCall

enum class AlarmMessageMode {
    APP_VOICE_TTS,
    CUSTOM_AUDIO
}

enum class AlarmSpeakerDefault {
    EARPIECE,
    SPEAKER
}

data class AlarmModeItem(
    val id: Long,
    val callerName: String,
    val callerNumber: String,
    val hour: Int,
    val minute: Int,
    val repeatDays: Set<Int> = emptySet(),
    val messageMode: AlarmMessageMode = AlarmMessageMode.APP_VOICE_TTS,
    val ttsMessage: String = "",
    val customAudioUri: String = "",
    val customAudioName: String = "",
    val snoozeEnabled: Boolean = false,
    val snoozeMinutes: Int = 5,
    val speakerDefault: AlarmSpeakerDefault = AlarmSpeakerDefault.EARPIECE,
    val enabled: Boolean = true,
    val nextTriggerAtMillis: Long = 0L
)

data class AlarmModeDraft(
    val callerName: String,
    val callerNumber: String,
    val hour: Int,
    val minute: Int,
    val repeatDays: Set<Int>,
    val messageMode: AlarmMessageMode,
    val ttsMessage: String,
    val customAudioUri: String,
    val customAudioName: String,
    val snoozeEnabled: Boolean,
    val snoozeMinutes: Int,
    val speakerDefault: AlarmSpeakerDefault
)
