package app.fugitive.model

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.round

/** Locale-independent number formatting that works on every Kotlin platform (JVM, JS). */
object Fmt {
    /** [value] with exactly [decimals] digits after the point, e.g. fixed(1.25, 1) = "1.3". */
    fun fixed(value: Double, decimals: Int): String {
        if (value.isNaN()) return "NaN"
        val factor = 10.0.pow(decimals)
        val scaled = round(abs(value) * factor).toLong()
        val intPart = scaled / factor.toLong()
        val frac = scaled % factor.toLong()
        val sign = if (value < 0 && scaled != 0L) "-" else ""
        return if (decimals == 0) "$sign$intPart" else "$sign$intPart.${frac.toString().padStart(decimals, '0')}"
    }

    fun pad2(v: Long): String = v.toString().padStart(2, '0')
}
