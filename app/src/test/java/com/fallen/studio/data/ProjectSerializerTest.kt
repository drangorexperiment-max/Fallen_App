package com.fallen.studio.data

import com.fallen.studio.data.model.Asset
import com.fallen.studio.data.model.CanvasElement
import com.fallen.studio.data.model.CanvasSize
import com.fallen.studio.data.model.FallenProject
import com.fallen.studio.data.model.NodeTransition
import com.fallen.studio.data.model.ProjectFont
import com.fallen.studio.data.model.ProjectGraph
import com.fallen.studio.data.model.ScreenNode
import com.fallen.studio.data.model.TransitionKind
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure JVM/JUnit4; эти тесты не проверяют Android/Unity runtime или файловое хранилище. */
class ProjectSerializerTest {
    @Test
    fun legacyVersionsPreserveEveryKnownGeometryStyleResourceAndMetadataField() {
        for (version in 1..3) {
            val legacy = legacyProject(version)
            val expected = legacy.copy(
                version = 4,
                nodes = listOf(ScreenNode("screen_1", "Screen 1", legacy.canvas, legacy.elements)),
                startNodeId = "screen_1", activeNodeId = "screen_1", counter = 42,
            )

            val decoded = requireNotNull(ProjectSerializer.decode(legacyJson(version)))

            assertEquals(expected, decoded)
            assertEquals(expected, ProjectSerializer.decode(ProjectSerializer.encode(decoded)))
            assertEquals(expected, ProjectSerializer.decode(ProjectSerializer.encode(legacy)))
        }
    }

    @Test
    fun versionlessLegacyUsesMigrationRatherThanTheNewModelVersionDefault() {
        val raw = legacyJson(3).replace("\"version\": 3,", "")

        val decoded = requireNotNull(ProjectSerializer.decode(raw))

        assertEquals(4, decoded.version)
        assertEquals("screen_1", decoded.nodes.single().id)
        assertEquals(legacyProject(3).elements, decoded.elements)
        assertEquals(legacyProject(3).assets, decoded.assets)
        assertEquals(legacyProject(3).fonts, decoded.fonts)
    }

    @Test
    fun minimalLegacyCanvasOrElementsCanUseSchemaDefaults() {
        listOf("""{"canvas":{}}""", """{"elements":[]}""", """{"version":1,"canvas":{"w":320}}""")
            .forEach { raw ->
                val decoded = requireNotNull(ProjectSerializer.decode(raw))
                assertEquals(4, decoded.version)
                assertEquals(1, decoded.nodes.size)
                assertEquals(1080, decoded.canvas.h)
            }
        val elementDefaults = requireNotNull(ProjectSerializer.decode(
            """{"elements":[{"id":"el8","type":"text"}]}""",
        ))
        assertEquals(listOf(CanvasElement("el8", "text")), elementDefaults.elements)
        assertEquals(8, elementDefaults.counter)

        val sizedElement = requireNotNull(ProjectSerializer.decode(
            """{"elements":[{"id":"image","type":"image","w":24.5,"h":12.25}]}""",
        ))
        assertEquals(CanvasElement("image", "image", w = 24.5f, h = 12.25f), sizedElement.elements.single())
    }

    @Test
    fun emptyArbitraryMalformedAndNonObjectDocumentsAreNotProjects() {
        listOf(
            "", " ", "{}", "[]", "null", "true", "123", "\"project\"",
            "{", "{broken}", """{"name":"not a project"}""", """{"version":3}""",
            """{"assets":[],"fonts":[],"counter":0}""", """{"other":{"canvas":{}}}""",
        ).forEach(::assertRejected)
    }

    @Test
    fun encodeNewProjectProducesCanonicalV4ThatDecodeCanRead() {
        val encoded = ProjectSerializer.encode(FallenProject(name = "New"))
        val root = ProjectSerializer.json.parseToJsonElement(encoded).jsonObject

        assertEquals(JsonPrimitive(4), root["version"])
        assertNotNull(root["nodes"])
        assertEquals(JsonPrimitive("screen_1"), root["startNodeId"])
        assertEquals(JsonPrimitive("screen_1"), root["activeNodeId"])
        val decoded = requireNotNull(ProjectSerializer.decode(encoded))
        assertEquals(ProjectGraph.normalize(FallenProject(name = "New")), decoded)
        assertEquals(encoded, ProjectSerializer.encode(decoded))
    }

