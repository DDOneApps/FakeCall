package com.upnp.fakeCall

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager

class TelecomHelper(private val context: Context) {

    private val telecomManager: TelecomManager =
        context.getSystemService(TelecomManager::class.java)

    fun accountHandle(): PhoneAccountHandle {
        return PhoneAccountHandle(
            ComponentName(context, FakeCallConnectionService::class.java),
            ACCOUNT_ID
        )
    }

    fun registerOrUpdatePhoneAccount(label: String): Boolean {
        return runCatching {
            val phoneAccount = PhoneAccount.Builder(accountHandle(), label)
                .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
                .addSupportedUriScheme(PhoneAccount.SCHEME_TEL)
                .build()

            telecomManager.registerPhoneAccount(phoneAccount)
            true
        }.getOrDefault(false)
    }

    fun isAccountEnabled(): Boolean {
        return runCatching {
            telecomManager.getPhoneAccount(accountHandle())?.isEnabled == true
        }.getOrDefault(false)
    }

    fun triggerIncomingCall(
        callerName: String,
        callerNumber: String,
        source: IncomingCallSource = IncomingCallSource.CALL
    ): Boolean {
        return runCatching {
            val normalizedNumber = callerNumber.trim()
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val timeoutSeconds = when (source) {
                IncomingCallSource.ALARM -> prefs.getInt(KEY_ALARM_RING_TIMEOUT_SECONDS, DEFAULT_ALARM_RING_TIMEOUT_SECONDS)
                IncomingCallSource.CALL -> prefs.getInt(KEY_CALL_RING_TIMEOUT_SECONDS, DEFAULT_CALL_RING_TIMEOUT_SECONDS)
            }.coerceAtLeast(0)
            val incomingExtras = Bundle().apply {
                putString(EXTRA_FAKE_CALLER_NAME, callerName.trim())
                putString(EXTRA_FAKE_CALLER_NUMBER, normalizedNumber)
                putString(EXTRA_FAKE_CALL_SOURCE, source.storageValue)
                putInt(EXTRA_RING_TIMEOUT_SECONDS, timeoutSeconds)
            }

            val extras = Bundle().apply {
                putParcelable(
                    TelecomManager.EXTRA_INCOMING_CALL_ADDRESS,
                    Uri.fromParts(PhoneAccount.SCHEME_TEL, normalizedNumber, null)
                )
                putBundle(TelecomManager.EXTRA_INCOMING_CALL_EXTRAS, incomingExtras)
            }

            telecomManager.addNewIncomingCall(accountHandle(), extras)
            true
        }.getOrDefault(false)
    }

    companion object {
        private const val PREFS_NAME = "fake_call_prefs"
        private const val KEY_CALL_RING_TIMEOUT_SECONDS = "call_ring_timeout_seconds"
        private const val KEY_ALARM_RING_TIMEOUT_SECONDS = "alarm_ring_timeout_seconds"
        private const val DEFAULT_CALL_RING_TIMEOUT_SECONDS = 45
        private const val DEFAULT_ALARM_RING_TIMEOUT_SECONDS = 0
        const val ACCOUNT_ID = "fake_call_provider_account"
        const val EXTRA_FAKE_CALLER_NAME = "extra_fake_caller_name"
        const val EXTRA_FAKE_CALLER_NUMBER = "extra_fake_caller_number"
        const val EXTRA_FAKE_CALL_SOURCE = "extra_fake_call_source"
        const val EXTRA_RING_TIMEOUT_SECONDS = "extra_ring_timeout_seconds"
    }
}

enum class IncomingCallSource(val storageValue: String) {
    CALL("call"),
    ALARM("alarm");

    companion object {
        fun fromStorage(value: String?): IncomingCallSource {
            return values().firstOrNull { it.storageValue == value } ?: CALL
        }
    }
}
