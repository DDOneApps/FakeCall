package com.upnp.fakeCall

import android.content.Context

data class QuickTriggerDefaults(
    val callerName: String = "",
    val callerNumber: String = "",
    val delaySeconds: Int = 10
)

data class TriggerScheduleRequest(
    val callerName: String,
    val callerNumber: String,
    val delaySeconds: Int,
    val providerName: String
)

enum class QuickTriggerExecution {
    IMMEDIATE,
    SCHEDULED,
    FAILED
}

object QuickTriggerManager {
    private const val PREFS_NAME = "fake_call_prefs"
    private const val KEY_PROVIDER_NAME = "provider_name"
    private const val KEY_CALLER_NAME = "caller_name"
    private const val KEY_CALLER_NUMBER = "caller_number"
    private const val KEY_DELAY_SECONDS = "delay_seconds"
    private const val KEY_TIMER_ENDS_AT = "timer_ends_at"
    private const val KEY_QUICK_TRIGGER_CALLER_NAME = "quick_trigger_caller_name"
    private const val KEY_QUICK_TRIGGER_CALLER_NUMBER = "quick_trigger_caller_number"
    private const val KEY_QUICK_TRIGGER_DELAY_SECONDS = "quick_trigger_delay_seconds"
    private const val DEFAULT_PROVIDER_NAME = "Fake Call Provider"
    const val DEFAULT_DELAY_SECONDS = 10

    fun loadDefaults(context: Context): QuickTriggerDefaults {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val callerName = prefs.getString(KEY_QUICK_TRIGGER_CALLER_NAME, null)
            ?: prefs.getString(KEY_CALLER_NAME, "").orEmpty()
        val callerNumber = prefs.getString(KEY_QUICK_TRIGGER_CALLER_NUMBER, null)
            ?: prefs.getString(KEY_CALLER_NUMBER, "").orEmpty()
        val delaySeconds = prefs.getInt(
            KEY_QUICK_TRIGGER_DELAY_SECONDS,
            prefs.getInt(KEY_DELAY_SECONDS, DEFAULT_DELAY_SECONDS)
        ).coerceAtLeast(0)
        return QuickTriggerDefaults(
            callerName = callerName,
            callerNumber = callerNumber,
            delaySeconds = delaySeconds
        )
    }

    fun saveDefaults(context: Context, defaults: QuickTriggerDefaults) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_QUICK_TRIGGER_CALLER_NAME, defaults.callerName)
            .putString(KEY_QUICK_TRIGGER_CALLER_NUMBER, defaults.callerNumber)
            .putInt(KEY_QUICK_TRIGGER_DELAY_SECONDS, defaults.delaySeconds.coerceAtLeast(0))
            .apply()
    }

    fun executeFromInputs(
        context: Context,
        callerName: String?,
        callerNumber: String?,
        delaySeconds: Int?
    ): QuickTriggerExecution {
        val request = resolveRequest(context, callerName, callerNumber, delaySeconds)
            ?: return QuickTriggerExecution.FAILED
        return execute(context, request)
    }

    fun executeFromDefaults(context: Context): QuickTriggerExecution {
        val defaults = loadDefaults(context)
        return executeFromInputs(
            context = context,
            callerName = defaults.callerName,
            callerNumber = defaults.callerNumber,
            delaySeconds = defaults.delaySeconds
        )
    }

    fun resolveRequest(
        context: Context,
        callerName: String?,
        callerNumber: String?,
        delaySeconds: Int?
    ): TriggerScheduleRequest? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val defaults = loadDefaults(context)
        val resolvedName = callerName?.takeIf { it.isNotBlank() } ?: defaults.callerName
        val resolvedNumber = callerNumber?.takeIf { it.isNotBlank() } ?: defaults.callerNumber
        val resolvedDelay = delaySeconds ?: defaults.delaySeconds
        val providerName = prefs.getString(KEY_PROVIDER_NAME, DEFAULT_PROVIDER_NAME).orEmpty()
            .ifBlank { DEFAULT_PROVIDER_NAME }

        if (resolvedNumber.isBlank()) return null

        return TriggerScheduleRequest(
            callerName = resolvedName,
            callerNumber = resolvedNumber,
            delaySeconds = resolvedDelay.coerceAtLeast(0),
            providerName = providerName
        )
    }

    private fun execute(context: Context, request: TriggerScheduleRequest): QuickTriggerExecution {
        val now = System.currentTimeMillis()
        val triggerAtMillis = now + request.delaySeconds * 1_000L

        return if (request.delaySeconds == 0 || triggerAtMillis < now + 100L) {
            if (triggerImmediately(context, request)) {
                QuickTriggerExecution.IMMEDIATE
            } else {
                QuickTriggerExecution.FAILED
            }
        } else {
            if (scheduleAlarm(context, request, triggerAtMillis)) {
                QuickTriggerExecution.SCHEDULED
            } else {
                QuickTriggerExecution.FAILED
            }
        }
    }

    private fun scheduleAlarm(
        context: Context,
        request: TriggerScheduleRequest,
        triggerAtMillis: Long
    ): Boolean {
        FakeCallAlarmScheduler.cancel(context)
        val scheduled = FakeCallAlarmScheduler.scheduleExact(
            context = context,
            triggerAtMillis = triggerAtMillis,
            callerName = request.callerName,
            callerNumber = request.callerNumber,
            providerName = request.providerName
        )

        if (scheduled) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_CALLER_NAME, request.callerName)
                .putString(KEY_CALLER_NUMBER, request.callerNumber)
                .putLong(KEY_TIMER_ENDS_AT, triggerAtMillis)
                .apply()
        }

        return scheduled
    }

    private fun triggerImmediately(context: Context, request: TriggerScheduleRequest): Boolean {
        FakeCallAlarmScheduler.cancel(context)
        val telecomHelper = TelecomHelper(context)
        telecomHelper.registerOrUpdatePhoneAccount(request.providerName)
        val triggered = if (telecomHelper.isAccountEnabled()) {
            telecomHelper.triggerIncomingCall(request.callerName, request.callerNumber)
        } else {
            false
        }

        if (triggered) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_CALLER_NAME, request.callerName)
                .putString(KEY_CALLER_NUMBER, request.callerNumber)
                .remove(KEY_TIMER_ENDS_AT)
                .apply()
        }

        return triggered
    }
}
