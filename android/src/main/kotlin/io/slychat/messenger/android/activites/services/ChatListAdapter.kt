package io.slychat.messenger.android.activites.services

import android.widget.ArrayAdapter
import android.view.ViewGroup
import android.content.Context
import android.view.View
import io.slychat.messenger.android.activites.ChatActivity
import io.slychat.messenger.android.activites.views.MessageExpired
import io.slychat.messenger.android.activites.views.MessageReceived
import io.slychat.messenger.android.activites.views.MessageSent
import io.slychat.messenger.services.MessageUpdateEvent

class ChatListAdapter(context: Context, private val values: List<AndroidUIMessageInfo>, val chatActivity: ChatActivity) : ArrayAdapter<AndroidUIMessageInfo>(context, -1, values) {
    private val mapData = mutableMapOf<String, Int>()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val message = values[position]

        if (message.isExpired) {
            return MessageExpired(message.isSent, chatActivity)
        }

        val messageView: View
        if (message.isSent)
            messageView = MessageSent(message, chatActivity)
        else
            messageView = MessageReceived(message, chatActivity)

        chatActivity.registerForContextMenu(messageView)

        return messageView
    }

    override fun notifyDataSetChanged() {
        super.notifyDataSetChanged()
    }

    fun addAllMessages(messages: List<AndroidUIMessageInfo>) {
        super.addAll(messages)
        updateMap()
    }

    override fun add(`object`: AndroidUIMessageInfo) {
        super.add(`object`)
        updateMap()
    }

    private fun updateMap() {
        for((index, element) in values.withIndex()) {
            mapData.put(element.messageId, index)
        }
    }

    fun addMessageIfInexistent(messages: List<AndroidUIMessageInfo>) {
        val notSeen = mutableListOf<AndroidUIMessageInfo>()

        messages.forEach { message ->
            if (!mapData.containsKey(message.messageId))
                notSeen.add(message)
        }

        notSeen.sortBy { it.timestamp }
        this.addAllMessages(notSeen)
    }

    fun addMessagesToTop(messages: List<AndroidUIMessageInfo>) {
        messages.forEach {
            if (!mapData.contains(it.messageId))
                this.insert(it, 0)
        }

        updateMap()
    }

    private fun deleteMessage(messageId: String) {
        val position = mapData[messageId]
        if (position != null)
            this.remove(values[position])
    }

    fun deleteMessages(messageIds: List<String>) {
        messageIds.forEach {
            deleteMessage(it)
        }
    }

    fun getMessageInfo(messageId: String): AndroidUIMessageInfo? {
        val position = mapData[messageId]
        return if (position != null)
            values[position]
        else
            null
    }

    fun displayExpiringMessage(eventInfo: MessageUpdateEvent.Expiring) {
        val position = mapData[eventInfo.messageId] ?: return
        val info = this.getItem(position)

        info.startExpiration(eventInfo.ttl, eventInfo.expiresAt)
        this.notifyDataSetChanged()
    }

    fun updateMessageExpired(eventInfo: MessageUpdateEvent.Expired) {
        val position = mapData[eventInfo.messageId] ?: return
        val info = this.getItem(position)

        info.isExpired = true
        this.notifyDataSetChanged()
    }

    fun updateMessageDelivered(eventInfo: MessageUpdateEvent.Delivered) {
        val position = mapData[eventInfo.messageId] ?: return
        val info = this.getItem(position)

        info.receivedTimestamp = eventInfo.deliveredTimestamp
        this.notifyDataSetChanged()
    }

    fun updateMessageDeliveryFailed(eventInfo: MessageUpdateEvent.DeliveryFailed) {
        val position = mapData[eventInfo.messageId] ?: return
        val info = this.getItem(position)

        info.failures = eventInfo.failures
        this.notifyDataSetChanged()
    }
}