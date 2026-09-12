package com.fallen.studio.ui.screens.editor

import com.fallen.studio.data.model.CanvasElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

/** Pure JVM geometry tests; no Android Canvas, gesture controller or bitmap decoding. */
class CanvasHitTestTest {
    @Test
    fun unrotatedBoundsIncludeEdgesButNotPointsOutside() {
        val element = element("button").copy(x = 20f, y = 30f, w = 120f, h = 60f)
        assertTrue(containsCanvasPoint(element, 20f, 30f))
        assertTrue(containsCanvasPoint(element, 140f, 90f))
        assertTrue(containsCanvasPoint(element, 80f, 60f))
        assertFalse(containsCanvasPoint(element, 19f, 60f))
        assertFalse(containsCanvasPoint(element, 80f, 91f))
    }

    @Test
    fun quarterTurnHitsVisibleAreaOutsideOldAxisAlignedBounds() {
        val element = element("rotated").copy(x = 10f, y = 20f, w = 100f, h = 20f, rotation = 90f)
        // The rotated rectangle is x=50..70, y=-20..80, centred at (60,30).
        assertTrue(containsCanvasPoint(element, 60f, -10f))
        assertTrue(containsCanvasPoint(element, 50f, 80f))
        assertFalse(containsCanvasPoint(element, 20f, 30f))
    }

    @Test
    fun inverseRotationMatchesRendererForArbitraryPositiveAndNegativeAngles() {
        listOf(-405f, -90f, -32f, 0f, 17f, 45f, 135f, 360f, 721f).forEach { angle ->
            val element = element("rotated").copy(x = -300f, y = 80f, w = 180f, h = 56f, rotation = angle)
            val inside = rotateFromLocal(element, 70f, 24f)
            val outside = rotateFromLocal(element, 95f, 0f)
            val corner = rotateFromLocal(element, 90f, 28f)
            assertTrue("inside at $angle", containsCanvasPoint(element, inside.first, inside.second))
            assertTrue("corner at $angle", containsCanvasPoint(element, corner.first, corner.second))
            assertFalse("outside at $angle", containsCanvasPoint(element, outside.first, outside.second))
        }
    }

    @Test
    fun lastDrawnElementWinsWhenZValuesAreEqual() {
        val first = element("first").copy(z = 5)
        val last = element("last").copy(z = 5)
        val elements = listOf(first, last)
        assertEquals(listOf("first", "last"), canvasElementsInDrawOrder(elements).map { it.id })
        assertEquals("last", hitTestCanvasElement(elements, 50f, 50f)?.id)
        assertEquals("first", hitTestCanvasElement(elements.reversed(), 50f, 50f)?.id)
    }

    @Test
    fun greaterZWinsRegardlessOfListPosition() {
        val front = element("front").copy(z = 100)
        val back = element("back").copy(z = -10)
        val middle = element("middle").copy(z = 3)
        val elements = listOf(front, back, middle)
        assertEquals(listOf("back", "middle", "front"), canvasElementsInDrawOrder(elements).map { it.id })
        assertEquals("front", hitTestCanvasElement(elements, 30f, 30f)?.id)
        // Sorting/picking must not mutate authoring order.
        assertEquals(listOf("front", "back", "middle"), elements.map { it.id })
    }

    @Test
    fun aRotatedTopElementDoesNotBlockPointsOutsideItsRotatedBounds() {
        val back = element("back").copy(x = -100f, y = -100f, w = 300f, h = 300f)
        val front = element("front").copy(w = 100f, h = 20f, rotation = 90f, z = 1)
        assertEquals("back", hitTestCanvasElement(listOf(back, front), 10f, 10f)?.id)
        assertEquals("front", hitTestCanvasElement(listOf(back, front), 50f, -20f)?.id)
    }

    @Test
    fun eachImageInstanceIsPickedByItsOwnIdEvenWithSharedAssetSource() {
        val first = element("go-settings").copy(assetId = "same-image", src = "shared", x = 0f)
        val second = element("go-back").copy(assetId = "same-image", src = "shared", x = 140f, locked = true)
        assertEquals("go-settings", hitTestCanvasElement(listOf(first, second), 50f, 50f)?.id)
        // Authoring lock prevents edits, not preview interactions.
        assertEquals("go-back", hitTestCanvasElement(listOf(first, second), 190f, 50f)?.id)
    }

    @Test
    fun emptySpaceAndInvalidGeometryDoNotHit() {
        assertNull(hitTestCanvasElement(emptyList(), 0f, 0f))
        assertNull(hitTestCanvasElement(listOf(element("button")), 200f, 200f))
        assertFalse(containsCanvasPoint(element("zero").copy(w = 0f), 0f, 0f))
        assertFalse(containsCanvasPoint(element("negative").copy(h = -1f), 0f, 0f))
        assertFalse(containsCanvasPoint(element("bad").copy(rotation = Float.NaN), 50f, 50f))
        assertFalse(containsCanvasPoint(element("button"), Float.POSITIVE_INFINITY, 50f))
        assertFalse(containsCanvasPoint(element("button"), 50f, Float.NaN))
    }

    private fun element(id: String) = CanvasElement(id = id, type = "image", w = 100f, h = 100f)

    private fun rotateFromLocal(element: CanvasElement, dx: Float, dy: Float): Pair<Float, Float> {
        val radians = Math.toRadians(element.rotation.toDouble())
        val x = element.x + element.w / 2f + dx * cos(radians) - dy * sin(radians)
        val y = element.y + element.h / 2f + dx * sin(radians) + dy * cos(radians)
        return x.toFloat() to y.toFloat()
    }
}
