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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import com.fallen.studio.data.AppSettings
import com.fallen.studio.data.model.CanvasElement
import com.fallen.studio.ui.theme.FallenColors
import com.fallen.studio.util.ImageUtils
import kotlin.math.abs

/** Ручки ресайза. SCALE — пропорциональное масштабирование (правый нижний угол, снаружи) */
private enum class Handle { NW, N, NE, W, E, SW, S, SE, SCALE }

/** Смещение ручки масштабирования от правого нижнего угла (в px канваса при scale=1) */
private const val SCALE_HANDLE_OFFSET = 34f

/** Режим текущего жеста */
private enum class GestureMode { NONE, DRAG, RESIZE, PAN, PAN_ZOOM }

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
    onBeginScaleGesture: () -> Unit = {},
    onScale: (String, Float) -> Unit = { _, _ -> },
) {
    var viewScale by remember { mutableFloatStateOf(0.15f) }
    var viewOffset by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var initialized by remember { mutableStateOf(false) }
    // Пока пользователь сам не двигал/не масштабировал вид,
    // холст автоматически центрируется заново
    var userMoved by remember { mutableStateOf(false) }

    val context = LocalContext.current

    // ИСПРАВЛЕНИЕ ЗУМА: pointerInput(Unit) не пересоздаётся при изменении
    // viewScale/viewOffset/elements, поэтому жест не обрывается на каждом
    // кадре. Актуальное состояние читается через rememberUpdatedState.
    val currentState by rememberUpdatedState(state)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentOnBeginGesture by rememberUpdatedState(onBeginGesture)
    val currentOnMove by rememberUpdatedState(onMove)
    val currentOnResize by rememberUpdatedState(onResize)
    val currentOnViewTransform by rememberUpdatedState(onViewTransform)
    val currentOnBeginScaleGesture by rememberUpdatedState(onBeginScaleGesture)
    val currentOnScale by rememberUpdatedState(onScale)

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
        val scaleX = (containerSize.width - padding * 2) / currentState.canvas.w
        val scaleY = (containerSize.height - padding * 2) / currentState.canvas.h
        viewScale = minOf(scaleX, scaleY).coerceIn(0.02f, 4f)
        viewOffset = Offset(
            (containerSize.width - currentState.canvas.w * viewScale) / 2f,
            (containerSize.height - currentState.canvas.h * viewScale) / 2f,
        )
        currentOnViewTransform(viewScale)
    }

    // ИСПРАВЛЕНИЕ ЦЕНТРИРОВАНИЯ: раньше fitToScreen вызывался только один раз
    // при первой отрисовке — до того как загружался реальный размер холста,
    // поэтому холст оказывался не по центру. Теперь центрирование повторяется
    // при изменении размера контейнера или холста, пока пользователь
    // сам не начал двигать/масштабировать вид.
    LaunchedEffect(containerSize, state.canvas.w, state.canvas.h) {
        if (!userMoved) fitToScreen()
    }

    fun screenToCanvas(p: Offset): Offset =
        Offset((p.x - viewOffset.x) / viewScale, (p.y - viewOffset.y) / viewScale)

    /** Хит-тест элементов сверху вниз (по z) */
    fun hitTest(canvasPoint: Offset): CanvasElement? =
        currentState.elements
            .sortedByDescending { it.z }
            .firstOrNull { el ->
                canvasPoint.x >= el.x && canvasPoint.x <= el.x + el.w &&
                    canvasPoint.y >= el.y && canvasPoint.y <= el.y + el.h
            }

    /** Хит-тест ручек ресайза выделенного элемента */
    fun hitTestHandle(canvasPoint: Offset): Handle? {
        val el = currentState.selectedElement ?: return null
        val r = 24f / viewScale // радиус захвата ручки в координатах канваса

        // Ручка пропорционального масштабирования — правый нижний угол,
        // чуть снаружи рамки. Проверяется первой (приоритет над SE).
        val scalePos = Offset(
            el.x + el.w + SCALE_HANDLE_OFFSET / viewScale,
            el.y + el.h + SCALE_HANDLE_OFFSET / viewScale,
        )
        if (abs(canvasPoint.x - scalePos.x) < r && abs(canvasPoint.y - scalePos.y) < r) {
            return Handle.SCALE
        }

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
            .onSizeChanged { containerSize = it }
            .background(if (isDarkTheme) Color(0xFF07070D) else Color(0xFFE4E4EC)),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val firstDown = awaitFirstDown()
                        var mode = GestureMode.NONE
                        var activeHandle: Handle? = null
                        var gestureStarted = false

                        val startCanvasPoint = screenToCanvas(firstDown.position)
                        val handleHit = hitTestHandle(startCanvasPoint)
                        val elementHit = hitTest(startCanvasPoint)

                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.changes.all { !it.pressed }) {
                                // Отпустили все пальцы
                                if (mode == GestureMode.NONE) {
                                    // Это был тап — выделение
                                    currentOnSelect(elementHit?.id)
                                }
                                break
                            }

                            val pointerCount = event.changes.count { it.pressed }

                            if (pointerCount >= 2) {
                                // Два пальца — пан и зум вида.
                                // Как только начали пан/зум, перетаскивание
                                // элемента больше не активируется до конца жеста.
                                mode = GestureMode.PAN_ZOOM
                                userMoved = true
                                val zoom = event.calculateZoom()
                                val pan = event.calculatePan()
                                val centroid = event.calculateCentroid()
                                if (zoom != 1f || pan != Offset.Zero) {
                                    val oldScale = viewScale
                                    val newScale = (oldScale * zoom).coerceIn(0.02f, 6f)
                                    // Масштабирование относительно центра жеста
                                    viewOffset = Offset(
                                        centroid.x - (centroid.x - viewOffset.x) * (newScale / oldScale) + pan.x,
                                        centroid.y - (centroid.y - viewOffset.y) * (newScale / oldScale) + pan.y,
                                    )
                                    viewScale = newScale
                                    currentOnViewTransform(newScale)
                                }
                                event.changes.forEach { it.consume() }
                                continue
                            }

                            // Если жест уже был двухпальцевым — остался один палец,
                            // просто ждём завершения, ничего не двигаем.
                            if (mode == GestureMode.PAN_ZOOM && pointerCount == 1) {
                                event.changes.forEach { it.consume() }
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
                                    val el = currentState.selectedElement
                                    if (el != null && !el.locked && activeHandle == Handle.SCALE) {
                                        // Пропорциональное масштабирование:
                                        // рамка + изображение + шрифт текста
                                        if (!gestureStarted) {
                                            gestureStarted = true
                                            currentOnBeginScaleGesture()
                                        }
                                        val current = screenToCanvas(change.position)
                                        val newW = current.x - el.x - SCALE_HANDLE_OFFSET / viewScale
                                        if (newW > 10f) {
                                            currentOnScale(el.id, newW)
                                        }
                                        change.consume()
                                        continue
                                    }
                                    if (el != null && !el.locked) {
                                        if (!gestureStarted) {
                                            gestureStarted = true
                                            currentOnBeginGesture()
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
                                            Handle.SCALE, null -> {}
                                        }
                                        if (right - left >= 10f && bottom - top >= 10f) {
                                            currentOnResize(el.id, left, top, right - left, bottom - top)
                                        }
                                    }
                                    change.consume()
                                }

                                mode == GestureMode.DRAG ||
                                    (mode == GestureMode.NONE && elementHit != null &&
                                        elementHit.id == currentState.selectedId && !elementHit.locked) -> {
                                    mode = GestureMode.DRAG
                                    if (!gestureStarted) {
                                        gestureStarted = true
                                        currentOnBeginGesture()
                                    }
                                    currentOnMove(elementHit!!.id, canvasDelta.x, canvasDelta.y)
                                    change.consume()
                                }

                                else -> {
                                    // Один палец по пустому месту — пан вида
                                    mode = GestureMode.PAN
                                    userMoved = true
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

                // ---------- Сетка ----------
                // Шаг подстраивается под разрешение холста (ровно делит его),
                // та же формула используется при привязке к сетке —
                // границы элементов совпадают с линиями.
                if (settings.gridEnabled) {
                    val stepX = effectiveGridStep(state.canvas.w, settings.gridSize)
                    val stepY = effectiveGridStep(state.canvas.h, settings.gridSize)
                    val gridStroke = 1f / viewScale
                    var gx = stepX
                    while (gx < canvasW - 0.5f) {
                        drawLine(
                            color = colors.gridLine,
                            start = Offset(gx, 0f),
                            end = Offset(gx, canvasH),
                            strokeWidth = gridStroke,
                        )
                        gx += stepX
                    }
                    var gy = stepY
                    while (gy < canvasH - 0.5f) {
                        drawLine(
                            color = colors.gridLine,
                            start = Offset(0f, gy),
                            end = Offset(canvasW, gy),
                            strokeWidth = gridStroke,
                        )
                        gy += stepY
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
                            // Растягивание вкл: картинка заполняет рамку целиком.
                            // Растягивание выкл: картинка вписывается по центру
                            // рамки без искажений (как ведёт себя текст).
                            val dst = if (settings.imageStretchEnabled) {
                                android.graphics.RectF(el.x, el.y, el.x + el.w, el.y + el.h)
                            } else {
                                val scale = minOf(el.w / bmp.width, el.h / bmp.height)
                                val dw = bmp.width * scale
                                val dh = bmp.height * scale
                                val dx = el.x + (el.w - dw) / 2f
                                val dy = el.y + (el.h - dh) / 2f
                                android.graphics.RectF(dx, dy, dx + dw, dy + dh)
                            }
                            drawIntoCanvasWithAlpha(alpha) {
                                it.nativeCanvas.drawBitmap(bmp, null, dst, null)
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
                            TextElementRenderer.draw(it.nativeCanvas, el, state.fonts, context)
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

                    // Ручка пропорционального масштабирования (правый нижний
                    // угол, снаружи): залитый кружок акцентного цвета с
                    // диагональной стрелкой. Масштабирует изображение и текст
                    // пропорционально, не растягивая.
                    val scaleCenter = Offset(
                        el.x + el.w + SCALE_HANDLE_OFFSET / viewScale,
                        el.y + el.h + SCALE_HANDLE_OFFSET / viewScale,
                    )
                    val scaleR = 11f / viewScale
                    // Линия-связка от угла к ручке
                    drawLine(
                        color = colors.selection.copy(alpha = 0.55f),
                        start = Offset(el.x + el.w, el.y + el.h),
                        end = Offset(
                            scaleCenter.x - scaleR * 0.7f,
                            scaleCenter.y - scaleR * 0.7f,
                        ),
                        strokeWidth = 1.5f / viewScale,
                    )
                    drawCircle(color = colors.selection, radius = scaleR, center = scaleCenter)
                    drawCircle(
                        color = Color.White,
                        radius = scaleR,
                        center = scaleCenter,
                        style = Stroke(width = 1.5f / viewScale),
                    )
                    // Диагональная стрелка внутри
                    val a = scaleR * 0.45f
                    drawLine(
                        color = Color.White,
                        start = Offset(scaleCenter.x - a, scaleCenter.y - a),
                        end = Offset(scaleCenter.x + a, scaleCenter.y + a),
                        strokeWidth = 2f / viewScale,
                    )
                    drawLine(
                        color = Color.White,
                        start = Offset(scaleCenter.x + a, scaleCenter.y + a),
                        end = Offset(scaleCenter.x + a, scaleCenter.y),
                        strokeWidth = 2f / viewScale,
                    )
                    drawLine(
                        color = Color.White,
                        start = Offset(scaleCenter.x + a, scaleCenter.y + a),
                        end = Offset(scaleCenter.x, scaleCenter.y + a),
                        strokeWidth = 2f / viewScale,
                    )
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
