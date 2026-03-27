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

        return FakeConnection(
            context = this,
            callerName = name,
            callerNumber = number
        )
    }
}
