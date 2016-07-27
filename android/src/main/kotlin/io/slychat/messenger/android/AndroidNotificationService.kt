package io.slychat.messenger.android

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.media.RingtoneManager
import android.net.Uri
import android.text.SpannableString
import android.text.style.StyleSpan
import io.slychat.messenger.services.PlatformNotificationService
import io.slychat.messenger.services.contacts.NotificationConversationInfo
import io.slychat.messenger.services.contacts.NotificationMessageInfo
import java.util.*

/** The latest available message for a conversation. */
data class NewMessageData(
    val speakerName: String,
    val lastMessage: String,
    val lastMessageTimestamp: Long,
    val unreadCount: Int
)

/** Current state of the new messages notification. */
class NewMessagesNotification {
    val contents = HashMap<NotificationConversationInfo, NewMessageData>()

    fun hasNewMessages(): Boolean = contents.isNotEmpty()
    fun userCount(): Int = contents.size

    fun clear() {
        contents.clear()
    }

    /** Increases the unread count by the amount given in newMessageData. */
    fun update(conversationInfo: NotificationConversationInfo, newMessageData: NewMessageData) {
        val current = contents[conversationInfo]
        val newValue = if (current != null) {
            val newUnreadCount = current.unreadCount + newMessageData.unreadCount
            NewMessageData(current.speakerName, newMessageData.lastMessage, newMessageData.lastMessageTimestamp, newUnreadCount)
        }
        else
            newMessageData

        contents[conversationInfo] = newValue
    }

    fun clear(conversationInfo: NotificationConversationInfo) {
        contents.remove(conversationInfo)
    }
}

class AndroidNotificationService(private val context: Context) : PlatformNotificationService {
    companion object {
        val NOTIFICATION_ID_NEW_MESSAGES: Int = 0
        //api docs say 5, but 19+ allow up to 7
        val MAX_NOTIFICATION_LINES = 7
    }
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private val newMessagesNotification = NewMessagesNotification()

    /* PlatformNotificationService methods */
    override fun clearMessageNotificationsFor(notificationConversationInfo: NotificationConversationInfo) {
        newMessagesNotification.clear(notificationConversationInfo)
        updateNewMessagesNotification()
    }

    override fun clearAllMessageNotifications() {
        newMessagesNotification.clear()
        updateNewMessagesNotification()
    }

    override fun addNewMessageNotification(notificationConversationInfo: NotificationConversationInfo, lastMessageInfo: NotificationMessageInfo, messageCount: Int) {
        val newMessageData = NewMessageData(lastMessageInfo.speakerName, lastMessageInfo.message, lastMessageInfo.timestamp, messageCount)
        newMessagesNotification.update(notificationConversationInfo, newMessageData)
        updateNewMessagesNotification()
    }

    /* Other */

    private fun getInboxStyle(adapter: InboxStyleAdapter): Notification.InboxStyle? {
        val userCount = adapter.userCount

        if (userCount < 2)
            return null

        val inboxStyle = Notification.InboxStyle()

        val needsSummary = userCount > MAX_NOTIFICATION_LINES

        for (i in 0..MAX_NOTIFICATION_LINES - 1) {
            if (i >= userCount)
                break

            val messageInfo = adapter.getEntryInfoLine(i)
            val name = adapter.getEntryName(i)

            val line = SpannableString("$name $messageInfo")
            line.setSpan(StyleSpan(Typeface.BOLD), 0, name.length, 0)
            inboxStyle.addLine(line)
        }

        if (needsSummary) {
            //if we go over the limit, android adds a ... for us
            //so we just add this to trigger that behavior
            inboxStyle.addLine("...")

            var remainingCount = 0
            for (i in MAX_NOTIFICATION_LINES..userCount-1)
                remainingCount += adapter.getEntryUnreadCount(i)

            inboxStyle.setSummaryText("$remainingCount more messages")
        }

        return inboxStyle
    }

    //TODO sort notifications by last message timestamp
    private fun getLoggedInInboxStyle(): Notification.InboxStyle? {
        return getInboxStyle(NewMessageNotificationInboxStyleAdapter(newMessagesNotification))
    }

    private fun getNewMessagesNotificationContentText(): String {
        val notification = newMessagesNotification

        return if (notification.userCount() == 1) {
            val info = notification.contents.values.first()
            val count = info.unreadCount
            if (count == 1)
                info.lastMessage
            else
                "$count new messages"
        }
        else {
            val users = notification.contents.values.map { it.speakerName }
            users.joinToString(", ")
        }
    }

