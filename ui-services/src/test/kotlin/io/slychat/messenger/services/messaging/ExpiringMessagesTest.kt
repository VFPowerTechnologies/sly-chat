package io.slychat.messenger.services.messaging

import io.slychat.messenger.core.crypto.randomMessageId
import io.slychat.messenger.core.randomUserConversationId
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals

class ExpiringMessagesTest {
    private val expiringMessages = ExpiringMessages()

    private fun randomEntry(expiresAt: Long): ExpiringMessages.ExpiringEntry {
        return ExpiringMessages.ExpiringEntry(randomUserConversationId(), randomMessageId(), expiresAt)
    }

    private fun randomEntries(n: Int = 5): List<ExpiringMessages.ExpiringEntry> {
        return (1L..n).map { randomEntry(it) }
    }

    @Test
    fun `add should keep the list sorted in ascending order`() {
        val e1 = randomEntry(2)
        val e2 = randomEntry(1)

        expiringMessages.add(e1)
        expiringMessages.add(e2)

        assertEquals(listOf(e2, e1), expiringMessages.toList())
    }

    @Test
    fun `addAll should keep the list sorted in ascending order`() {
        val ordered = (1..5L).map { randomEntry(it) }

        val shuffled = ArrayList(ordered)
        Collections.shuffle(shuffled)

        expiringMessages.addAll(shuffled)

        assertEquals(ordered, expiringMessages.toList())
    }

    @Test
    fun `removeExpired should remove and return all expired entries`() {
        val n = 5
        val entries = randomEntries(n)

        expiringMessages.addAll(entries)

        val removed = expiringMessages.removeExpired(3)

        val expectedRemoved = entries.subList(0, 3)
        val expectedKept = entries.subList(3, n)

        assertEquals(expectedRemoved, removed)
        assertEquals(expectedKept, expiringMessages.toList())
    }

    @Test
    fun `nextExpiration should return 0 when no entries are present`() {
        assertEquals(0, expiringMessages.nextExpiration())
    }

    @Test
    fun `nextExpiration should return the first element expiration when entries are present`() {
        val entries = randomEntries()
        expiringMessages.addAll(entries)
        assertEquals(entries.first().expireAt, expiringMessages.nextExpiration())
    }
}