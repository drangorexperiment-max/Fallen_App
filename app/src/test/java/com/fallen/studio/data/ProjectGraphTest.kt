package com.fallen.studio.data

import com.fallen.studio.data.model.Asset
import com.fallen.studio.data.model.CanvasElement
import com.fallen.studio.data.model.CanvasSize
import com.fallen.studio.data.model.FallenProject
import com.fallen.studio.data.model.NodeTransition
import com.fallen.studio.data.model.ProjectFont
import com.fallen.studio.data.model.ProjectGraph
import com.fallen.studio.data.model.ProjectSummary
import com.fallen.studio.data.model.ScreenNode
import com.fallen.studio.data.model.TransitionKind
import com.fallen.studio.data.model.toSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure JVM/JUnit4: граф и миграция не требуют Android runtime. */
class ProjectGraphTest {
    @Test
    fun newProjectGetsStableDefaultScreenAndNormalizationIsIdempotent() {
        val project = FallenProject()
        assertEquals(4, project.version)

        val normalized = ProjectGraph.normalize(project)

        assertEquals(listOf(ScreenNode("screen_1", "Screen 1")), normalized.nodes)
        assertEquals("screen_1", normalized.startNodeId)
        assertEquals("screen_1", normalized.activeNodeId)
        assertEquals(CanvasSize(), normalized.canvas)
        assertTrue(normalized.elements.isEmpty())
        assertEquals(0, normalized.counter)
        assertEquals(normalized, ProjectGraph.normalize(normalized))
        assertTrue(project.nodes.isEmpty())
    }

    @Test
    fun newProjectWithoutNodesKeepsItsInitialCanvasAndElements() {
        val project = FallenProject(
            canvas = CanvasSize(640, 480),
            elements = listOf(CanvasElement("el7", "text", x = -7.25f, w = 83.5f)),
        )

        val normalized = ProjectGraph.normalize(project)

        assertEquals(project.canvas, normalized.nodes.single().canvas)
        assertEquals(project.elements, normalized.nodes.single().elements)
        assertEquals(7, normalized.counter)
    }

    @Test
    fun allLegacyVersionsMigrateWithoutChangingGeometryMetadataOrResources() {
        val text = CanvasElement(
            id = "el42", type = "text", name = "Текст исходный", x = -12.25f, y = 81.5f,
            w = 360.75f, h = 96.125f, z = -3, locked = true, opacity = 63.5f,
            rotation = 721.25f, showDimensions = false,
            assetId = "keep-even-on-text", src = "data:text/plain;base64,QQ==",
            text = "Строка 1\nСтрока 2", fontFamily = "font-1", fontSize = 37,
            fontWeight = "700", color = "#ABCDEF", textAlign = "right", verticalAlign = "top",
            strokeWidth = 1.25f, strokeColor = "#102030", lineHeight = 22.5f,
            letterSpacing = 8.75f, shadowEnabled = true, shadowBlur = 3.25f,
            shadowOpacity = 54.5f, shadowX = 18.75f, shadowY = 92.5f, shadowColor = "#445566",
        )
        val image = CanvasElement(
            "image-original", "image", name = "Original image", assetId = "asset-1",
            src = "data:image/png;base64,INLINE", x = 123.75f, y = -9.5f,
            w = 71.25f, h = 48.5f, rotation = -45.5f,
        )
        for (version in 1..3) {
            val legacy = FallenProject(
                version = version, id = "original-project-id", name = "Legacy $version",
                created = "2020-01-02T03:04:05Z", modified = "2021-02-03T04:05:06Z",
                canvas = CanvasSize(2011, 999),
                assets = listOf(
                    Asset("asset-1", "Картинка", "data:image/png;base64,ORIGINAL"),
                    Asset("unused-asset", "Unused", "data:image/jpeg;base64,UNUSED"),
                ),
                elements = listOf(text, image),
                fonts = listOf(
                    ProjectFont("font-1", "Custom", "data:font/otf;base64,FONT", "opentype", "custom.otf"),
                    ProjectFont("unused-font", "Unused", "data:font/ttf;base64,UNUSED"),
                ),
                counter = 4,
            )

            val normalized = ProjectGraph.normalize(legacy)

            assertEquals(
                legacy.copy(
                    version = ProjectGraph.VERSION,
                    nodes = listOf(ScreenNode("screen_1", "Screen 1", legacy.canvas, legacy.elements)),
                    startNodeId = "screen_1", activeNodeId = "screen_1", counter = 42,
                ),
                normalized,
            )
            assertEquals(legacy.elements, normalized.nodes.single().elements)
            assertEquals(normalized, ProjectGraph.normalize(normalized))
            assertEquals(version, legacy.version)
        }
    }

