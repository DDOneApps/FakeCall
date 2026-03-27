package com.upnp.fakeCall

import android.accessibilityservice.AccessibilityButtonController
import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast

class QuickTriggerAccessibilityService : AccessibilityService() {

    private val buttonCallback = object : AccessibilityButtonController.AccessibilityButtonCallback() {
        override fun onClicked(controller: AccessibilityButtonController) {
            scheduleQuickTrigger()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            accessibilityButtonController.registerAccessibilityButtonCallback(buttonCallback)
        }
    }

    override fun onDestroy() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            accessibilityButtonController.unregisterAccessibilityButtonCallback(buttonCallback)
        }
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Intentionally empty: this service is only a shortcut entry point.
    }

    override fun onInterrupt() = Unit

    private fun scheduleQuickTrigger() {
        val defaults = QuickTriggerManager.loadDefaults(this)
        val result = QuickTriggerManager.executeFromInputs(
            context = this,
            callerName = defaults.callerName,
            callerNumber = defaults.callerNumber,
            delaySeconds = defaults.delaySeconds
        )
        val message = when (result) {
            QuickTriggerExecution.IMMEDIATE -> getString(R.string.toast_triggering_now)
            QuickTriggerExecution.SCHEDULED -> getString(R.string.toast_scheduled_in, defaults.delaySeconds)
            QuickTriggerExecution.FAILED -> getString(R.string.toast_call_scheduling_failed)
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
