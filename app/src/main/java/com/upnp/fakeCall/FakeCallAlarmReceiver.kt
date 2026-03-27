package com.upnp.fakeCall

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class FakeCallAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_TIMER_ENDS_AT)
            .putInt(KEY_ACTIVE_PRESET_SLOT, -1)
            .apply()
        QuickTriggerManager.refreshQuickSettingsTiles(context)
        val callerName = intent?.getStringExtra(EXTRA_CALLER_NAME).orEmpty()
        val callerNumber = intent?.getStringExtra(EXTRA_CALLER_NUMBER).orEmpty()
        val providerName = intent?.getStringExtra(EXTRA_PROVIDER_NAME).orEmpty()

        if (callerNumber.isBlank()) return

        val telecomHelper = TelecomHelper(context)
        telecomHelper.registerOrUpdatePhoneAccount(providerName.ifBlank { context.getString(R.string.default_provider_name) })
        if (telecomHelper.isAccountEnabled()) {
            telecomHelper.triggerIncomingCall(callerName, callerNumber)
        }
    }

    companion object {
        private const val PREFS_NAME = "fake_call_prefs"
        private const val KEY_TIMER_ENDS_AT = "timer_ends_at"
        private const val KEY_ACTIVE_PRESET_SLOT = "quick_trigger_active_preset_slot"
        const val EXTRA_CALLER_NAME = "extra_caller_name"
        const val EXTRA_CALLER_NUMBER = "extra_caller_number"
        const val EXTRA_PROVIDER_NAME = "extra_provider_name"
    }
}
