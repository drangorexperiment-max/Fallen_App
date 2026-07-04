package com.fallen.studio.util

import androidx.compose.ui.graphics.Color

object ColorUtils {

    /** Парсит hex-строку ("#RRGGBB" или "#AARRGGBB") в Compose Color. */
    fun parseHex(hex: String?, fallback: Color = Color.Black): Color {
        if (hex.isNullOrBlank()) return fallback
        return try {
            val clean = hex.removePrefix("#")
            val value = when (clean.length) {
                6 -> 0xFF000000 or clean.toLong(16)
                8 -> clean.toLong(16)
                3 -> {
                    // #RGB -> #RRGGBB
                    val r = clean[0].toString().repeat(2)
                    val g = clean[1].toString().repeat(2)
                    val b = clean[2].toString().repeat(2)
                    0xFF000000 or "$r$g$b".toLong(16)
                }
                else -> return fallback
            }
            Color(value)
        } catch (_: Exception) {
            fallback
        }
    }

    /** Color -> "#RRGGBB" */
    fun toHex(color: Color): String {
        val r = (color.red * 255).toInt().coerceIn(0, 255)
        val g = (color.green * 255).toInt().coerceIn(0, 255)
        val b = (color.blue * 255).toInt().coerceIn(0, 255)
        return "#%02X%02X%02X".format(r, g, b)
    }
}