    private fun getNewMessagesNotificationTitle(): String {
        val notification = newMessagesNotification

        return if (notification.userCount() == 1) {
            notification.contents.entries.first().value.speakerName
        }
        else {
            val totalUnreadCount = newMessagesNotification.contents.values.fold(0) { acc, v -> acc + v.unreadCount }
            "$totalUnreadCount new messages"
        }
    }

    private fun getMainActivityIntent(): Intent {
        val intent = Intent(context, MainActivity::class.java)

        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)

        return intent
    }

    private fun getPendingIntentForActivity(intent: Intent): PendingIntent =
        PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun getPendingIntentForService(intent: Intent): PendingIntent =
        PendingIntent.getService(context, 0, intent, PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun getNewMessagesNotificationIntent(): PendingIntent {
        val intent = getMainActivityIntent()
        intent.action = MainActivity.ACTION_VIEW_MESSAGES

        val isSingleUser = newMessagesNotification.userCount() == 1
        val typeExtra = if (isSingleUser)
            MainActivity.EXTRA_PENDING_MESSAGES_TYPE_SINGLE
        else
            MainActivity.EXTRA_PENDING_MESSAGES_TYPE_MULTI

        intent.putExtra(MainActivity.EXTRA_PENDING_MESSAGES_TYPE, typeExtra)

        if (isSingleUser) {
            val conversationInfo = newMessagesNotification.contents.keys.first()
            intent.putExtra(MainActivity.EXTRA_USERID, conversationInfo.key)
        }

        return getPendingIntentForActivity(intent)
    }

    private fun getNewMessagesNotificationDeleteIntent(): PendingIntent {
        val intent = Intent(context, NotificationDeletionService::class.java)

        return getPendingIntentForService(intent)
    }

    //updates the current new message notification based on the current data
    fun updateNewMessagesNotification() {
        if (!newMessagesNotification.hasNewMessages()) {
            notificationManager.cancel(NOTIFICATION_ID_NEW_MESSAGES)
            return
        }

        val pendingIntent = getNewMessagesNotificationIntent()
        val deletePendingIntent = getNewMessagesNotificationDeleteIntent()

        val soundUri = getNotificationSound()

        val notification = Notification.Builder(context)
            .setContentTitle(getNewMessagesNotificationTitle())
            .setContentText(getNewMessagesNotificationContentText())
            .setSmallIcon(R.drawable.notification)
            .setSound(soundUri)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setDeleteIntent(deletePendingIntent)
            .setStyle(getLoggedInInboxStyle())
            .build()

        notificationManager.notify(NOTIFICATION_ID_NEW_MESSAGES, notification)
    }

    private fun getLoggedOffNotificationIntent(): PendingIntent {
        val intent = getMainActivityIntent()
        intent.action = Intent.ACTION_MAIN
        intent.addCategory(Intent.CATEGORY_LAUNCHER)
        return getPendingIntentForActivity(intent)
    }

    private fun getStopMessagesIntent(): PendingIntent {
        val intent = Intent(context, NotificationStopService::class.java)
        return getPendingIntentForService(intent)
    }

    private fun getNotificationSound(): Uri {
       return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
    }

    //XXX this shares a decent bit of code with getInboxStyle, maybe find a way to centralize it
    //biggest diff is accessing the fields, as well as logged in having actual messages
    //write an adaptor class so we only have one of these (the overall setup is the same for both cases)
    private fun getLoggedOutInboxStyle(info: List<OfflineMessageInfo>): Notification.InboxStyle? {
        return getInboxStyle(OfflineMessageInfoInboxStyleAdapter(info))
    }

    fun showLoggedOutNotification(accountName: String, info: List<OfflineMessageInfo>) {
        val pendingIntent = getLoggedOffNotificationIntent()

        val soundUri = getNotificationSound()

        val notificationTitle = if (info.size == 1) {
            "New messages from ${info[0].name}"
        }
        else {
            val totalMessages = info.fold(0) { z, b ->
                z + b.pendingCount
            }
            "$totalMessages new messages"
        }

        val notificationText = if (info.size == 1) {
            val pendingCount = info[0].pendingCount
            val s = "$pendingCount new message"
            if (pendingCount > 1)
                s + "s"
            else
                s
        }
        else {
            "New messages for $accountName"
        }

        val notification = Notification.Builder(context)
            .setContentTitle(notificationTitle)
            .setContentText(notificationText)
            .setSmallIcon(R.drawable.notification)
            .setSound(soundUri)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setStyle(getLoggedOutInboxStyle(info))
            //FIXME icon
            .addAction(R.drawable.notification, "Stop receiving notifications", getStopMessagesIntent())
            .build()

        //just reuse the same notification id, as both of these don't currently coexist
        notificationManager.notify(NOTIFICATION_ID_NEW_MESSAGES, notification)
    }
}
