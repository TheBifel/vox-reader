package com.bifel.voxreader.application.reader

import com.bifel.voxreader.domain.model.NarratorProfile
import com.bifel.voxreader.domain.model.SpeechEngineMode
import com.bifel.voxreader.domain.model.SpeechPhrase
import com.bifel.voxreader.domain.speech.PlaybackCommand
import com.bifel.voxreader.domain.speech.PlaybackNotificationController
import com.bifel.voxreader.domain.speech.SpeechBackendAvailability
import com.bifel.voxreader.domain.speech.SpeechEngine
import com.bifel.voxreader.domain.speech.SpeechEngineRegistry
import com.bifel.voxreader.domain.speech.SpeechEvent
import com.bifel.voxreader.domain.speech.SpeechQueueMode
import com.bifel.voxreader.domain.speech.SpeechRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class ReaderPlaybackControllerTest {
    private lateinit var scope: CoroutineScope
    private lateinit var engines: FakeRegistry
    private lateinit var controller: ReaderPlaybackController

    @Before
    fun setUp() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        engines = FakeRegistry()
        controller = ReaderPlaybackController(engines, FakeNotification(), scope)
    }

    @After
    fun tearDown() {
        controller.close()
        scope.cancel()
    }

    @Test
    fun `play starts at first visible phrase and fills lookahead`() = runBlocking {
        controller.updateContext(context(firstVisibleOffset = 12))

        controller.play()
        yield()

        val queued = engines.engine(SpeechEngineMode.PROCEDURAL).requests
        assertEquals(5, queued.size)
        assertEquals("phrase 1", queued.first().text)
        assertEquals(SpeechQueueMode.FLUSH, queued.first().queueMode)
        assertEquals(PlaybackStatus.PREPARING, controller.state.value.status)
    }

    @Test
    fun `play waits for background phrase preparation`() = runBlocking {
        controller.updateContext(context(phrases = emptyList()))
        controller.play()
        assertEquals(PlaybackStatus.PREPARING, controller.state.value.status)

        controller.updateContext(context())
        yield()

        assertEquals(5, engines.engine(SpeechEngineMode.PROCEDURAL).requests.size)
    }

    @Test
    fun `events from inactive engine cannot change highlight`() = runBlocking {
        controller.updateContext(context())
        controller.play()
        yield()
        val before = controller.state.value

        engines.engine(SpeechEngineMode.LOCAL_GENERATIVE).emit(SpeechEvent.Started("foreign"))
        yield()

        assertEquals(before, controller.state.value)
    }

    private fun context(
        firstVisibleOffset: Int = 0,
        phrases: List<SpeechPhrase> = List(8) { index ->
            SpeechPhrase(index, index * 10, index * 10 + 8, "phrase $index")
        },
    ) = PlaybackContext(
        bookId = "book",
        bookTitle = "Book",
        phrases = phrases,
        characters = listOf(NarratorProfile),
        firstVisibleOffset = firstVisibleOffset,
        controlsEnabled = true,
    )
}

private class FakeRegistry : SpeechEngineRegistry {
    private val engines = SpeechEngineMode.entries.associateWith { FakeEngine() }

    override fun engine(mode: SpeechEngineMode): FakeEngine = engines.getValue(mode)
    override fun availability(mode: SpeechEngineMode) = SpeechBackendAvailability(true)
    override fun stopAll() = engines.values.forEach(SpeechEngine::stop)
}

private class FakeEngine : SpeechEngine {
    private val mutableEvents = MutableSharedFlow<SpeechEvent>(extraBufferCapacity = 16)
    override val events: SharedFlow<SpeechEvent> = mutableEvents
    val requests = mutableListOf<QueuedRequest>()

    override suspend fun awaitReady() = Unit

    override fun enqueue(request: SpeechRequest, queueMode: SpeechQueueMode) {
        requests += QueuedRequest(request.text, queueMode)
    }

    override fun stop() = Unit

    fun emit(event: SpeechEvent) {
        mutableEvents.tryEmit(event)
    }
}

private data class QueuedRequest(val text: String, val queueMode: SpeechQueueMode)

private class FakeNotification : PlaybackNotificationController {
    override val commands: Flow<PlaybackCommand> = MutableSharedFlow()
    override fun update(title: String, playing: Boolean) = Unit
    override fun stop() = Unit
}