    @Test
    fun multiNodeRoundTripIncludesTransitionsResourcesActiveMirrorAndGlobalCounter() {
        val project = twoNodes().copy(
            id = "project-original", name = "Два экрана", created = "created", modified = "modified",
            canvas = CanvasSize(1, 1), elements = listOf(CanvasElement("stale", "image")),
            assets = listOf(Asset("unused", "Retain", "data:image/png;base64,ASSET")),
            fonts = listOf(ProjectFont("unused-font", "Retain", "data:font/ttf;base64,FONT")),
            startNodeId = "a", activeNodeId = "b", counter = 1,
            transitions = listOf(
                NodeTransition("open", "a", "el1", toNodeId = "b"),
                NodeTransition("back", "b", "el42", TransitionKind.BACK),
            ),
        )

        val encoded = ProjectSerializer.encode(project)
        val decoded = requireNotNull(ProjectSerializer.decode(encoded))

        assertEquals(ProjectGraph.normalize(project), decoded)
        assertEquals(project.nodes, decoded.nodes)
        assertEquals(project.nodes[1].elements, decoded.elements)
        assertEquals(project.nodes[1].canvas, decoded.canvas)
        assertEquals(project.transitions, decoded.transitions)
        assertEquals(42, decoded.counter)
        assertEquals(encoded, ProjectSerializer.encode(decoded))
    }

    @Test
    fun decodeIgnoresStaleMirrorButNeverMergesItIntoTheActiveNode() {
        val project = twoNodes().copy(
            elements = listOf(CanvasElement("el999", "text")),
            canvas = CanvasSize(7, 8), activeNodeId = "b",
        )

        val decoded = requireNotNull(ProjectSerializer.decode(rawProject(project)))

        assertEquals(project.nodes, decoded.nodes)
        assertEquals(project.nodes[1].elements, decoded.elements)
        assertEquals(project.nodes[1].canvas, decoded.canvas)
        assertEquals(42, decoded.counter)
    }

    @Test
    fun futureAndUnsupportedVersionsAreRejectedAndCannotBeEncodedAsV4() {
        listOf(0, -1, 5, Int.MAX_VALUE).forEach { version ->
            val project = twoNodes().copy(version = version)
            assertRejected(rawProject(project))
            assertThrows(IllegalArgumentException::class.java) { ProjectSerializer.encode(project) }
        }
        listOf(
            """{"version":"4","nodes":[{"id":"a","name":"A"}]}""",
            """{"version":4.5,"canvas":{}}""",
            """{"version":2147483648,"canvas":{}}""",
            """{"version":null,"canvas":{}}""",
            """{"version":true,"canvas":{}}""",
        ).forEach(::assertRejected)
    }

    @Test
    fun importedV4RequiresNodesEvenWhenALegacyMirrorExists() {
        listOf(
            """{"version":4,"canvas":{},"elements":[]}""",
            """{"version":4,"nodes":[],"canvas":{}}""",
            """{"version":4,"nodes":null}""",
            """{"version":4,"nodes":{}}""",
            """{"version":4,"nodes":[{}]}""",
            """{"version":4,"nodes":[{"id":"a"}]}""",
        ).forEach(::assertRejected)
        val minimal = requireNotNull(ProjectSerializer.decode(
            """{"version":4,"nodes":[{"id":"a","name":"A"}]}""",
        ))
        assertEquals(listOf(ScreenNode("a", "A")), minimal.nodes)
        assertEquals("a", minimal.startNodeId)
        assertEquals("a", minimal.activeNodeId)
    }

