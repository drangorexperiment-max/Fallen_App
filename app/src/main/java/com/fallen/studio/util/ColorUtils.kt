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

    /** Псевдоним parseHex с фолбэком по умолчанию. */
    fun parse(hex: String?, fallback: Color = Color.Black): Color = parseHex(hex, fallback)

    /** Парсит hex или возвращает null при ошибке. */
    fun parseOrNull(hex: String?): Color? {
        if (hex.isNullOrBlank()) return null
        val sentinel = Color(0x01010101)
        val parsed = parseHex(hex, sentinel)
        return if (parsed == sentinel) null else parsed
    }

    /** Возвращает оттенок (hue, 0..360) для hex-цвета. */
    fun hueOf(hex: String?): Float {
        val c = parse(hex, Color.Red)
        val r = c.red
        val g = c.green
        val b = c.blue
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val delta = max - min
        if (delta == 0f) return 0f
        val hue = when (max) {
            r -> 60f * (((g - b) / delta) % 6f)
            g -> 60f * (((b - r) / delta) + 2f)
            else -> 60f * (((r - g) / delta) + 4f)
        }
        return if (hue < 0f) hue + 360f else hue
    }
}
