package com.fallen.studio.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream

/**
 * Работа с data URL (base64) — формат, в котором старая HTML-программа
 * хранит ассеты внутри .uiproj. Сохраняем совместимость 1-в-1.
 */
object ImageUtils {

    /** Декодирует data URL ("data:image/png;base64,....") в Bitmap. */
    fun decodeDataUrl(dataUrl: String?): Bitmap? {
        if (dataUrl.isNullOrBlank()) return null
        return try {
            val base64Part = dataUrl.substringAfter("base64,", "")
            if (base64Part.isEmpty()) null
            else {
                val bytes = Base64.decode(base64Part, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Кодирует Bitmap в data URL PNG (для сохранения в .uiproj-совместимом формате). */
    fun encodeToDataUrl(bitmap: Bitmap): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        val base64 = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
        return "data:image/png;base64,$base64"
    }

    /** Читает изображение из URI (галерея/файлы) и возвращает data URL. */
    fun uriToDataUrl(context: Context, uri: Uri, maxDimension: Int = 2048): String? = try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val bytes = input.readBytes()
            var bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: return null

            // Ограничиваем размер, чтобы не раздувать проектные файлы
            if (bitmap.width > maxDimension || bitmap.height > maxDimension) {
                val scale = maxDimension.toFloat() / maxOf(bitmap.width, bitmap.height)
                bitmap = Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * scale).toInt().coerceAtLeast(1),
                    (bitmap.height * scale).toInt().coerceAtLeast(1),
                    true,
                )
            }

            // Определяем формат по расширению/MIME: PNG сохраняет прозрачность
            val mime = context.contentResolver.getType(uri) ?: "image/png"
            val stream = ByteArrayOutputStream()
            if (mime.contains("jpeg") || mime.contains("jpg")) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
                "data:image/jpeg;base64," +
                    Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
            } else {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                "data:image/png;base64," +
                    Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
            }
        }
    } catch (_: Exception) {
        null
    }

    /** Читает имя файла из URI. */
    fun fileNameFromUri(context: Context, uri: Uri): String {
        var name = "asset"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) {
                name = cursor.getString(idx) ?: "asset"
            }
        }
        return name.substringBeforeLast('.')
    }
}