    @Test
    fun invalidDuplicateAndDanglingNodesElementsOrActionsRejectTheWholeDocument() {
        val project = twoNodes()
        val a = project.nodes[0]
        val open = NodeTransition("open", "a", "el1", toNodeId = "b")
        val invalid = listOf(
            project.copy(nodes = listOf(a, a.copy(name = "duplicate"))),
            project.copy(nodes = listOf(a.copy(id = ""))),
            project.copy(nodes = listOf(a.copy(elements = a.elements + a.elements))),
            project.copy(startNodeId = "missing"), project.copy(activeNodeId = "missing"),
            project.copy(nodes = listOf(a.copy(canvas = CanvasSize(0, 1)))),
            project.copy(transitions = listOf(open.copy(fromNodeId = "missing"))),
            project.copy(transitions = listOf(open.copy(elementId = "el42"))),
            project.copy(transitions = listOf(open.copy(toNodeId = "missing"))),
            project.copy(transitions = listOf(open.copy(toNodeId = null))),
            project.copy(transitions = listOf(open.copy(kind = TransitionKind.BACK))),
            project.copy(transitions = listOf(open.copy(id = ""))),
            project.copy(transitions = listOf(open, NodeTransition("open", "b", "el42", TransitionKind.BACK))),
            project.copy(transitions = listOf(open, NodeTransition("other", "a", "el1", TransitionKind.BACK))),
        )
        invalid.forEach { value ->
            assertRejected(rawProject(value))
            assertThrows(IllegalArgumentException::class.java) { ProjectSerializer.encode(value) }
        }
    }

    @Test
    fun unknownNullAndWronglyTypedEnumValuesAreNotCoercedToOpenScreen() {
        val valid = rawProject(twoNodes().copy(transitions = listOf(NodeTransition("open", "a", "el1", toNodeId = "b"))))
        assertTrue(valid.contains("\"kind\":\"OPEN_SCREEN\""))
        listOf("\"LAUNCH\"", "\"open_screen\"", "null", "1", "true", "{}", "[]").forEach { kind ->
            assertRejected(valid.replace("\"kind\":\"OPEN_SCREEN\"", "\"kind\":$kind"))
        }
        val defaultKind = requireNotNull(ProjectSerializer.decode(valid.replace("\"kind\":\"OPEN_SCREEN\",", "")))
        assertEquals(TransitionKind.OPEN_SCREEN, defaultKind.transitions.single().kind)
    }

    @Test
    fun invalidKnownJsonTypesAndUnsupportedElementTypesAreNotCoerced() {
        listOf(
            """{"canvas":[]}""", """{"canvas":{"w":"320"}}""",
            """{"canvas":{"w":3.5}}""", """{"canvas":{"w":0}}""",
            """{"canvas":{},"name":123}""", """{"canvas":{},"id":true}""",
            """{"canvas":{},"created":false}""", """{"canvas":{},"counter":"12"}""",
            """{"canvas":{},"assets":{}}""", """{"canvas":{},"assets":[{}]}""",
            """{"canvas":{},"assets":[{"id":"a","name":"A","src":7}]}""",
            """{"canvas":{},"fonts":[{"id":"f","name":"F","src":false}]}""",
            """{"elements":[{"id":1,"type":"text"}]}""",
            """{"elements":[{"id":"e","type":"video"}]}""",
            """{"elements":[{"id":"e","type":true}]}""",
            """{"elements":[{"id":"e","type":"text","x":"12.5"}]}""",
            """{"elements":[{"id":"e","type":"text","locked":"false"}]}""",
            """{"elements":[{"id":"e","type":"text","text":123}]}""",
            """{"elements":[{"id":"e","type":"text","fontSize":"30"}]}""",
            """{"elements":[{"id":"e","type":"text","w":-2}]}""",
            """{"elements":[{"id":"e","type":"text","x":NaN}]}""",
            """{"version":4,"nodes":[{"id":42,"name":"A"}]}""",
            """{"version":4,"nodes":[{"id":"a","name":"A"}],"activeNodeId":7}""",
        ).forEach(::assertRejected)
    }

