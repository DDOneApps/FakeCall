package com.upnp.fakeCall

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class FakeCallSchedulerService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var scheduleJob: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL_CALL -> {
                scheduleJob?.cancel()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
                return START_NOT_STICKY
            }

            ACTION_SCHEDULE_CALL -> {
                val delaySeconds = intent.getIntExtra(EXTRA_DELAY_SECONDS, 10)
                val callerName = intent.getStringExtra(EXTRA_CALLER_NAME).orEmpty()
                val callerNumber = intent.getStringExtra(EXTRA_CALLER_NUMBER).orEmpty()
                val providerName = intent.getStringExtra(EXTRA_PROVIDER_NAME).orEmpty()

                val notification = buildNotification(delaySeconds, callerName, callerNumber)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }

                scheduleJob?.cancel()
                scheduleJob = serviceScope.launch {
                    val telecomHelper = TelecomHelper(applicationContext)
                    telecomHelper.registerOrUpdatePhoneAccount(providerName.ifBlank { getString(R.string.default_provider_name) })
                    delay(delaySeconds * 1_000L)
                    if (telecomHelper.isAccountEnabled()) {
                        telecomHelper.triggerIncomingCall(callerName, callerNumber)
                    }
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf(startId)
                }

                return START_NOT_STICKY
            }

            else -> {
                stopSelf(startId)
                return START_NOT_STICKY
            }
        }
    }

    override fun onDestroy() {
        scheduleJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(delaySeconds: Int, callerName: String, callerNumber: String): Notification {
        createNotificationChannel()

        val contentTitle = if (callerName.isBlank()) {
            getString(R.string.notification_scheduled_title_no_name)
        } else {
            getString(R.string.notification_scheduled_title_with_name, callerName)
        }
        val contentText = getString(
            R.string.notification_scheduled_text,
            delaySeconds,
            callerNumber.ifBlank { getString(R.string.notification_unknown_caller) }
        )

        val launchIntent = Intent(this, MainActivity::class.java)
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_phone_call)
            .setContentTitle(contentTitle)
            .setContentText(contentText)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_scheduler),
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "fake_call_scheduler"
        private const val NOTIFICATION_ID = 101

        private const val ACTION_SCHEDULE_CALL = "com.upnp.fakeCall.action.SCHEDULE"
        private const val ACTION_CANCEL_CALL = "com.upnp.fakeCall.action.CANCEL"
        private const val EXTRA_DELAY_SECONDS = "extra_delay_seconds"
        private const val EXTRA_CALLER_NAME = "extra_caller_name"
        private const val EXTRA_CALLER_NUMBER = "extra_caller_number"
        private const val EXTRA_PROVIDER_NAME = "extra_provider_name"

        fun start(
            context: Context,
            delaySeconds: Int,
            callerName: String,
            callerNumber: String,
            providerName: String
        ) {
            val intent = Intent(context, FakeCallSchedulerService::class.java).apply {
                action = ACTION_SCHEDULE_CALL
                putExtra(EXTRA_DELAY_SECONDS, delaySeconds)
                putExtra(EXTRA_CALLER_NAME, callerName)
                putExtra(EXTRA_CALLER_NUMBER, callerNumber)
                putExtra(EXTRA_PROVIDER_NAME, providerName)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun cancel(context: Context) {
            val intent = Intent(context, FakeCallSchedulerService::class.java).apply {
                action = ACTION_CANCEL_CALL
            }
            context.startService(intent)
        }
    }
}
