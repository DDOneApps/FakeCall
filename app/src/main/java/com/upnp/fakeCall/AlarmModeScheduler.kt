package com.upnp.fakeCall

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.DayOfWeek
import java.time.ZonedDateTime

object AlarmModeScheduler {
    fun canScheduleExact(context: Context): Boolean {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    fun schedule(context: Context, alarm: AlarmModeItem): Long {
        val triggerAtMillis = computeNextTriggerAtMillis(alarm)
        if (triggerAtMillis <= 0L) return 0L
        if (!scheduleAt(context, alarm, triggerAtMillis)) return 0L
        return triggerAtMillis
    }

    fun scheduleSnooze(
        context: Context,
        alarm: AlarmModeItem,
        triggerAtMillis: Long
    ): Boolean {
        return scheduleAt(context, alarm, triggerAtMillis)
    }

    fun cancel(context: Context, alarmId: Long) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCodeFor(alarmId),
            Intent(context, AlarmModeAlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    fun computeNextTriggerAtMillis(
        alarm: AlarmModeItem,
        now: ZonedDateTime = ZonedDateTime.now()
    ): Long {
        val hour = alarm.hour.coerceIn(0, 23)
        val minute = alarm.minute.coerceIn(0, 59)
        val hasRepeat = alarm.repeatDays.isNotEmpty()
        var candidate = now
            .withHour(hour)
            .withMinute(minute)
            .withSecond(0)
            .withNano(0)

        if (!hasRepeat) {
            if (!candidate.isAfter(now)) {
                candidate = candidate.plusDays(1)
            }
            return candidate.toInstant().toEpochMilli()
        }

        if (!candidate.isAfter(now)) {
            candidate = candidate.plusDays(1)
        }
        repeat(8) {
            val day = candidate.dayOfWeek.value
            if (alarm.repeatDays.contains(day)) {
                return candidate.toInstant().toEpochMilli()
            }
            candidate = candidate.plusDays(1)
        }
        return 0L
    }

    fun dayLabel(context: Context, day: Int): String {
        return when (day) {
            DayOfWeek.MONDAY.value -> context.getString(R.string.weekday_mon)
            DayOfWeek.TUESDAY.value -> context.getString(R.string.weekday_tue)
            DayOfWeek.WEDNESDAY.value -> context.getString(R.string.weekday_wed)
            DayOfWeek.THURSDAY.value -> context.getString(R.string.weekday_thu)
            DayOfWeek.FRIDAY.value -> context.getString(R.string.weekday_fri)
            DayOfWeek.SATURDAY.value -> context.getString(R.string.weekday_sat)
            DayOfWeek.SUNDAY.value -> context.getString(R.string.weekday_sun)
            else -> ""
        }
    }

    private fun scheduleAt(
        context: Context,
        alarm: AlarmModeItem,
        triggerAtMillis: Long
    ): Boolean {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            return false
        }
        val intent = Intent(context, AlarmModeAlarmReceiver::class.java).apply {
            putExtra(AlarmModeAlarmReceiver.EXTRA_ALARM_ID, alarm.id)
            putExtra(AlarmModeAlarmReceiver.EXTRA_CALLER_NAME, alarm.callerName)
            putExtra(AlarmModeAlarmReceiver.EXTRA_CALLER_NUMBER, alarm.callerNumber)
            putExtra(AlarmModeAlarmReceiver.EXTRA_HOUR, alarm.hour)
            putExtra(AlarmModeAlarmReceiver.EXTRA_MINUTE, alarm.minute)
            putExtra(AlarmModeAlarmReceiver.EXTRA_REPEAT_DAYS, alarm.repeatDays.toIntArray())
            putExtra(AlarmModeAlarmReceiver.EXTRA_MESSAGE_MODE, alarm.messageMode.name)
            putExtra(AlarmModeAlarmReceiver.EXTRA_TTS_MESSAGE, alarm.ttsMessage)
            putExtra(AlarmModeAlarmReceiver.EXTRA_REPEAT_TTS_MESSAGE, alarm.repeatTtsMessage)
            putExtra(AlarmModeAlarmReceiver.EXTRA_CUSTOM_AUDIO_URI, alarm.customAudioUri)
            putExtra(AlarmModeAlarmReceiver.EXTRA_CUSTOM_AUDIO_NAME, alarm.customAudioName)
            putExtra(AlarmModeAlarmReceiver.EXTRA_SNOOZE_ENABLED, alarm.snoozeEnabled)
            putExtra(AlarmModeAlarmReceiver.EXTRA_SNOOZE_MINUTES, alarm.snoozeMinutes)
            putExtra(AlarmModeAlarmReceiver.EXTRA_SPEAKER_DEFAULT, alarm.speakerDefault.name)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCodeFor(alarm.id),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
        return true
    }

    private fun requestCodeFor(alarmId: Long): Int {
        val positive = if (alarmId < 0) -alarmId else alarmId
        val bounded = positive % 1_000_000_000L
        return (40_000 + bounded).toInt()
    }
}
