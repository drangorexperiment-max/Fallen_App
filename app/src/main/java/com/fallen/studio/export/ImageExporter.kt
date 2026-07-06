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
        /** Рисовать размерные метки (стрелки с разрешением сторон) внутри холста */
        dimensionLabels: Boolean = false,
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

        // ---------- Размерные метки при экспорте ----------
        // Все метки рисуются ВНУТРИ холста, чтобы не обрезались:
        // холст — вдоль нижней/правой границы, элементы — у своих границ
        if (dimensionLabels) {
            drawDimensionLabels(canvas, project)
        }
        return bitmap
    }

    /** Стрелки с разрешением сторон: ширина у нижней границы, высота у правой */
    private fun drawDimensionLabels(canvas: Canvas, project: FallenProject) {
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(200, 255, 255, 255)
            strokeWidth = 2f
        }
        val shadowLine = Paint(line).apply {
            color = android.graphics.Color.argb(140, 0, 0, 0)
            strokeWidth = 4f
        }
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = 28f
            textAlign = Paint.Align.CENTER
            setShadowLayer(4f, 0f, 0f, android.graphics.Color.BLACK)
        }

        fun marks(x: Float, y: Float, w: Float, h: Float, inset: Float) {
            val arrow = 10f
            // Горизонтальная (ширина) — внутри, у нижней границы
            val hy = y + h - inset
            for (p in listOf(shadowLine, line)) {
                canvas.drawLine(x + inset, hy, x + w - inset, hy, p)
                canvas.drawLine(x + inset, hy, x + inset + arrow, hy - arrow * 0.6f, p)
                canvas.drawLine(x + inset, hy, x + inset + arrow, hy + arrow * 0.6f, p)
                canvas.drawLine(x + w - inset, hy, x + w - inset - arrow, hy - arrow * 0.6f, p)
                canvas.drawLine(x + w - inset, hy, x + w - inset - arrow, hy + arrow * 0.6f, p)
            }
            canvas.drawText(w.toInt().toString(), x + w / 2f, hy - 10f, text)
            // Вертикальная (высота) — внутри, у правой границы
            val vx = x + w - inset
            for (p in listOf(shadowLine, line)) {
                canvas.drawLine(vx, y + inset, vx, y + h - inset, p)
                canvas.drawLine(vx, y + inset, vx - arrow * 0.6f, y + inset + arrow, p)
                canvas.drawLine(vx, y + inset, vx + arrow * 0.6f, y + inset + arrow, p)
                canvas.drawLine(vx, y + h - inset, vx - arrow * 0.6f, y + h - inset - arrow, p)
                canvas.drawLine(vx, y + h - inset, vx + arrow * 0.6f, y + h - inset - arrow, p)
            }
            canvas.save()
            canvas.rotate(-90f, vx - 10f, y + h / 2f)
            canvas.drawText(h.toInt().toString(), vx - 10f, y + h / 2f, text)
            canvas.restore()
        }

        // Холст целиком
        marks(0f, 0f, project.canvas.w, project.canvas.h, inset = 24f)
        // Элементы с включёнными метками
        project.elements.forEach { el ->
            if (el.showDimensions) {
                marks(el.x, el.y, el.w, el.h, inset = 6f)
            }
        }
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
        dimensionLabels: Boolean = false,
    ): Boolean {
        val bitmap = render(project, scale, transparentBackground, context, dimensionLabels) ?: return false
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
