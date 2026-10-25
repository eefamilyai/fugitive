package app.fugitive.core.ui

import androidx.compose.runtime.staticCompositionLocalOf
import app.fugitive.engine.settings.SettingUnit
import app.fugitive.engine.settings.SettingFormat
import kotlin.math.roundToInt

enum class UnitSystem { METRIC, IMPERIAL }

/** Distance unit preference, provided at the app root. */
val LocalUnitSystem = staticCompositionLocalOf { UnitSystem.METRIC }

object Format {
    fun distance(meters: Double, units: UnitSystem): String = when (units) {
        UnitSystem.METRIC -> when {
            meters < 1000 -> "${meters.roundToInt()} m"
            meters < 10_000 -> "%.1f km".format(meters / 1000)
            else -> "${(meters / 1000).roundToInt()} km"
        }
        UnitSystem.IMPERIAL -> {
            val feet = meters * 3.28084
            if (feet < 1000) "${feet.roundToInt()} ft" else "%.1f mi".format(meters / 1609.344)
        }
    }

    fun speed(mps: Double, units: UnitSystem): String = when (units) {
        UnitSystem.METRIC -> "%.1f km/h".format(mps * 3.6)
        UnitSystem.IMPERIAL -> "%.1f mph".format(mps * 2.23694)
    }

    /** "now", "12s ago", "3m ago". */
    fun ago(ms: Long): String {
        val s = ms / 1000
        return when {
            s < 5 -> "now"
            s < 60 -> "${s}s ago"
            s < 3600 -> "${s / 60}m ago"
            else -> "${s / 3600}h ago"
        }
    }

    /** Coarse badge for ping markers: "now", "1m", "5m". */
    fun ageBadge(ms: Long): String {
        val m = ms / 60_000
        return if (m < 1) "now" else "${m}m"
    }

    fun duration(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
        return when {
            h > 0 -> "${h}h ${m}m"
            m > 0 -> "${m}m ${sec}s"
            else -> "${sec}s"
        }
    }

    /** Setting value text honouring the user's unit system for distances. */
    fun settingValue(value: Long, unit: SettingUnit, units: UnitSystem): String =
        if (unit == SettingUnit.METERS) distance(value.toDouble(), units) else SettingFormat.format(value, unit)
}
