package com.upnp.fakeCall

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.upnp.fakeCall.ui.FakeCallApp
import com.upnp.fakeCall.ui.theme.FakecallTheme
import android.graphics.Color

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        val startInSettings = shouldOpenSettings(intent)
        setContent {
            FakecallTheme {
                FakeCallApp(startInSettings = startInSettings)
            }
        }
    }

    private fun shouldOpenSettings(intent: Intent?): Boolean {
        val action = intent?.action.orEmpty()
        return action == ACTION_QS_TILE_PREFERENCES || action == ACTION_OPEN_SETTINGS
    }

    companion object {
        const val ACTION_OPEN_SETTINGS = "com.upnp.fakeCall.action.OPEN_SETTINGS"
        private const val ACTION_QS_TILE_PREFERENCES = "android.service.quicksettings.action.QS_TILE_PREFERENCES"
    }
}
