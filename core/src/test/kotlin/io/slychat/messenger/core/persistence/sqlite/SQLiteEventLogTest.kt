package io.slychat.messenger.core.persistence.sqlite

import io.slychat.messenger.core.persistence.LogEvent
import io.slychat.messenger.core.persistence.LogEventType
import io.slychat.messenger.core.persistence.LogTarget
import io.slychat.messenger.core.persistence.SecurityEventData
import io.slychat.messenger.core.randomSlyAddress
import io.slychat.messenger.core.randomUserConversationId
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test

class SQLiteEventLogTest {
    companion object {
        @JvmStatic
        @BeforeClass
        fun loadLibrary() {
            SQLiteEventLogTest::class.java.loadSQLiteLibraryFromResources()
        }
    }

    lateinit var persistenceManager: SQLitePersistenceManager
    lateinit var eventLog: SQLiteEventLog

    private var currentTime = 0L

    @Before
    fun before() {
        persistenceManager = SQLitePersistenceManager(null, null)
        persistenceManager.init()

        eventLog = SQLiteEventLog(persistenceManager)

        currentTime = 0L
    }

    @After
    fun after() {
        persistenceManager.shutdown()
    }

    private fun randomSecurityEvent(target: LogTarget? = null): LogEvent.Security {
        return LogEvent.Security(
            target ?: LogTarget.Conversation(randomUserConversationId()),
            ++currentTime,
            SecurityEventData.InvalidKey(randomSlyAddress(), "invalid signature on device key!")
        )
    }

    private fun randomSecurityEvents(n: Int = 2, target: LogTarget? = null): List<LogEvent.Security> {
        return (1..n).map { randomSecurityEvent(target) }
    }

    private fun addEvents(events: Collection<LogEvent>) {
        events.forEach { eventLog.addEvent(it).get() }
    }

    @Test
    fun `getEvents should be able to retrieve all events`() {
        val events = (0..2).map { randomSecurityEvent() }
        addEvents(events)

        val entries = eventLog.getEvents(emptySet(), null, 0, 100).get()

        assertThat(entries).apply {
            `as`("Should return added entries")
            containsExactlyElementsOf(events.reversed())
        }
    }

    private fun testSingleEventGet(target: LogTarget) {
        val event = randomSecurityEvent(target)

        eventLog.addEvent(event).get()
        eventLog.addEvent(randomSecurityEvent()).get()

        val entries = eventLog.getEvents(emptySet(), event.target, 0, 100).get()

        assertThat(entries).apply {
            `as`("Should return added entries")
            containsOnly(event)
        }
    }

    @Test
    fun `getEvents should be able to retrieve added events with system target`() {
        testSingleEventGet(LogTarget.System)
    }

    @Test
    fun `getEvents should be able to retrieve added events with conversation target`() {
        testSingleEventGet(LogTarget.Conversation(randomUserConversationId()))
    }

    //TODO by type
    //right now this just makes sure the query is well-formed
    @Test
    fun `getEvents should be able to retrieve events based on type`() {
        eventLog.getEvents(setOf(LogEventType.SECURITY), null, 0, 100).get()
    }

    @Test
    fun `getEvents should be able to retrieve added events within the given time range`() {
        val events = randomSecurityEvents(10)

        addEvents(events)

        val expected = events.reversed().slice(1..5)

        val got = eventLog.getEvents(emptySet(), null, 1, 5).get()

        assertThat(got).apply {
            `as`("Returns only the asked for events")
            containsExactlyElementsOf(expected)
        }
    }

    //TODO based on type/etc
    @Test
    fun `deleteEventRange should remove matching events`() {
        val events = randomSecurityEvents(10)

        addEvents(events)

        val startTimestamp = events.first().timestamp
        val endTimestamp = events[4].timestamp

        val expected = events.subList(5, events.size).reversed()

        eventLog.deleteEventRange(emptySet(), null, startTimestamp, endTimestamp).get()

        val got = eventLog.getEvents(emptySet(), null, 0, 100).get()

        assertThat(got).apply {
            `as`("Target range should be removed")
            containsExactlyElementsOf(expected)
        }
    }

    //TODO type
    @Test
    fun `deleteEvents should remove matching entries (target)`() {
        val event = randomSecurityEvent(LogTarget.System)
        addEvents(listOf(event))

        val removeTarget = LogTarget.Conversation(randomUserConversationId())
        val toRemoveEvents = randomSecurityEvents(target = removeTarget)

        addEvents(toRemoveEvents)

        eventLog.deleteEvents(setOf(LogEventType.SECURITY), removeTarget).get()

        val got = eventLog.getEvents(emptySet(), null, 0, 100).get()

        assertThat(got).apply {
            `as`("Target events should be removed")
            containsOnly(event)
        }
    }

    @Test
    fun `deleteEvents should delete all events if no query params are given`() {
        addEvents(randomSecurityEvents())

        eventLog.deleteEvents(emptySet(), null).get()

        val got = eventLog.getEvents(emptySet(), null, 0, 100).get()

        assertThat(got).apply {
            `as`("All events should be removed")
            isEmpty()
        }
    }
}