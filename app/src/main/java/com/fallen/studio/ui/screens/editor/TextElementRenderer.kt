package com.fallen.studio.ui.screens.editor

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.fallen.studio.data.model.CanvasElement
import com.fallen.studio.data.model.ProjectFont
import com.fallen.studio.util.ColorUtils
import com.fallen.studio.util.FontManager

/**
 * Рендер текстового элемента на нативном Canvas.
 *
 * ИСПРАВЛЕНИЕ ТЕНИ (пункт 4 требований):
 * В старой HTML-версии text-shadow применялся только к заливке текста,
 * а обводка (-webkit-text-stroke) оставалась без тени.
 * Здесь тень рисуется ОТДЕЛЬНЫМ проходом от ПОЛНОЙ формы глифов
 * (заливка + обводка вместе), поэтому тень корректно повторяет
 * контур текста с обводкой — как в PicsArt.
 *
 * Порядок отрисовки:
 *   1. Теневой проход: stroke + fill в цвете тени со смещением и размытием
 *   2. Обводка (stroke)
 *   3. Заливка (fill)
 */
object TextElementRenderer {

    fun draw(
        canvas: Canvas,
        el: CanvasElement,
        fonts: List<ProjectFont> = emptyList(),
        context: Context? = null,
    ) {
        val text = el.text ?: return
        if (text.isEmpty()) return

        val fontSize = (el.fontSize ?: 24).toFloat()
        val isBold = when (el.fontWeight) {
            "bold", "700", "800", "900" -> true
            else -> false
        }
        val typeface: Typeface = FontManager.resolve(context, el.fontFamily, fonts, isBold)

        val alignment = when (el.textAlign) {
            "left" -> Layout.Alignment.ALIGN_NORMAL
            "right" -> Layout.Alignment.ALIGN_OPPOSITE
            else -> Layout.Alignment.ALIGN_CENTER
        }

        // Слайдеры 0..100 из старого формата, 50 = нейтральное значение
        val lineSpacingMult = 0.5f + (el.lineHeight ?: 50f) / 100f     // 50 -> 1.0
        val letterSpacingEm = ((el.letterSpacing ?: 50f) - 50f) / 250f // 50 -> 0

        val strokeWidth = el.strokeWidth ?: 0f
        val hasStroke = strokeWidth > 0f
        val shadowEnabled = el.shadowEnabled == true

        val fillColor = ColorUtils.parseHex(el.color).let { c ->
            AndroidColor.argb(
                255,
                (c.red * 255).toInt(),
                (c.green * 255).toInt(),
                (c.blue * 255).toInt(),
            )
        }
        val strokeColor = ColorUtils.parseHex(el.strokeColor).let { c ->
            AndroidColor.argb(
                255,
                (c.red * 255).toInt(),
                (c.green * 255).toInt(),
                (c.blue * 255).toInt(),
            )
        }

        fun basePaint(): TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            textSize = fontSize
            letterSpacing = letterSpacingEm
        }

        fun buildLayout(paint: TextPaint): StaticLayout =
            StaticLayout.Builder
                .obtain(text, 0, text.length, paint, el.w.toInt().coerceAtLeast(1))
                .setAlignment(alignment)
                .setLineSpacing(0f, lineSpacingMult)
                .setIncludePad(false)
                .build()

        // ---------- 1. ТЕНЕВОЙ ПРОХОД (fill + stroke единой формой) ----------
        if (shadowEnabled) {
            val blur = (el.shadowBlur ?: 20f).coerceAtLeast(0.1f)
            val opacity = ((el.shadowOpacity ?: 50f) / 100f).coerceIn(0f, 1f)
            val offsetX = (el.shadowX ?: 50f) - 50f
            val offsetY = (el.shadowY ?: 50f) - 50f
            val shadowBase = ColorUtils.parseHex(el.shadowColor)
            val shadowColor = AndroidColor.argb(
                (opacity * 255).toInt(),
                (shadowBase.red * 255).toInt(),
                (shadowBase.green * 255).toInt(),
                (shadowBase.blue * 255).toInt(),
            )

            canvas.save()
            canvas.translate(el.x + offsetX, el.y + offsetY)

            // Тень от обводки — рисуем stroke-форму в цвете тени с размытием
            if (hasStroke) {
                val shadowStrokePaint = basePaint().apply {
                    style = Paint.Style.STROKE
                    this.strokeWidth = strokeWidth * 2f
                    strokeJoin = Paint.Join.ROUND
                    color = shadowColor
                    maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL)
                }
                buildLayout(shadowStrokePaint).draw(canvas)
            }

            // Тень от заливки
            val shadowFillPaint = basePaint().apply {
                style = Paint.Style.FILL
                color = shadowColor
                maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL)
            }
            buildLayout(shadowFillPaint).draw(canvas)

            canvas.restore()
        }

        // ---------- 2. ОБВОДКА ----------
        canvas.save()
        canvas.translate(el.x, el.y)

        if (hasStroke) {
            val strokePaint = basePaint().apply {
                style = Paint.Style.STROKE
                this.strokeWidth = strokeWidth * 2f
                strokeJoin = Paint.Join.ROUND
                strokeMiter = 2f
                color = strokeColor
            }
            buildLayout(strokePaint).draw(canvas)
        }

        // ---------- 3. ЗАЛИВКА ----------
        val fillPaint = basePaint().apply {
            style = Paint.Style.FILL
            color = fillColor
        }
        buildLayout(fillPaint).draw(canvas)

        canvas.restore()
    }

    /** Расчёт высоты текста для авто-подгонки рамки. */
    fun measureHeight(el: CanvasElement): Float {
        val text = el.text ?: return el.h
        if (text.isEmpty()) return el.h
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = (el.fontSize ?: 24).toFloat()
        }
        val lineSpacingMult = 0.5f + (el.lineHeight ?: 50f) / 100f
        val layout = StaticLayout.Builder
            .obtain(text, 0, text.length, paint, el.w.toInt().coerceAtLeast(1))
            .setLineSpacing(0f, lineSpacingMult)
            .setIncludePad(false)
            .build()
        return layout.height.toFloat()
    }
}