    @Test
    fun activeNodeAloneIsTheSourceOfTheLegacyMirror() {
        val graph = twoNodes().copy(
            canvas = CanvasSize(1, 2),
            elements = listOf(CanvasElement("el999", "image")),
            startNodeId = "a", activeNodeId = "b",
        )

        val normalized = ProjectGraph.normalize(graph)

        assertEquals(graph.nodes, normalized.nodes)
        assertEquals(graph.nodes[1].canvas, normalized.canvas)
        assertEquals(graph.nodes[1].elements, normalized.elements)
        assertEquals(2, normalized.counter) // Не считаем устаревший mirror вторым источником данных.
    }

    @Test
    fun nullStartUsesFirstNodeAndNullActiveUsesStart() {
        val graph = twoNodes()
        val first = ProjectGraph.normalize(graph)
        assertEquals("a", first.startNodeId)
        assertEquals("a", first.activeNodeId)

        val startB = ProjectGraph.normalize(graph.copy(startNodeId = "b"))
        assertEquals("b", startB.startNodeId)
        assertEquals("b", startB.activeNodeId)
        assertEquals(graph.nodes[1].canvas, startB.canvas)

        val activeB = ProjectGraph.normalize(graph.copy(activeNodeId = "b"))
        assertEquals("a", activeB.startNodeId)
        assertEquals("b", activeB.activeNodeId)
    }

    @Test
    fun explicitUnknownStartOrActiveIsRejectedEvenForAnEmptyNewGraph() {
        val invalid = listOf(
            twoNodes().copy(startNodeId = "missing"),
            twoNodes().copy(activeNodeId = "missing"),
            twoNodes().copy(startNodeId = ""),
            FallenProject(startNodeId = "missing"),
            FallenProject(activeNodeId = "missing"),
        )
        invalid.forEach(::assertInvalid)
    }

    @Test
    fun blankAndDuplicateNodeOrLocalElementIdsAreRejected() {
        val graph = twoNodes()
        val a = graph.nodes.first()
        val invalid = listOf(
            graph.copy(nodes = listOf(a, a.copy(name = "Duplicate"))),
            graph.copy(nodes = listOf(a.copy(id = " "))),
            graph.copy(nodes = listOf(a.copy(elements = listOf(a.elements[0], a.elements[0])))),
            graph.copy(nodes = listOf(a.copy(elements = listOf(CanvasElement("", "text"))))),
        )
        invalid.forEach(::assertInvalid)
    }

    @Test
    fun sameElementIdInDifferentNodesIsAllowedAndClickActionsAreInstanceScoped() {
        val nodes = listOf(
            ScreenNode("a", "A", elements = listOf(CanvasElement("shared", "text"))),
            ScreenNode("b", "B", elements = listOf(CanvasElement("shared", "image"))),
        )
        val transitions = listOf(
            NodeTransition("open", "a", "shared", toNodeId = "b"),
            NodeTransition("back", "b", "shared", TransitionKind.BACK),
        )

        val normalized = ProjectGraph.normalize(FallenProject(nodes = nodes, transitions = transitions))

        assertEquals(nodes, normalized.nodes)
        assertEquals(transitions, normalized.transitions)
    }

    @Test
    fun duplicateTransitionIdsAndMultipleActionsOnOneInstanceAreRejected() {
        val graph = twoNodes()
        val open = NodeTransition("click", "a", "el1", toNodeId = "b")
        assertInvalid(graph.copy(transitions = listOf(open, NodeTransition("click", "b", "el2", TransitionKind.BACK))))
        assertInvalid(graph.copy(transitions = listOf(open, NodeTransition("other", "a", "el1", TransitionKind.BACK))))
        assertInvalid(graph.copy(transitions = listOf(open.copy(id = " "))))
    }

    @Test
    fun danglingSourcesElementsAndTargetsAreRejectedRatherThanRemoved() {
        val open = NodeTransition("click", "a", "el1", toNodeId = "b")
        val invalid = listOf(
            open.copy(fromNodeId = "missing"),
            open.copy(elementId = "missing"),
            open.copy(elementId = "el2"), // Существует, но в другом экране.
            open.copy(toNodeId = "missing"),
            open.copy(toNodeId = null),
            open.copy(kind = TransitionKind.BACK), // Target у BACK — ошибка, не повод его стереть.
        )
        invalid.forEach { assertInvalid(twoNodes().copy(transitions = listOf(it))) }
    }

    @Test
    fun cyclesAndSelfTransitionsAreAllowed() {
        val graph = twoNodes()
        val a = graph.nodes[0].copy(elements = graph.nodes[0].elements + CanvasElement("self", "text"))
        val transitions = listOf(
            NodeTransition("ab", "a", "el1", toNodeId = "b"),
            NodeTransition("ba", "b", "el2", toNodeId = "a"),
            NodeTransition("aa", "a", "self", toNodeId = "a"),
        )

        val normalized = ProjectGraph.normalize(graph.copy(nodes = listOf(a, graph.nodes[1]), transitions = transitions))

        assertEquals(transitions, normalized.transitions)
    }

