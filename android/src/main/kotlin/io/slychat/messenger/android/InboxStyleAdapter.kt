package io.slychat.messenger.android

interface InboxStyleAdapter {
    val userCount: Int

    fun getEntryName(i: Int): String
    fun getEntryInfoLine(i: Int): String
    fun getEntryUnreadCount(i: Int): Int
}

class OfflineMessageInfoInboxStyleAdapter(private val info: List<OfflineMessageInfo>) : InboxStyleAdapter {
    override val userCount: Int = info.size

    override fun getEntryName(i: Int): String {
        return info[i].name
    }

    override fun getEntryInfoLine(i: Int): String {
        val userInfo = info[i]
        return if (userInfo.pendingCount == 1)
            "1 new message"
        else
            "${userInfo.pendingCount} new messages"

    }

    override fun getEntryUnreadCount(i: Int): Int {
        return info[i].pendingCount
    }
}

class NewMessageNotificationInboxStyleAdapter(newMessagesNotification: NewMessagesNotification) : InboxStyleAdapter {
    override val userCount: Int = newMessagesNotification.userCount()

    private val entries = newMessagesNotification.contents.toList()

    override fun getEntryName(i: Int): String {
        return entries[i].second.speakerName
    }

    override fun getEntryInfoLine(i: Int): String {
        val info = entries[i].second
        return if (info.unreadCount == 1) info.lastMessage else "${info.unreadCount} messages"
    }

    override fun getEntryUnreadCount(i: Int): Int {
        return entries[i].second.unreadCount
    }
}
