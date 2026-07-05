package com.fallen.studio.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Тактильный отклик (вибрация) для действий в приложении.
 * Работает на всех поддерживаемых версиях Android:
 * - Android 12+ — через VibratorManager
 * - старые версии — через Vibrator напрямую
 */
object Haptics {

    private fun vibrator(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    /** Короткий «тик» — выбор элемента, переключение вкладок */
    fun tick(context: Context) {
        val v = vibrator(context) ?: return
        if (!v.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        } else {
            v.vibrate(VibrationEffect.createOneShot(10, 80))
        }
    }

    /** Лёгкий «клик» — добавление/удаление элементов, undo/redo */
    fun click(context: Context) {
        val v = vibrator(context) ?: return
        if (!v.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
        } else {
            v.vibrate(VibrationEffect.createOneShot(20, 120))
        }
    }
}
