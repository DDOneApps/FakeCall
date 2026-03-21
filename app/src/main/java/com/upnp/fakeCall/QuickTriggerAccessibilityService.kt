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
            QuickTriggerExecution.IMMEDIATE -> "Triggering Fake Call now..."
            QuickTriggerExecution.SCHEDULED -> "Fake Call scheduled in ${defaults.delaySeconds} seconds"
            QuickTriggerExecution.FAILED -> "Fake Call couldn't be scheduled"
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