    @Test
    fun nullNonNullableValuesDoNotBecomeDefaultsButNullableFieldsRemainValid() {
        listOf(
            """{"canvas":null}""", """{"elements":null}""", """{"canvas":{"w":null}}""",
            """{"canvas":{},"name":null}""", """{"canvas":{},"assets":null}""",
            """{"canvas":{},"fonts":null}""", """{"canvas":{},"counter":null}""",
            """{"elements":[{"id":"e","type":null}]}""",
            """{"elements":[{"id":"e","type":"text","showDimensions":null}]}""",
        ).forEach(::assertRejected)
        val nullable = requireNotNull(ProjectSerializer.decode(
            """{"canvas":{},"id":null,"modified":null,"elements":[{"id":"e","type":"text","fontSize":null,"src":null}]}""",
        ))
        assertNull(nullable.id)
        assertNull(nullable.modified)
        assertEquals(listOf(CanvasElement("e", "text")), nullable.elements)
    }

    @Test
    fun cyclesAndDuplicateElementIdsAcrossNodesSurviveDecodeAndEncode() {
        val project = FallenProject(nodes = listOf(
            ScreenNode("a", "A", elements = listOf(CanvasElement("shared", "text"))),
            ScreenNode("b", "B", elements = listOf(CanvasElement("shared", "image"))),
        ), transitions = listOf(
            NodeTransition("ab", "a", "shared", toNodeId = "b"),
            NodeTransition("ba", "b", "shared", toNodeId = "a"),
        ))

        val decoded = requireNotNull(ProjectSerializer.decode(rawProject(project)))

        assertEquals(project.nodes, decoded.nodes)
        assertEquals(project.transitions, decoded.transitions)
        assertEquals(decoded, ProjectSerializer.decode(ProjectSerializer.encode(decoded)))
    }

    @Test
    fun missingResourceReferencesAndUnusedResourcePayloadsRoundTripWithoutLoss() {
        val project = FallenProject(
            assets = listOf(Asset("unused", "Unused", "data:image/png;base64,KEEP")),
            fonts = listOf(ProjectFont("unused-font", "Unused", "data:font/otf;base64,KEEP", "opentype", "keep.otf")),
            nodes = listOf(ScreenNode("a", "A", elements = listOf(
                CanvasElement("image", "image", assetId = "absent", src = null),
                CanvasElement("inline", "image", src = "data:image/png;base64,INLINE"),
                CanvasElement("text", "text", fontFamily = "absent font", text = "Keep me"),
            ))),
        )

        val decoded = requireNotNull(ProjectSerializer.decode(ProjectSerializer.encode(project)))

        assertEquals(project.nodes, decoded.nodes)
        assertEquals(project.assets, decoded.assets)
        assertEquals(project.fonts, decoded.fonts)
        assertEquals("absent", decoded.elements.first().assetId)
    }

    @Test
    fun legacyGraphDataIsRejectedInsteadOfDiscardedDuringMigration() {
        assertRejected(rawProject(twoNodes().copy(version = 3)))
        assertRejected("""{"canvas":{},"nodes":[{"id":"a","name":"A"}]}""")
        assertRejected("""{"version":2,"canvas":{},"startNodeId":"a"}""")
    }

    @Test
    fun unknownFieldsInSupportedLegacyFilesDoNotPreventKnownDataMigration() {
        val raw = """{"version":2,"canvas":{"w":500,"legacySetting":true},"elements":[{"id":"e","type":"text","custom":42}],"extension":{"x":1}}"""

        val decoded = requireNotNull(ProjectSerializer.decode(raw))

        assertEquals(CanvasSize(500, 1080), decoded.canvas)
        assertEquals(listOf(CanvasElement("e", "text")), decoded.elements)
    }

    @Test
    fun exhaustedCounterIsRejectedRatherThanWrappingOrRenamingElements() {
        listOf(
            """{"canvas":{},"counter":2147483647}""",
            """{"elements":[{"id":"el2147483648","type":"text"}]}""",
        ).forEach(::assertRejected)
    }

    private fun assertRejected(raw: String) {
        assertNull("Expected invalid project: $raw", ProjectSerializer.decode(raw))
    }

