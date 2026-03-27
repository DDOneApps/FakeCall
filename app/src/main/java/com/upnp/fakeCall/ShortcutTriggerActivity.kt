package com.upnp.fakeCall

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

class ShortcutTriggerActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runShortcutAction(intent)
        finishWithoutAnimation()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        runShortcutAction(intent)
        finishWithoutAnimation()
    }

    private fun runShortcutAction(intent: Intent?) {
        if (intent?.action != QuickTriggerManager.ACTION_TRIGGER_PRESET) return
        val slot = intent.getIntExtra(QuickTriggerManager.EXTRA_PRESET_SLOT, -1)
        if (slot <= 0) return

        when (QuickTriggerManager.executePreset(this, slot)) {
            QuickTriggerExecution.IMMEDIATE -> {
                Toast.makeText(this, getString(R.string.toast_triggering_now), Toast.LENGTH_SHORT).show()
            }
            QuickTriggerExecution.SCHEDULED -> {
                val preset = QuickTriggerManager.getPresetBySlot(this, slot)
                val delay = preset?.delaySeconds ?: 0
                Toast.makeText(this, getString(R.string.toast_scheduled_in, delay), Toast.LENGTH_SHORT).show()
            }
            QuickTriggerExecution.FAILED -> {
                Toast.makeText(this, getString(R.string.toast_preset_run_failed, slot), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun finishWithoutAnimation() {
        finish()
        overridePendingTransition(0, 0)
    }
}