    @Test
    fun counterFloorIncludesInactiveNodesAndKeepsAnAlreadyHigherCounter() {
        val graph = twoNodes().copy(nodes = listOf(
            ScreenNode("a", "A", elements = listOf(CanvasElement("el7", "text"))),
            ScreenNode("b", "B", elements = listOf(
                CanvasElement("el00042", "image"), CanvasElement("custom", "text"),
                CanvasElement("el999-extra", "text"),
            )),
        ), activeNodeId = "a", counter = -2)

        val normalized = ProjectGraph.normalize(graph)

        assertEquals(42, normalized.counter)
        val nextId = "el${normalized.counter + 1}"
        assertFalse(normalized.nodes.any { node -> node.elements.any { it.id == nextId } })
        assertEquals(1000, ProjectGraph.normalize(graph.copy(counter = 1000)).counter)
        assertEquals(0, ProjectGraph.normalize(FallenProject(counter = -1)).counter)
    }

    @Test
    fun exhaustedOrUnrepresentableElementCounterIsRejectedWithoutWrapping() {
        assertInvalid(FallenProject(counter = Int.MAX_VALUE))
        listOf("el2147483647", "el2147483648", "el999999999999999999999").forEach { id ->
            assertInvalid(FallenProject(elements = listOf(CanvasElement(id, "text"))))
        }
    }

    @Test
    fun invalidCanvasUnknownElementTypeAndNonFiniteGeometryAreRejected() {
        listOf(CanvasSize(0, 100), CanvasSize(100, -1)).forEach { canvas ->
            assertInvalid(FallenProject(canvas = canvas))
            assertInvalid(FallenProject(nodes = listOf(ScreenNode("a", "A", canvas))))
        }
        val element = CanvasElement("el1", "text")
        listOf(
            element.copy(type = "video"), element.copy(x = Float.NaN),
            element.copy(rotation = Float.POSITIVE_INFINITY), element.copy(shadowBlur = Float.NEGATIVE_INFINITY),
            element.copy(w = -1f), element.copy(h = -1f),
        ).forEach { assertInvalid(FallenProject(elements = listOf(it))) }
        assertEquals(0f, ProjectGraph.normalize(FallenProject(elements = listOf(element.copy(w = 0f)))).elements.single().w, 0f)
    }

    @Test
    fun missingAndUnusedResourcesNeverCauseElementOrResourceLoss() {
        val project = FallenProject(
            assets = listOf(Asset("unused", "Keep image", "data:image/png;base64,KEEP")),
            fonts = listOf(ProjectFont("unused-font", "Keep font", "data:font/ttf;base64,KEEP")),
            nodes = listOf(ScreenNode("a", "A", elements = listOf(
                CanvasElement("image", "image", assetId = "missing-asset"),
                CanvasElement("text", "text", fontFamily = "Missing custom font", text = "Keep text"),
            ))),
        )

        val normalized = ProjectGraph.normalize(project)

        assertEquals(project.assets, normalized.assets)
        assertEquals(project.fonts, normalized.fonts)
        assertEquals(project.nodes, normalized.nodes)
        assertEquals("missing-asset", normalized.elements[0].assetId)
        assertEquals("Missing custom font", normalized.elements[1].fontFamily)
    }

    @Test
    fun summaryAggregatesEveryNodeAndUsesActiveCanvasWithoutDoubleCountingMirror() {
        val project = twoNodes().copy(
            id = "project", name = "Summary", created = "created", modified = "modified",
            assets = listOf(Asset("asset", "Thumbnail", "data:image/png;base64,THUMB")),
            elements = listOf(CanvasElement("stale", "text")), activeNodeId = "b",
        )

        assertEquals(
            ProjectSummary("project", "Summary", "created", "modified", 2, 1, 1, 900, 600,
                "data:image/png;base64,THUMB", nodeCount = 2),
            project.toSummary(),
        )
        assertEquals(1, FallenProject(version = 1).toSummary().nodeCount)
        assertEquals(1, ProjectSummary("", "", "", null, 0, 0, 0, 1, 1, null).nodeCount)
    }

    @Test
    fun unsupportedVersionsAndGraphDataUnderLegacyVersionAreRejected() {
        listOf(-1, 0, 5, Int.MAX_VALUE).forEach { assertInvalid(FallenProject(version = it)) }
        assertInvalid(twoNodes().copy(version = 3))
        assertInvalid(FallenProject(version = 1, startNodeId = "screen_1"))
        assertInvalid(FallenProject(version = 2, activeNodeId = "screen_1"))
        assertInvalid(FallenProject(version = 3, transitions = listOf(NodeTransition("back", "screen_1", "x", TransitionKind.BACK))))
    }

    private fun twoNodes() = FallenProject(nodes = listOf(
        ScreenNode("a", "A", CanvasSize(640, 480), listOf(CanvasElement("el1", "text"))),
        ScreenNode("b", "B", CanvasSize(900, 600), listOf(CanvasElement("el2", "image"))),
    ))

    private fun assertInvalid(project: FallenProject) {
        assertThrows(IllegalArgumentException::class.java) { ProjectGraph.normalize(project) }
    }
}