    /** Обход normalize только для конструирования намеренно невалидных входных файлов. */
    private fun rawProject(project: FallenProject): String =
        ProjectSerializer.json.encodeToString(FallenProject.serializer(), project)

    private fun twoNodes() = FallenProject(nodes = listOf(
        ScreenNode("a", "A", CanvasSize(640, 480), listOf(CanvasElement("el1", "text"))),
        ScreenNode("b", "B", CanvasSize(900, 600), listOf(CanvasElement("el42", "image"))),
    ))

    private fun legacyProject(version: Int) = FallenProject(
        version = version, id = "legacy-id", name = "Legacy документ", created = "created-original",
        modified = "modified-original", canvas = CanvasSize(2011, 999), counter = 3,
        assets = listOf(
            Asset("asset", "Картинка", "data:image/png;base64,ORIGINAL"),
            Asset("unused", "Unused image", "data:image/jpeg;base64,UNUSED"),
        ),
        fonts = listOf(
            ProjectFont("font", "Custom font", "data:font/otf;base64,ORIGINAL", "opentype", "original.otf"),
            ProjectFont("unused-font", "Unused font", "data:font/ttf;base64,UNUSED"),
        ),
        elements = listOf(
            CanvasElement(
                id = "el42", type = "text", name = "Text original", x = -12.25f, y = 81.5f,
                w = 360.75f, h = 96.125f, z = -3, locked = true, opacity = 63.5f,
                rotation = 721.25f, showDimensions = false, assetId = "retain-on-text",
                src = "data:text/plain;base64,QQ==", text = "Line 1\nLine 2", fontFamily = "font",
                fontSize = 37, fontWeight = "700", color = "#ABCDEF", textAlign = "right", verticalAlign = "top",
                strokeWidth = 1.25f, strokeColor = "#102030", lineHeight = 22.5f,
                letterSpacing = 8.75f, shadowEnabled = true, shadowBlur = 3.25f,
                shadowOpacity = 54.5f, shadowX = 18.75f, shadowY = 92.5f, shadowColor = "#445566",
            ),
            CanvasElement("image-original", "image", assetId = "asset", src = "data:image/png;base64,INLINE",
                x = 123.75f, y = -9.5f, w = 71.25f, h = 48.5f, rotation = -45.5f),
        ),
    )

    private fun legacyJson(version: Int) = """
        {
          "version": $version,
          "id": "legacy-id", "name": "Legacy документ", "created": "created-original",
          "modified": "modified-original", "canvas": {"w":2011,"h":999}, "counter":3,
          "assets": [
            {"id":"asset","name":"Картинка","src":"data:image/png;base64,ORIGINAL"},
            {"id":"unused","name":"Unused image","src":"data:image/jpeg;base64,UNUSED"}
          ],
          "fonts": [
            {"id":"font","name":"Custom font","src":"data:font/otf;base64,ORIGINAL","format":"opentype","fileName":"original.otf"},
            {"id":"unused-font","name":"Unused font","src":"data:font/ttf;base64,UNUSED"}
          ],
          "elements": [
            {
              "id":"el42","type":"text","name":"Text original","x":-12.25,"y":81.5,
              "w":360.75,"h":96.125,"z":-3,"locked":true,"opacity":63.5,"rotation":721.25,
              "showDimensions":false,"assetId":"retain-on-text","src":"data:text/plain;base64,QQ==",
              "text":"Line 1\nLine 2","fontFamily":"font","fontSize":37,"fontWeight":"700",
              "color":"#ABCDEF","textAlign":"right","verticalAlign":"top","strokeWidth":1.25,
              "strokeColor":"#102030","lineHeight":22.5,"letterSpacing":8.75,"shadowEnabled":true,
              "shadowBlur":3.25,"shadowOpacity":54.5,"shadowX":18.75,"shadowY":92.5,"shadowColor":"#445566"
            },
            {"id":"image-original","type":"image","assetId":"asset","src":"data:image/png;base64,INLINE",
             "x":123.75,"y":-9.5,"w":71.25,"h":48.5,"rotation":-45.5}
          ]
        }
    """.trimIndent()
}
