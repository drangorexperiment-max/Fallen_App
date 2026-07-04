package com.fallen.studio.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import com.fallen.studio.data.model.FallenProject
import com.fallen.studio.ui.screens.editor.TextElementRenderer
import com.fallen.studio.util.ImageUtils
import java.io.OutputStream

/**
 * Рендер проекта в PNG-изображение (экспорт холста).
 */
object ImageExporter {

    /**
     * Рисует весь холст проекта в Bitmap.
     * @param scale множитель разрешения (0.25..4)
     * @param transparentBackground true — прозрачный фон, false — тёмный
     */
    fun render(
        project: FallenProject,
        scale: Float = 1f,
        transparentBackground: Boolean = true,
        context: Context? = null,
    ): Bitmap? {
        val w = (project.canvas.w * scale).toInt().coerceIn(1, 8192)
        val h = (project.canvas.h * scale).toInt().coerceIn(1, 8192)

        val bitmap = try {
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        } catch (e: OutOfMemoryError) {
            return null
        }

        val canvas = Canvas(bitmap)
        if (!transparentBackground) {
            canvas.drawColor(android.graphics.Color.rgb(10, 10, 18))
        }
        canvas.scale(scale, scale)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        project.elements.sortedBy { it.z }.forEach { el ->
            canvas.save()
            // Прозрачность и поворот элемента
            val alpha = (el.opacity / 100f).coerceIn(0f, 1f)
            if (el.rotation != 0f) {
                canvas.rotate(el.rotation, el.x + el.w / 2f, el.y + el.h / 2f)
            }
            if (el.isImage) {
                val src = el.src ?: project.assets.find { it.id == el.assetId }?.src
                val bmp = ImageUtils.decodeDataUrl(src)
                if (bmp != null) {
                    paint.alpha = (alpha * 255).toInt()
                    canvas.drawBitmap(bmp, null, RectF(el.x, el.y, el.x + el.w, el.y + el.h), paint)
                }
            } else if (el.isText) {
                if (alpha < 1f) {
                    canvas.saveLayerAlpha(
                        el.x - 200f, el.y - 200f,
                        el.x + el.w + 200f, el.y + el.h + 200f,
                        (alpha * 255).toInt(),
                    )
                }
                TextElementRenderer.draw(canvas, el, project.fonts, context)
                if (alpha < 1f) canvas.restore()
            }
            canvas.restore()
        }
        return bitmap
    }

    /** Сохраняет Bitmap в поток как PNG. */
    fun writePng(bitmap: Bitmap, out: OutputStream) {
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
    }

    /** Сохраняет проект как PNG по Uri (SAF). */
    fun exportToUri(
        context: Context,
        project: FallenProject,
        uri: Uri,
        scale: Float,
        transparentBackground: Boolean,
    ): Boolean {
        val bitmap = render(project, scale, transparentBackground, context) ?: return false
        return try {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                writePng(bitmap, out)
                true
            } ?: false
        } catch (e: Exception) {
            false
        } finally {
            bitmap.recycle()
        }
    }
}
