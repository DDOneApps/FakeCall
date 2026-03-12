package com.upnp.fakeCall

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

object FakeCallAlarmScheduler {

    fun canScheduleExact(context: Context): Boolean {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    fun scheduleExact(
        context: Context,
        triggerAtMillis: Long,
        callerName: String,
        callerNumber: String,
        providerName: String
    ): Boolean {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            return false
        }

        val intent = Intent(context, FakeCallAlarmReceiver::class.java).apply {
            putExtra(FakeCallAlarmReceiver.EXTRA_CALLER_NAME, callerName)
            putExtra(FakeCallAlarmReceiver.EXTRA_CALLER_NUMBER, callerNumber)
            putExtra(FakeCallAlarmReceiver.EXTRA_PROVIDER_NAME, providerName)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
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

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val intent = Intent(context, FakeCallAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private const val REQUEST_CODE = 2001
}
