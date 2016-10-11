package io.slychat.messenger.services.messaging

import io.slychat.messenger.core.persistence.ConversationId
import java.util.*

internal class MessageListMap : HashMap<ConversationId, MutableList<String>>() {
    override fun get(key: ConversationId): MutableList<String> {
        val v = super.get(key)
        if (v != null)
            return v

        val list = ArrayList<String>()
        set(key, list)

        return list
    }

    fun toMap(): Map<ConversationId, List<String>> {
        return HashMap(this)
    }
}