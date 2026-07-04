package com.fallen.studio.util

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.util.Base64
import com.fallen.studio.data.model.ProjectFont
import java.io.File

/**
 * Менеджер пользовательских шрифтов.
 *
 * Шрифты хранятся в проекте как data URL (base64) — так же,
 * как в старом HTML-формате .uiproj, поэтому старые проекты
 * с встроенными шрифтами открываются без потерь.
 *
 * Для создания Typeface байты шрифта записываются во временный
 * файл в кэше приложения (Android не умеет строить Typeface
 * напрямую из памяти до API 29 единообразно).
 */
object FontManager {

    /** Кэш Typeface по id шрифта */
    private val typefaceCache = mutableMapOf<String, Typeface?>()

    /** Встроенные семейства (имя -> системный Typeface) */
    private val builtinFamilies: Map<String, Typeface> = mapOf(
        "Inter" to Typeface.SANS_SERIF,
        "Sans" to Typeface.SANS_SERIF,
        "Serif" to Typeface.SERIF,
        "Monospace" to Typeface.MONOSPACE,
    )

    /** Список встроенных шрифтов для выпадающих меню */
    val builtinFontNames: List<String> = listOf("Inter", "Sans", "Serif", "Monospace")

    /**
     * Возвращает Typeface для элемента.
     * @param fontFamily имя семейства из элемента (может совпадать с именем
     *   пользовательского шрифта из проекта или со встроенным)
     * @param fonts пользовательские шрифты текущего проекта
     * @param bold требуется ли жирное начертание
     */
    fun resolve(
        context: Context?,
        fontFamily: String?,
        fonts: List<ProjectFont>,
        bold: Boolean,
    ): Typeface {
        // 1. Пользовательский шрифт проекта (по имени или id)
        val custom = fonts.firstOrNull { it.name == fontFamily || it.id == fontFamily }
        if (custom != null && context != null) {
            val tf = typefaceFor(context, custom)
            if (tf != null) {
                return if (bold) Typeface.create(tf, Typeface.BOLD) else tf
            }
        }
        // 2. Встроенное семейство
        val base = builtinFamilies[fontFamily] ?: Typeface.SANS_SERIF
        return Typeface.create(base, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    /** Создаёт (или берёт из кэша) Typeface из пользовательского шрифта. */
    private fun typefaceFor(context: Context, font: ProjectFont): Typeface? =
        typefaceCache.getOrPut(font.id) {
            try {
                val base64Part = font.src.substringAfter("base64,", "")
                if (base64Part.isEmpty()) return@getOrPut null
                val bytes = Base64.decode(base64Part, Base64.DEFAULT)
                val dir = File(context.cacheDir, "fonts").apply { mkdirs() }
                val ext = if (font.format.contains("opentype")) "otf" else "ttf"
                val file = File(dir, "${font.id}.$ext")
                if (!file.exists() || file.length() != bytes.size.toLong()) {
                    file.writeBytes(bytes)
                }
                Typeface.createFromFile(file)
            } catch (_: Exception) {
                null
            }
        }

    /**
     * Читает файл шрифта по Uri и создаёт ProjectFont c data URL.
     * Возвращает null при ошибке или если файл слишком большой (> 8 МБ).
     */
    fun fontFromUri(context: Context, uri: Uri): ProjectFont? {
        return try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return null
            if (bytes.isEmpty() || bytes.size > 8 * 1024 * 1024) return null

            val fileName = ImageUtils.fileNameFromUri(context, uri)
            val isOtf = fileName.endsWith(".otf", ignoreCase = true)
            val format = if (isOtf) "opentype" else "truetype"
            val mime = if (isOtf) "font/otf" else "font/ttf"
            // Имя семейства = имя файла без расширения
            val name = fileName
                .substringBeforeLast('.')
                .replace('_', ' ')
                .replace('-', ' ')
                .trim()
                .ifBlank { "Шрифт" }

            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            ProjectFont(
                id = "f${System.currentTimeMillis()}_${(0..9999).random()}",
                name = name,
                src = "data:$mime;base64,$base64",
                format = format,
                fileName = fileName,
            )
        } catch (_: Exception) {
            null
        }
    }

    /** Сбрасывает кэш (например, при удалении шрифта из проекта). */
    fun invalidate(fontId: String) {
        typefaceCache.remove(fontId)
    }
}
