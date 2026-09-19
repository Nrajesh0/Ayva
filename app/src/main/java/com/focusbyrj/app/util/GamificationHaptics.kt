package com.focusbyrj.app.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object GamificationHaptics {

    private fun getVibrator(context: Context): Vibrator? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Crisp, light haptic feedback for a correct answer.
     */
    fun playCorrect(context: Context) {
        try {
            val vibrator = getVibrator(context) ?: return
            if (!vibrator.hasVibrator()) return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(25L)
            }
        } catch (_: Exception) {}
    }

    /**
     * Soft double buzz for an incorrect answer.
     */
    fun playWrong(context: Context) {
        try {
            val vibrator = getVibrator(context) ?: return
            if (!vibrator.hasVibrator()) return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val timings = longArrayOf(0, 35, 45, 35)
                val amplitudes = intArrayOf(0, 140, 0, 140)
                vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0, 35, 45, 35), -1)
            }
        } catch (_: Exception) {}
    }

    /**
     * Escalating celebratory haptics when building or maintaining a combo.
     */
    fun playCombo(context: Context, combo: Int) {
        try {
            val vibrator = getVibrator(context) ?: return
            if (!vibrator.hasVibrator()) return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                when {
                    combo >= 8 -> {
                        // High intensity triple pulse
                        val timings = longArrayOf(0, 30, 30, 40, 30, 60)
                        val amplitudes = intArrayOf(0, 180, 0, 220, 0, 255)
                        vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
                    }
                    combo >= 5 -> {
                        // Double pulse on fire
                        val timings = longArrayOf(0, 35, 35, 50)
                        val amplitudes = intArrayOf(0, 160, 0, 210)
                        vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
                    }
                    combo >= 3 -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
                        } else {
                            vibrator.vibrate(VibrationEffect.createOneShot(45L, 180))
                        }
                    }
                    else -> playCorrect(context)
                }
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(40L)
            }
        } catch (_: Exception) {}
    }

    /**
     * Crisp, light haptic feedback for a click or tap.
     */
    fun playLight(context: Context) {
        playCorrect(context)
    }

    /**
     * Success haptic feedback for upgrades and accomplishments.
     */
    fun playSuccess(context: Context) {
        playCombo(context, 4)
    }

    /**
     * Fanfare vibration pattern for opening the mystery chest or leveling up.
     */
    fun playCelebration(context: Context) {
        try {
            val vibrator = getVibrator(context) ?: return
            if (!vibrator.hasVibrator()) return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val timings = longArrayOf(0, 40, 50, 40, 50, 90)
                val amplitudes = intArrayOf(0, 140, 0, 180, 0, 255)
                vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0, 40, 50, 40, 50, 90), -1)
            }
        } catch (_: Exception) {}
    }
}
