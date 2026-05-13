package com.upnp.fakeCall

import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager

class FakeCallConnectionService : ConnectionService() {

    override fun onCreateIncomingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle,
        request: ConnectionRequest
    ): Connection {
        val extras = request.extras?.getBundle(TelecomManager.EXTRA_INCOMING_CALL_EXTRAS)
        val number = request.address?.schemeSpecificPart
            ?: extras?.getString(TelecomHelper.EXTRA_FAKE_CALLER_NUMBER)
            ?: getString(R.string.notification_unknown_caller)
        val name = extras?.getString(TelecomHelper.EXTRA_FAKE_CALLER_NAME).orEmpty()
        val source = IncomingCallSource.fromStorage(
            extras?.getString(TelecomHelper.EXTRA_FAKE_CALL_SOURCE)
        )
        val ringTimeoutSeconds = extras?.getInt(TelecomHelper.EXTRA_RING_TIMEOUT_SECONDS, if (source == IncomingCallSource.ALARM) 60 else 45)
            ?: if (source == IncomingCallSource.ALARM) 60 else 45

        return FakeConnection(
            context = this,
            callerName = name,
            callerNumber = number,
            ringTimeoutSeconds = ringTimeoutSeconds
        )
    }
}
