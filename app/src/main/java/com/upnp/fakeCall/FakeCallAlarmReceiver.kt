package com.upnp.fakeCall

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class FakeCallAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val pendingResult = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        scope.launch {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                WAKE_LOCK_TAG
            ).apply { acquire(WAKE_LOCK_TIMEOUT_MS) }

            try {
                val runtimeAudioUri = intent?.getStringExtra(EXTRA_RUNTIME_AUDIO_URI).orEmpty()
                val runtimeAudioName = intent?.getStringExtra(EXTRA_RUNTIME_AUDIO_NAME).orEmpty()
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .apply {
                        if (runtimeAudioUri.isBlank()) {
                            putBoolean(KEY_RUNTIME_AUDIO_OVERRIDE_ENABLED, false)
                            remove(KEY_RUNTIME_AUDIO_OVERRIDE_URI)
                            remove(KEY_RUNTIME_AUDIO_OVERRIDE_NAME)
                        } else {
                            putBoolean(KEY_RUNTIME_AUDIO_OVERRIDE_ENABLED, true)
                            putString(KEY_RUNTIME_AUDIO_OVERRIDE_URI, runtimeAudioUri)
                            putString(KEY_RUNTIME_AUDIO_OVERRIDE_NAME, runtimeAudioName)
                        }
                    }
                    .apply()
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .remove(KEY_TIMER_ENDS_AT)
                    .putInt(KEY_ACTIVE_PRESET_SLOT, -1)
                    .apply()
                QuickTriggerManager.refreshQuickSettingsTiles(context)
                val callerName = intent?.getStringExtra(EXTRA_CALLER_NAME).orEmpty()
                val callerNumber = intent?.getStringExtra(EXTRA_CALLER_NUMBER).orEmpty()
                val providerName = intent?.getStringExtra(EXTRA_PROVIDER_NAME).orEmpty()

                if (callerNumber.isBlank()) return@launch

                val telecomHelper = TelecomHelper(context)
                telecomHelper.registerOrUpdatePhoneAccount(providerName.ifBlank { context.getString(R.string.default_provider_name) })
                if (telecomHelper.isAccountEnabled()) {
                    telecomHelper.triggerIncomingCall(callerName, callerNumber)
                }
            } finally {
                if (wakeLock.isHeld) {
                    runCatching { wakeLock.release() }
                }
                pendingResult.finish()
                scope.cancel()
            }
        }
    }

    companion object {
        private const val PREFS_NAME = "fake_call_prefs"
        private const val KEY_TIMER_ENDS_AT = "timer_ends_at"
        private const val KEY_ACTIVE_PRESET_SLOT = "quick_trigger_active_preset_slot"
        private const val WAKE_LOCK_TAG = "fakecall:alarm-receiver"
        private const val WAKE_LOCK_TIMEOUT_MS = 30_000L
        const val EXTRA_CALLER_NAME = "extra_caller_name"
        const val EXTRA_CALLER_NUMBER = "extra_caller_number"
        const val EXTRA_PROVIDER_NAME = "extra_provider_name"
        const val EXTRA_RUNTIME_AUDIO_URI = "extra_runtime_audio_uri"
        const val EXTRA_RUNTIME_AUDIO_NAME = "extra_runtime_audio_name"
        private const val KEY_RUNTIME_AUDIO_OVERRIDE_ENABLED = "runtime_audio_override_enabled"
        private const val KEY_RUNTIME_AUDIO_OVERRIDE_URI = "runtime_audio_override_uri"
        private const val KEY_RUNTIME_AUDIO_OVERRIDE_NAME = "runtime_audio_override_name"
    }
}
