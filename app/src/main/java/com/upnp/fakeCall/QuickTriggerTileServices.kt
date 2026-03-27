package com.upnp.fakeCall

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import android.graphics.drawable.Icon

abstract class BaseQuickTriggerTileService : TileService() {
    abstract val presetSlot: Int

    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
    }

    override fun onClick() {
        super.onClick()
        if (isLocked) {
            unlockAndRun { executePreset() }
        } else {
            executePreset()
        }
    }

    private fun executePreset() {
        when (QuickTriggerManager.executePreset(this, presetSlot)) {
            QuickTriggerExecution.IMMEDIATE -> {
                Toast.makeText(this, getString(R.string.toast_triggering_now), Toast.LENGTH_SHORT).show()
            }
            QuickTriggerExecution.SCHEDULED -> {
                val preset = QuickTriggerManager.getPresetBySlot(this, presetSlot)
                val delay = preset?.delaySeconds ?: 0
                Toast.makeText(this, getString(R.string.toast_scheduled_in, delay), Toast.LENGTH_SHORT).show()
            }
            QuickTriggerExecution.FAILED -> {
                Toast.makeText(this, getString(R.string.toast_preset_not_configured, presetSlot), Toast.LENGTH_SHORT).show()
            }
        }
        refreshTile()
    }

    private fun refreshTile() {
        val tile = qsTile ?: return
        val preset = QuickTriggerManager.getPresetBySlot(this, presetSlot)
        tile.icon = Icon.createWithResource(this, R.drawable.ic_quick_trigger_phone)
        if (preset == null) {
            tile.state = Tile.STATE_UNAVAILABLE
            tile.label = getString(R.string.tile_preset_label, presetSlot)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = getString(R.string.tile_not_configured)
            }
        } else {
            val activeSlot = QuickTriggerManager.loadActivePresetSlot(this)
            tile.state = if (activeSlot == presetSlot) {
                Tile.STATE_ACTIVE
            } else {
                Tile.STATE_INACTIVE
            }
            tile.label = getString(R.string.tile_preset_label, presetSlot)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = preset.title
            }
        }
        tile.updateTile()
    }
}

class QuickTriggerTile1Service : BaseQuickTriggerTileService() {
    override val presetSlot: Int = 1
}

class QuickTriggerTile2Service : BaseQuickTriggerTileService() {
    override val presetSlot: Int = 2
}

class QuickTriggerTile3Service : BaseQuickTriggerTileService() {
    override val presetSlot: Int = 3
}

class QuickTriggerTile4Service : BaseQuickTriggerTileService() {
    override val presetSlot: Int = 4
}

class QuickTriggerTile5Service : BaseQuickTriggerTileService() {
    override val presetSlot: Int = 5
}
