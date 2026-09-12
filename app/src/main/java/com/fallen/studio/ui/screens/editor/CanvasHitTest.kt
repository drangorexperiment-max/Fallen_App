package com.fallen.studio.ui.screens.editor

import com.fallen.studio.data.model.CanvasElement
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Kotlin's stable sort preserves insertion/draw order for equal z values. */
internal fun canvasElementsInDrawOrder(elements: List<CanvasElement>): List<CanvasElement> =
    elements.sortedBy { it.z }

/**
 * Both editor selection and preview activation use the reverse of the actual draw
 * order: the last drawn element wins, including ties in z. This is a rectangle hit
 * region, not alpha-pixel picking; locked elements are still interactive in preview.
 */
internal fun hitTestCanvasElement(
    elements: List<CanvasElement>,
    pointX: Float,
    pointY: Float,
): CanvasElement? = canvasElementsInDrawOrder(elements).asReversed().firstOrNull {
    containsCanvasPoint(it, pointX, pointY)
}

/** Inverse of the renderer's rotation around the element centre; no Android/Compose IO. */
internal fun containsCanvasPoint(element: CanvasElement, pointX: Float, pointY: Float): Boolean {
    if (!pointX.isFinite() || !pointY.isFinite() ||
        !element.x.isFinite() || !element.y.isFinite() ||
        !element.w.isFinite() || !element.h.isFinite() || !element.rotation.isFinite() ||
        element.w <= 0f || element.h <= 0f
    ) return false

    val radians = Math.toRadians((element.rotation % 360f).toDouble())
    val dx = pointX.toDouble() - (element.x.toDouble() + element.w / 2.0)
    val dy = pointY.toDouble() - (element.y.toDouble() + element.h / 2.0)
    val localX = dx * cos(radians) + dy * sin(radians)
    val localY = -dx * sin(radians) + dy * cos(radians)
    // A tiny tolerance keeps transformed edge/corner taps inclusive after Float rounding.
    val epsilon = 0.0001
    return abs(localX) <= element.w / 2.0 + epsilon && abs(localY) <= element.h / 2.0 + epsilon
}
