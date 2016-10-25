package io.slychat.messenger.services

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import io.slychat.messenger.core.persistence.EventLog
import io.slychat.messenger.core.persistence.LogEventType
import io.slychat.messenger.core.persistence.LogTarget
import io.slychat.messenger.core.randomSecurityEvent
import io.slychat.messenger.testutils.KovenantTestModeRule
import io.slychat.messenger.testutils.thenResolveUnit
import org.assertj.core.api.Assertions.assertThat
import org.junit.ClassRule
import org.junit.Test
import rx.observers.TestSubscriber

class EventLogServiceImplTest {
    companion object {
        @JvmField
        @ClassRule
        val kovenantTestMode = KovenantTestModeRule()
    }

    private val eventLog: EventLog = mock()
    private val eventLogService = EventLogServiceImpl(eventLog)

    private inline fun <reified T : EventLogEvent> eventCollector(): TestSubscriber<T> {
        return eventLogService.eventLogEvents.subclassFilterTestSubscriber()
    }

    @Test
    fun `addEvent should emit an Added event`() {
        val testSubscriber = eventCollector<EventLogEvent.Added>()

        val event = randomSecurityEvent()

        whenever(eventLog.addEvent(any())).thenResolveUnit()

        eventLogService.addEvent(event).get()

        verify(eventLog).addEvent(event)

        assertThat(testSubscriber.onNextEvents).apply {
            `as`("Should emit an added event")
            containsOnly(EventLogEvent.Added(event))
        }
    }

    @Test
    fun `deleteEventRange should emit a Deleted event`() {
        val testSubscriber = eventCollector<EventLogEvent.Deleted>()

        whenever(eventLog.deleteEventRange(any(), any(), any(), any())).thenResolveUnit()

        val types = setOf(LogEventType.SECURITY)
        val target = LogTarget.System
        val startTimestamp = 1L
        val endTimestamp = 2L

        eventLogService.deleteEventRange(types, target, startTimestamp, endTimestamp).get()

        verify(eventLog).deleteEventRange(types, target, startTimestamp, endTimestamp)

        assertThat(testSubscriber.onNextEvents).apply {
            `as`("Should emit a deleted event")
            containsOnly(EventLogEvent.Deleted(types, target, startTimestamp, endTimestamp))
        }
    }

    @Test
    fun `deleteEvents should emit a Deleted event`() {
        val testSubscriber = eventCollector<EventLogEvent.Deleted>()

        whenever(eventLog.deleteEvents(any(), any())).thenResolveUnit()

        val types = setOf(LogEventType.SECURITY)
        val target = LogTarget.System

        eventLogService.deleteEvents(types, target).get()

        verify(eventLog).deleteEvents(types, target)

        assertThat(testSubscriber.onNextEvents).apply {
            `as`("Should emit a deleted event")
            containsOnly(EventLogEvent.Deleted(types, target, -1, -1))
        }
    }
}