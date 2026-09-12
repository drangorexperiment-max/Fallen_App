package com.fallen.studio.ui.screens.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import com.fallen.studio.data.model.NodeTransition
import com.fallen.studio.data.model.ScreenNode
import com.fallen.studio.data.model.TransitionKind
import com.fallen.studio.ui.theme.FallenTheme

/**
 * A measured, vertically flowing graph. Cards retain their natural height (including
 * long user names and transition descriptions), so neither labels nor edges depend
 * on a fixed card size. The caller supplies the scrolling viewport.
 *
 * Solid arrows join real OPEN_SCREEN endpoints. A dashed return hook means BACK;
 * its destination is deliberately not invented, because it comes from preview history.
 */
@Composable
internal fun NodesWorkspace(
    nodes: List<ScreenNode>,
    transitions: List<NodeTransition>,
    showConnections: Boolean,
    modifier: Modifier = Modifier,
    nodeContent: @Composable (ScreenNode, Modifier) -> Unit,
) {
    if (!showConnections) {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            nodes.forEach { node ->
                key(node.id) { nodeContent(node, Modifier.fillMaxWidth()) }
            }
        }
        return
    }

    val colors = FallenTheme.colors
    val coordinates = remember { mutableMapOf<String, LayoutCoordinates>() }
    var graphCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var bounds by remember { mutableStateOf<Map<String, Rect>>(emptyMap()) }

    fun updateBounds() {
        val graph = graphCoordinates?.takeIf { it.isAttached } ?: return
        val next = nodes.mapNotNull { node ->
            val card = coordinates[node.id]?.takeIf { it.isAttached } ?: return@mapNotNull null
            node.id to graph.localBoundingBoxOf(card, clipBounds = false)
        }.toMap()
        if (next != bounds) bounds = next
        // Deleted cards must not keep their LayoutCoordinates alive.
        coordinates.keys.retainAll(nodes.map { it.id }.toSet())
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned {
                graphCoordinates = it
                updateBounds()
            },
    ) {
        Canvas(Modifier.matchParentSize()) {
            // Space for connection lanes is reserved to the right of every card.
            // Each lane stays inside this Canvas even on a compact phone.
            transitions.forEachIndexed { index, transition ->
                val sourceNode = nodes.firstOrNull { it.id == transition.fromNodeId }
                    ?: return@forEachIndexed
                if (sourceNode.elements.none { it.id == transition.elementId }) return@forEachIndexed
                val source = bounds[transition.fromNodeId] ?: return@forEachIndexed
                val outgoing = transitions.filter { it.fromNodeId == transition.fromNodeId }
                val slot = outgoing.indexOfFirst { it.id == transition.id }.coerceAtLeast(0)
                val portFraction = (slot + 1f) / (outgoing.size + 1f)
                val start = Offset(source.right, source.top + source.height * portFraction)
                val laneX = (source.right + (20 + index % 4 * 10).dp.toPx())
                    .coerceAtMost(size.width - 7.dp.toPx())
                val stroke = 1.5.dp.toPx()

                when (transition.kind) {
                    TransitionKind.OPEN_SCREEN -> {
                        val targetId = transition.toNodeId ?: return@forEachIndexed
                        val target = bounds[targetId] ?: return@forEachIndexed
                        val end = if (transition.toNodeId == transition.fromNodeId) {
                            // A real self-link, not a line to a fictitious neighbour.
                            Offset(target.right, (start.y - 28.dp.toPx()).coerceAtLeast(target.top + 8.dp.toPx()))
                        } else {
                            Offset(target.right, target.center.y)
                        }
                        val path = Path().apply {
                            moveTo(start.x, start.y)
                            cubicTo(laneX, start.y, laneX, start.y, laneX, (start.y + end.y) / 2f)
                            cubicTo(laneX, end.y, laneX, end.y, end.x, end.y)
                        }
                        drawPath(path, colors.selection.copy(alpha = 0.72f), style = Stroke(stroke))
                        drawCircle(colors.selection, 3.dp.toPx(), start)
                        drawLeftArrow(end, colors.selection, stroke)
                    }
                    TransitionKind.BACK -> {
                        val end = Offset(source.right + 9.dp.toPx(), start.y - 16.dp.toPx())
                        val path = Path().apply {
                            moveTo(start.x, start.y)
                            lineTo(laneX, start.y)
                            lineTo(laneX, end.y)
                            lineTo(end.x, end.y)
                        }
                        drawPath(
                            path,
                            colors.textSecondary,
                            style = Stroke(
                                width = stroke,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
                            ),
                        )
                        drawCircle(colors.textSecondary, 3.dp.toPx(), start)
                        drawLeftArrow(end, colors.textSecondary, stroke)
                    }
                }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 64.dp, top = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            nodes.forEach { node ->
                key(node.id) {
                    nodeContent(
                        node,
                        Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned {
                                coordinates[node.id] = it
                                updateBounds()
                            },
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawLeftArrow(tip: Offset, color: Color, strokeWidth: Float) {
    val wing = 5.dp.toPx()
    drawLine(color, tip, Offset(tip.x + wing, tip.y - wing), strokeWidth)
    drawLine(color, tip, Offset(tip.x + wing, tip.y + wing), strokeWidth)
}
