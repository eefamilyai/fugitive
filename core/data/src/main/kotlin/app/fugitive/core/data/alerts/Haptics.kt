package app.fugitive.core.data.alerts

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Distinct vibration "words" so players can tell what happened without looking. */
enum class HapticPattern(val timings: LongArray, val amplitudes: IntArray) {
    TICK(longArrayOf(0, 18), intArrayOf(0, 120)),
    NOTICE(longArrayOf(0, 60), intArrayOf(0, 180)),
    /** Lub-dub: hunter nearby. */
    HEARTBEAT(longArrayOf(0, 70, 110, 110), intArrayOf(0, 255, 0, 160)),
    /** Rapid buzzing: outside the zone. */
    DANGER(longArrayOf(0, 120, 80, 120, 80, 120), intArrayOf(0, 255, 0, 255, 0, 255)),
    /** Long: caught / catch claim. */
    CAUGHT(longArrayOf(0, 500, 150, 300), intArrayOf(0, 255, 0, 200)),
    /** Triple short: zone moving / big event. */
    ALERT(longArrayOf(0, 90, 70, 90, 70, 90), intArrayOf(0, 220, 0, 220, 0, 220)),
    SUCCESS(longArrayOf(0, 40, 60, 120), intArrayOf(0, 160, 0, 255)),
}

class Haptics(context: Context) {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION") context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    @Volatile var enabled: Boolean = true

    fun play(pattern: HapticPattern) {
        val v = vibrator ?: return
        if (!enabled || !v.hasVibrator()) return
        runCatching {
            val amps = if (v.hasAmplitudeControl()) pattern.amplitudes else pattern.amplitudes.map { if (it > 0) VibrationEffect.DEFAULT_AMPLITUDE else 0 }.toIntArray()
            v.vibrate(VibrationEffect.createWaveform(pattern.timings, amps, -1))
        }
    }
}
