package com.upnp.fakeCall

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class ExternalTriggerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_TRIGGER) return

        val callerName = intent?.getStringExtra(EXTRA_CALLER_NAME)
        val callerNumber = intent?.getStringExtra(EXTRA_CALLER_NUMBER)
        val delaySeconds = intent?.let {
            if (it.hasExtra(EXTRA_DELAY_SECONDS)) it.getIntExtra(EXTRA_DELAY_SECONDS, 0) else null
        }

        val result = QuickTriggerManager.executeFromInputs(
            context = context,
            callerName = callerName,
            callerNumber = callerNumber,
            delaySeconds = delaySeconds
        )

        if (result == QuickTriggerExecution.FAILED) {
            Toast.makeText(context, "Fake Call couldn't be scheduled", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        const val ACTION_TRIGGER = "com.upnp.fakeCall.TRIGGER"
        const val EXTRA_CALLER_NAME = "caller_name"
        const val EXTRA_CALLER_NUMBER = "caller_number"
        const val EXTRA_DELAY_SECONDS = "delay"
    }
}
