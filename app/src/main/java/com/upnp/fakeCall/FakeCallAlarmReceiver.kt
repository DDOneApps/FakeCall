package com.upnp.fakeCall

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class FakeCallAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val callerName = intent?.getStringExtra(EXTRA_CALLER_NAME).orEmpty()
        val callerNumber = intent?.getStringExtra(EXTRA_CALLER_NUMBER).orEmpty()
        val providerName = intent?.getStringExtra(EXTRA_PROVIDER_NAME).orEmpty()

        if (callerNumber.isBlank()) return

        val telecomHelper = TelecomHelper(context)
        telecomHelper.registerOrUpdatePhoneAccount(providerName.ifBlank { DEFAULT_PROVIDER_NAME })
        if (telecomHelper.isAccountEnabled()) {
            telecomHelper.triggerIncomingCall(callerName, callerNumber)
        }
    }

    companion object {
        const val EXTRA_CALLER_NAME = "extra_caller_name"
        const val EXTRA_CALLER_NUMBER = "extra_caller_number"
        const val EXTRA_PROVIDER_NAME = "extra_provider_name"

        private const val DEFAULT_PROVIDER_NAME = "Fake Call Provider"
    }
}
