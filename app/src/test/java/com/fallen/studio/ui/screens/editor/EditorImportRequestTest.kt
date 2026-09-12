package com.fallen.studio.ui.screens.editor

import com.fallen.studio.data.model.CanvasSize
import com.fallen.studio.data.model.FallenProject
import com.fallen.studio.data.model.ScreenNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The VM's actual pending-request helper; pure JVM tests, not an Android picker/lifecycle test. */
class EditorImportRequestTest {
    @Test
    fun beginCapturesOneRequestAndCompleteConsumesItExactlyOnce() {
        val state = fixture()
        val request = EditorImportRequest()

        assertNull(request.complete(state, hasResult = true))
        assertTrue(request.begin(state))
        assertFalse(request.begin(state))
        assertEquals(state.authoringToken(), request.complete(state, hasResult = true))
        assertNull(request.complete(state, hasResult = true))
        assertTrue(request.begin(state))
    }

    @Test
    fun previewRoundTripBetweenLaunchAndUriDeliveryInvalidatesThePendingToken() {
        val state = fixture()
        val request = EditorImportRequest()
        assertTrue(request.begin(state)) // Immediately before launcher.launch, not in the callback.
        val stopped = state.withPreviewStarted().withPreviewStopped()

        assertEquals(state.sessionId, stopped.sessionId)
        assertNotEquals(state.authoringEpoch, stopped.authoringEpoch)
        assertFalse(request.begin(stopped)) // Never replace a token still awaiting its callback.
        assertNull(request.complete(stopped, hasResult = true))
        assertNull(request.complete(stopped, hasResult = true))
        assertTrue(request.begin(stopped))
        assertEquals(stopped.authoringToken(), request.complete(stopped, hasResult = true))
    }

    @Test
    fun sessionReplacementBetweenLaunchAndUriDeliveryCannotBorrowANewToken() {
        val state = fixture()
        val request = EditorImportRequest()
        assertTrue(request.begin(state))
        val replacement = fixture(sessionId = state.sessionId + 1)

        assertEquals(state.authoringEpoch, replacement.authoringEpoch)
        assertFalse(request.begin(replacement))
        assertNull(request.complete(replacement, hasResult = true))
        assertTrue(request.begin(replacement))
        assertEquals(replacement.authoringToken(), request.complete(replacement, hasResult = true))
    }

    @Test
    fun cancellationConsumesPendingEvenInPreviewOrAnInvalidatedSession() {
        val state = fixture()
        val cancellationStates = listOf(
            state,
            state.withPreviewStarted(),
            state.withPreviewStarted().withPreviewStopped(),
            fixture(sessionId = state.sessionId + 1),
            state.copy(isExitRequested = true),
        )
        cancellationStates.forEach { cancelledAt ->
            val request = EditorImportRequest()
            assertTrue(request.begin(state))
            assertNull(request.complete(cancelledAt, hasResult = false))
            assertNull(request.complete(state, hasResult = true))
            assertTrue(request.begin(state))
            assertEquals(state.authoringToken(), request.complete(state, hasResult = true))
        }
    }

    @Test
    fun beginIsRejectedWhileProjectCannotBeEditedWithoutReservingASlot() {
        val state = fixture()
        val unavailable = listOf(
            EditorState(),
            state.copy(isSessionReady = false),
            state.copy(isLoading = true),
            state.withPreviewStarted(),
            state.copy(isExitRequested = true),
        )
        unavailable.forEach { blocked ->
            val request = EditorImportRequest()
            assertFalse(request.begin(blocked))
            assertNull(request.complete(state, hasResult = true))
            assertTrue(request.begin(state))
        }
    }

    @Test
    fun completeRejectsAndConsumesAResultWhileProjectCannotBeEdited() {
        val state = fixture()
        val unavailable = listOf(
            state.copy(isSessionReady = false),
            state.copy(isLoading = true),
            state.withPreviewStarted(),
            state.copy(isExitRequested = true),
        )
        unavailable.forEach { blocked ->
            val request = EditorImportRequest()
            assertTrue(request.begin(state))
            assertNull(request.complete(blocked, hasResult = true))
            assertNull(request.complete(state, hasResult = true))
            assertTrue(request.begin(state))
        }
    }

    @Test
    fun sharedResourceImportAllowsOrdinaryNodeSwitchAndProjectEditsDuringPicker() {
        val state = fixture()
        val request = EditorImportRequest()
        assertTrue(request.begin(state))
        val edited = state.withSelectedNode("details").withRenamedNode("home", "Renamed node")

        assertNotEquals(state.activeNodeId, edited.activeNodeId)
        assertTrue(edited.revision > state.revision)
        assertEquals(state.authoringToken(), request.complete(edited, hasResult = true))
    }

    @Test
    fun completedTokenStillRejectsPreviewOrSessionChangesBeforeIoApplication() {
        val state = fixture()
        val request = EditorImportRequest()
        assertTrue(request.begin(state))
        val token = request.complete(state, hasResult = true)!!

        assertTrue(state.acceptsAuthoringResult(token))
        assertFalse(state.withPreviewStarted().withPreviewStopped().acceptsAuthoringResult(token))
        assertFalse(fixture(sessionId = state.sessionId + 1).acceptsAuthoringResult(token))
        assertNull(request.complete(state, hasResult = true))
    }

    @Test
    fun imageAndFontSlotsDoNotOverwriteOrCancelEachOther() {
        val state = fixture()
        val image = EditorImportRequest()
        val font = EditorImportRequest()

        assertTrue(image.begin(state))
        assertTrue(font.begin(state))
        assertFalse(image.begin(state))
        assertFalse(font.begin(state))
        assertNull(image.complete(state, hasResult = false))
        assertTrue(image.begin(state))
        assertFalse(font.begin(state))
        assertEquals(state.authoringToken(), font.complete(state, hasResult = true))
        assertEquals(state.authoringToken(), image.complete(state, hasResult = true))
    }

    private fun fixture(sessionId: Long = 7): EditorState = FallenProject(
        nodes = listOf(
            ScreenNode("home", "Home", CanvasSize(400, 800), emptyList()),
            ScreenNode("details", "Details", CanvasSize(800, 400), emptyList()),
        ),
        activeNodeId = "home",
        startNodeId = "home",
    ).toEditorState(sessionId)
}
