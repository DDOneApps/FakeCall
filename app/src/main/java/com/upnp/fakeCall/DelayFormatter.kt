package com.upnp.fakeCall

import android.content.Context
import android.icu.text.MeasureFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import java.util.Locale

object DelayFormatter {

    fun formatLong(context: Context, seconds: Int): String {
        return format(context, seconds, MeasureFormat.FormatWidth.WIDE)
    }

    fun formatShort(context: Context, seconds: Int): String {
        return format(context, seconds, MeasureFormat.FormatWidth.SHORT)
    }

    private fun format(
        context: Context,
        seconds: Int,
        width: MeasureFormat.FormatWidth
    ): String {
        val safeSeconds = seconds.coerceAtLeast(0)
        if (safeSeconds == 0) return context.getString(R.string.delay_now)

        val locale = context.resources.configuration.locales[0] ?: Locale.getDefault()
        val formatter = MeasureFormat.getInstance(locale, width)
        val minutes = safeSeconds / 60
        val remainingSeconds = safeSeconds % 60

        return when {
            safeSeconds < 60 -> formatter.format(Measure(safeSeconds.toLong(), MeasureUnit.SECOND))
            remainingSeconds == 0 -> formatter.format(Measure(minutes.toLong(), MeasureUnit.MINUTE))
            else -> formatter.formatMeasures(
                Measure(minutes.toLong(), MeasureUnit.MINUTE),
                Measure(remainingSeconds.toLong(), MeasureUnit.SECOND)
            )
        }
    }
}
