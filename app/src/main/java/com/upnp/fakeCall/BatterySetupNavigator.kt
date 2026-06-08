package com.upnp.fakeCall

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

enum class RomFamily {
    HYPER_OS_XIAOMI,
    OXYGEN_OS_ONEPLUS,
    COLOR_OS_OPPO_REALME,
    ONE_UI_SAMSUNG,
    GENERIC
}

object BatterySetupNavigator {

    fun detectRomFamily(): RomFamily {
        val manufacturer = Build.MANUFACTURER.orEmpty().lowercase()
        val display = Build.DISPLAY.orEmpty().lowercase()
        val fingerprint = Build.FINGERPRINT.orEmpty().lowercase()
        val summary = "$manufacturer $display $fingerprint"

        return when {
            manufacturer in setOf("xiaomi", "redmi", "poco") || summary.contains("hyperos") -> RomFamily.HYPER_OS_XIAOMI
            manufacturer == "oneplus" || summary.contains("oxygen") -> RomFamily.OXYGEN_OS_ONEPLUS
            manufacturer in setOf("oppo", "realme") || summary.contains("coloros") || summary.contains("realmeui") -> RomFamily.COLOR_OS_OPPO_REALME
            manufacturer == "samsung" || summary.contains("one ui") -> RomFamily.ONE_UI_SAMSUNG
            else -> RomFamily.GENERIC
        }
    }

    fun isBatteryOptimizationDisabled(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val powerManager = context.getSystemService(PowerManager::class.java) ?: return false
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun openSystemBatteryOptimization(context: Context): Boolean {
        val intents = mutableListOf<Intent>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!isBatteryOptimizationDisabled(context)) {
                intents += Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:${context.packageName}")
                )
            }
            intents += Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        }
        intents += Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}")
        )
        return launchFirstResolvable(context, intents)
    }

    fun openOemBackgroundSettings(context: Context): Boolean {
        val packageName = context.packageName
        val appLabel = context.applicationInfo.loadLabel(context.packageManager).toString()
        val intents = mutableListOf<Intent>()

        when (detectRomFamily()) {
            RomFamily.HYPER_OS_XIAOMI -> {
                intents += Intent().setComponent(
                    ComponentName(
                        "com.miui.securitycenter",
                        "com.miui.permcenter.autostart.AutoStartManagementActivity"
                    )
                )
                intents += Intent().setComponent(
                    ComponentName(
                        "com.miui.securitycenter",
                        "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"
                    )
                ).putExtra("package_name", packageName)
                    .putExtra("package_label", appLabel)
            }

            RomFamily.OXYGEN_OS_ONEPLUS -> {
                intents += Intent().setComponent(
                    ComponentName(
                        "com.oneplus.security",
                        "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"
                    )
                )
                intents += Intent("com.android.settings.action.BACKGROUND_OPTIMIZE")
                    .setPackage("com.oneplus.security")
            }

            RomFamily.COLOR_OS_OPPO_REALME -> {
                intents += Intent().setComponent(
                    ComponentName(
                        "com.coloros.safecenter",
                        "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                    )
                )
                intents += Intent().setComponent(
                    ComponentName(
                        "com.oppo.safe",
                        "com.oppo.safe.permission.startup.StartupAppListActivity"
                    )
                )
                intents += Intent().setComponent(
                    ComponentName(
                        "com.coloros.oppoguardelf",
                        "com.coloros.powermanager.fuelgaue.PowerUsageModelActivity"
                    )
                )
                intents += Intent().setComponent(
                    ComponentName(
                        "com.oplus.battery",
                        "com.oplus.powermanager.fuelgaue.PowerUsageModelActivity"
                    )
                )
            }

            RomFamily.ONE_UI_SAMSUNG -> {
                intents += Intent().setComponent(
                    ComponentName(
                        "com.samsung.android.lool",
                        "com.samsung.android.sm.ui.battery.BatteryActivity"
                    )
                )
                intents += Intent().setComponent(
                    ComponentName(
                        "com.samsung.android.lool",
                        "com.samsung.android.sm.battery.ui.usage.CheckableAppListActivity"
                    )
                )
            }

            RomFamily.GENERIC -> Unit
        }

        intents += Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}")
        )
        return launchFirstResolvable(context, intents)
    }

    private fun launchFirstResolvable(context: Context, intents: List<Intent>): Boolean {
        val packageManager = context.packageManager
        intents.forEach { intent ->
            val prepared = intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (prepared.resolveActivity(packageManager) != null) {
                runCatching { context.startActivity(prepared) }
                    .onSuccess { return true }
            }
        }
        return false
    }
}

