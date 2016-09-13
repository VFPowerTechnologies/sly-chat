package io.slychat.messenger.services.messaging

import io.slychat.messenger.core.persistence.ConversationId
import java.util.*

internal class ExpiringMessages {
    data class ExpiringEntry(val conversationId: ConversationId, val messageId: String, val expireAt: Long)

    private val comparator: (ExpiringEntry, ExpiringEntry) -> Int = { a, b -> a.expireAt.compareTo(b.expireAt) }
    private var list = ArrayList<ExpiringEntry>()

    fun toList(): List<ExpiringEntry> = ArrayList(list)

    private fun sortList() {
        list.sort(comparator)
    }

    fun add(entry: ExpiringEntry) {
        //XXX might just be better to sort the list?
        val pos = Collections.binarySearch(list, entry, comparator)
        val insertionPosition = if (pos < 0)
            -(pos + 1)
        else
            pos

        list.add(insertionPosition, entry)
    }

    fun addAll(entries: Iterable<ExpiringEntry>) {
        list.addAll(entries)
        sortList()
    }

    //remove if exists, else do nothing
    fun remove(conversationId: ConversationId, messageId: String): Boolean {
        return list.removeIf { it.messageId == messageId && it.conversationId == conversationId }
    }

    fun removeAll(conversationId: ConversationId): Boolean {
        return list.removeIf { it.conversationId == conversationId }
    }

    fun removeExpired(currentTime: Long): List<ExpiringEntry> {
        val expired = list.takeWhile { it.expireAt <= currentTime }
        if (expired.isNotEmpty())
            list.subList(0, expired.size).clear()

        return expired
    }

    //0 if empty
    //absolute system time of next expiration (basicly the first item's expireAt)
    fun nextExpiration(): Long {
        return if (list.isEmpty())
            0
        else
            list.first().expireAt
    }
}