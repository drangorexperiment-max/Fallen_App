package com.fallen.studio.ui.screens.editor

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import com.fallen.studio.data.AppSettings
import com.fallen.studio.data.model.CanvasElement
import com.fallen.studio.ui.theme.FallenColors
import com.fallen.studio.util.ImageUtils
import kotlin.math.abs

/** Ручки ресайза */
private enum class Handle { NW, N, NE, W, E, SW, S, SE }

/** Режим текущего жеста */
private enum class GestureMode { NONE, DRAG, RESIZE, PAN_ZOOM }

/**
 * Канвас редактора Fallen:
 * - Рабочее поле чётко выделено на фоне (тень + рамка), не сливается
 * - Ровная квадратная сетка (вкл/выкл в настройках)
 * - Один палец: перемещение элемента / выделение
 * - Два пальца: пан и масштаб вида
 * - Ручки ресайза у выделенного элемента
 */
@Composable
fun EditorCanvas(
    state: EditorState,
    settings: AppSettings,
    colors: FallenColors,
    isDarkTheme: Boolean,
    onSelect: (String?) -> Unit,
    onBeginGesture: () -> Unit,
    onMove: (String, Float, Float) -> Unit,
    onResize: (String, Float, Float, Float, Float) -> Unit,
    modifier: Modifier = Modifier,
    onViewTransform: (scale: Float) -> Unit = {},
) {
    var viewScale by remember { mutableFloatStateOf(0.15f) }
    var viewOffset by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var initialized by remember { mutableStateOf(false) }

    // Кэш декодированных Bitmap по src (LRU-подобный, простой)
    val bitmapCache = remember { mutableMapOf<String, Bitmap?>() }

    fun bitmapFor(src: String?): Bitmap? {
        if (src == null) return null
        return bitmapCache.getOrPut(src) { ImageUtils.decodeDataUrl(src) }
    }

    // Автоцентрирование при первом измерении
    fun fitToScreen() {
        if (containerSize.width == 0 || containerSize.height == 0) return
        val padding = 48f
        val scaleX = (containerSize.width - padding * 2) / state.canvas.w
        val scaleY = (containerSize.height - padding * 2) / state.canvas.h
        viewScale = minOf(scaleX, scaleY).coerceIn(0.02f, 4f)
        viewOffset = Offset(
            (containerSize.width - state.canvas.w * viewScale) / 2f,
            (containerSize.height - state.canvas.h * viewScale) / 2f,
        )
        onViewTransform(viewScale)
    }

    fun screenToCanvas(p: Offset): Offset =
        Offset((p.x - viewOffset.x) / viewScale, (p.y - viewOffset.y) / viewScale)

    /** Хит-тест элементов сверху вниз (по z) */
    fun hitTest(canvasPoint: Offset): CanvasElement? =
        state.elements
            .sortedByDescending { it.z }
            .firstOrNull { el ->
                canvasPoint.x >= el.x && canvasPoint.x <= el.x + el.w &&
                    canvasPoint.y >= el.y && canvasPoint.y <= el.y + el.h
            }

    /** Хит-тест ручек ресайза выделенного элемента */
    fun hitTestHandle(canvasPoint: Offset): Handle? {
        val el = state.selectedElement ?: return null
        val r = 24f / viewScale // радиус захвата ручки в координатах канваса
        val handles = mapOf(
            Handle.NW to Offset(el.x, el.y),
            Handle.N to Offset(el.x + el.w / 2, el.y),
            Handle.NE to Offset(el.x + el.w, el.y),
            Handle.W to Offset(el.x, el.y + el.h / 2),
            Handle.E to Offset(el.x + el.w, el.y + el.h / 2),
            Handle.SW to Offset(el.x, el.y + el.h),
            Handle.S to Offset(el.x + el.w / 2, el.y + el.h),
            Handle.SE to Offset(el.x + el.w, el.y + el.h),
        )
        return handles.entries.firstOrNull { (_, pos) ->
            abs(canvasPoint.x - pos.x) < r && abs(canvasPoint.y - pos.y) < r
        }?.key
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(if (isDarkTheme) Color(0xFF07070D) else Color(0xFFE4E4EC)),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(state.selectedId, state.elements, viewScale, viewOffset) {
                    awaitEachGesture {
                        val firstDown = awaitFirstDown()
                        var mode = GestureMode.NONE
                        var activeHandle: Handle? = null
                        var gestureStarted = false

                        val startCanvasPoint = screenToCanvas(firstDown.position)
                        val handleHit = hitTestHandle(startCanvasPoint)
                        val elementHit = hitTest(startCanvasPoint)

                        // Стартовые данные для ресайза
                        val sel = state.selectedElement
                        val startRect = sel?.let { Rect(it.x, it.y, it.x + it.w, it.y + it.h) }

                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type == PointerEventType.Release &&
                                event.changes.all { !it.pressed }
                            ) {
                                // Отпустили все пальцы
                                if (mode == GestureMode.NONE) {
                                    // Это был тап — выделение
                                    onSelect(elementHit?.id)
                                }
                                break
                            }

                            val pointerCount = event.changes.count { it.pressed }

                            if (pointerCount >= 2) {
                                // Два пальца — пан и зум вида
                                mode = GestureMode.PAN_ZOOM
                                val zoom = event.calculateZoom()
                                val pan = event.calculatePan()
                                val centroid = event.calculateCentroid()
                                if (zoom != 1f || pan != Offset.Zero) {
                                    val newScale = (viewScale * zoom).coerceIn(0.02f, 6f)
                                    // Масштабирование относительно центра жеста
                                    viewOffset = Offset(
                                        centroid.x - (centroid.x - viewOffset.x) * (newScale / viewScale) + pan.x,
                                        centroid.y - (centroid.y - viewOffset.y) * (newScale / viewScale) + pan.y,
                                    )
                                    viewScale = newScale
                                    onViewTransform(viewScale)
                                }
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                                continue
                            }

                            val change = event.changes.firstOrNull { it.pressed } ?: continue
                            if (!change.positionChanged()) continue

                            val delta = change.position - change.previousPosition
                            val canvasDelta = Offset(delta.x / viewScale, delta.y / viewScale)

                            when {
                                mode == GestureMode.RESIZE ||
                                    (mode == GestureMode.NONE && handleHit != null) -> {
                                    mode = GestureMode.RESIZE
                                    activeHandle = activeHandle ?: handleHit
                                    val el = state.selectedElement
                                    if (el != null && startRect != null && !el.locked) {
                                        if (!gestureStarted) {
                                            gestureStarted = true
                                            onBeginGesture()
                                        }
                                        val current = screenToCanvas(change.position)
                                        var left = el.x
                                        var top = el.y
                                        var right = el.x + el.w
                                        var bottom = el.y + el.h
                                        when (activeHandle) {
                                            Handle.NW -> { left = current.x; top = current.y }
                                            Handle.N -> top = current.y
                                            Handle.NE -> { right = current.x; top = current.y }
                                            Handle.W -> left = current.x
                                            Handle.E -> right = current.x
                                            Handle.SW -> { left = current.x; bottom = current.y }
                                            Handle.S -> bottom = current.y
                                            Handle.SE -> { right = current.x; bottom = current.y }
                                            null -> {}
                                        }
                                        if (right - left >= 10f && bottom - top >= 10f) {
                                            onResize(el.id, left, top, right - left, bottom - top)
                                        }
                                    }
                                    change.consume()
                                }

                                mode == GestureMode.DRAG ||
                                    (mode == GestureMode.NONE && elementHit != null &&
                                        elementHit.id == state.selectedId && !elementHit.locked) -> {
                                    mode = GestureMode.DRAG
                                    if (!gestureStarted) {
                                        gestureStarted = true
                                        onBeginGesture()
                                    }
                                    onMove(elementHit!!.id, canvasDelta.x, canvasDelta.y)
                                    change.consume()
                                }

                                else -> {
                                    // Один палец по пустому месту — пан вида
                                    mode = GestureMode.PAN_ZOOM
                                    viewOffset += delta
                                    change.consume()
                                }
                            }
                        }
                    }
                },
        ) {
            if (!initialized && size.width > 0) {
                containerSize = IntSize(size.width.toInt(), size.height.toInt())
                initialized = true
                fitToScreen()
            }

            withTransform({
                translate(viewOffset.x, viewOffset.y)
                scale(viewScale, viewScale, Offset.Zero)
            }) {
                val canvasW = state.canvas.w.toFloat()
                val canvasH = state.canvas.h.toFloat()

                // ---------- Тень рабочего поля (выделение на фоне) ----------
                drawRect(
                    color = Color.Black.copy(alpha = if (isDarkTheme) 0.6f else 0.18f),
                    topLeft = Offset(-8f / viewScale, -8f / viewScale),
                    size = Size(canvasW + 16f / viewScale, canvasH + 16f / viewScale),
                )

                // ---------- Фон рабочего поля ----------
                if (settings.checkerboardBackground) {
                    // Шахматный фон (прозрачность)
                    val cell = 40f
                    val c1 = if (isDarkTheme) Color(0xFF1E1E2C) else Color(0xFFEDEDF3)
                    val c2 = if (isDarkTheme) Color(0xFF16161F) else Color(0xFFFFFFFF)
                    var yy = 0f
                    var rowIndex = 0
                    while (yy < canvasH) {
                        var xx = 0f
                        var colIndex = 0
                        while (xx < canvasW) {
                            drawRect(
                                color = if ((rowIndex + colIndex) % 2 == 0) c1 else c2,
                                topLeft = Offset(xx, yy),
                                size = Size(
                                    minOf(cell, canvasW - xx),
                                    minOf(cell, canvasH - yy),
                                ),
                            )
                            xx += cell
                            colIndex++
                        }
                        yy += cell
                        rowIndex++
                    }
                } else {
                    drawRect(
                        color = colors.canvasBackground,
                        topLeft = Offset.Zero,
                        size = Size(canvasW, canvasH),
                    )
                }

                // ---------- Квадратная сетка ----------
                if (settings.gridEnabled) {
                    val grid = settings.gridSize.coerceAtLeast(10).toFloat()
                    val gridStroke = 1f / viewScale
                    var gx = grid
                    while (gx < canvasW) {
                        drawLine(
                            color = colors.gridLine,
                            start = Offset(gx, 0f),
                            end = Offset(gx, canvasH),
                            strokeWidth = gridStroke,
                        )
                        gx += grid
                    }
                    var gy = grid
                    while (gy < canvasH) {
                        drawLine(
                            color = colors.gridLine,
                            start = Offset(0f, gy),
                            end = Offset(canvasW, gy),
                            strokeWidth = gridStroke,
                        )
                        gy += grid
                    }
                }

                // ---------- Центральные направляющие ----------
                if (settings.showCenterGuides) {
                    val dash = PathEffect.dashPathEffect(
                        floatArrayOf(12f / viewScale, 8f / viewScale),
                    )
                    drawLine(
                        color = colors.selection.copy(alpha = 0.35f),
                        start = Offset(canvasW / 2, 0f),
                        end = Offset(canvasW / 2, canvasH),
                        strokeWidth = 1.5f / viewScale,
                        pathEffect = dash,
                    )
                    drawLine(
                        color = colors.selection.copy(alpha = 0.35f),
                        start = Offset(0f, canvasH / 2),
                        end = Offset(canvasW, canvasH / 2),
                        strokeWidth = 1.5f / viewScale,
                        pathEffect = dash,
                    )
                }

                // ---------- Элементы (по z-порядку) ----------
                state.elements.sortedBy { it.z }.forEach { el ->
                    val alpha = (el.opacity / 100f).coerceIn(0f, 1f)
                    if (el.isImage) {
                        val bmp = bitmapFor(el.src)
                        if (bmp != null) {
                            drawIntoCanvasWithAlpha(alpha) {
                                it.nativeCanvas.drawBitmap(
                                    bmp,
                                    null,
                                    android.graphics.RectF(el.x, el.y, el.x + el.w, el.y + el.h),
                                    null,
                                )
                            }
                        } else {
                            // Плейсхолдер, если ассет не декодировался
                            drawRect(
                                color = Color.Gray.copy(alpha = 0.3f),
                                topLeft = Offset(el.x, el.y),
                                size = Size(el.w, el.h),
                            )
                        }
                    } else if (el.isText) {
                        drawIntoCanvasWithAlpha(alpha) {
                            TextElementRenderer.draw(it.nativeCanvas, el)
                        }
                    }
                }

                // ---------- Рамка выделения и ручки ----------
                state.selectedElement?.let { el ->
                    val selStroke = 2f / viewScale
                    drawRect(
                        color = colors.selection,
                        topLeft = Offset(el.x, el.y),
                        size = Size(el.w, el.h),
                        style = Stroke(width = selStroke),
                    )
                    val handleR = 7f / viewScale
                    listOf(
                        Offset(el.x, el.y),
                        Offset(el.x + el.w / 2, el.y),
                        Offset(el.x + el.w, el.y),
                        Offset(el.x, el.y + el.h / 2),
                        Offset(el.x + el.w, el.y + el.h / 2),
                        Offset(el.x, el.y + el.h),
                        Offset(el.x + el.w / 2, el.y + el.h),
                        Offset(el.x + el.w, el.y + el.h),
                    ).forEach { pos ->
                        drawCircle(color = Color.White, radius = handleR, center = pos)
                        drawCircle(
                            color = colors.selection,
                            radius = handleR,
                            center = pos,
                            style = Stroke(width = 2.5f / viewScale),
                        )
                    }
                }

                // ---------- Рамка рабочего поля ----------
                drawRect(
                    color = colors.selection.copy(alpha = 0.5f),
                    topLeft = Offset.Zero,
                    size = Size(canvasW, canvasH),
                    style = Stroke(width = 2f / viewScale),
                )
            }
        }
    }
}

/** Хелпер: рисование с альфой через saveLayer */
private inline fun androidx.compose.ui.graphics.drawscope.DrawScope.drawIntoCanvasWithAlpha(
    alpha: Float,
    crossinline block: (androidx.compose.ui.graphics.Canvas) -> Unit,
) {
    drawIntoCanvas { canvas ->
        if (alpha < 1f) {
            val paint = android.graphics.Paint().apply {
                this.alpha = (alpha * 255).toInt()
            }
            canvas.nativeCanvas.saveLayer(null, paint)
            block(canvas)
            canvas.nativeCanvas.restore()
        } else {
            block(canvas)
        }
    }
}
