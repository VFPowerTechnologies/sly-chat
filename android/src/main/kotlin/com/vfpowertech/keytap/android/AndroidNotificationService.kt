package com.vfpowertech.keytap.android

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.media.RingtoneManager
import android.text.SpannableString
import android.text.style.StyleSpan
import com.vfpowertech.keytap.core.persistence.MessageInfo
import com.vfpowertech.keytap.services.ContactDisplayInfo
import com.vfpowertech.keytap.services.PlatformNotificationService
import java.util.*

data class NewMessageData(
    val name: String,
    val lastMessage: String,
    val lastMessageTimestamp: Long,
    val unreadCount: Int
)

class NewMessagesNotification {
    val contents = HashMap<ContactDisplayInfo, NewMessageData>()

    fun hasNewMessages(): Boolean = contents.isNotEmpty()
    fun userCount(): Int = contents.size

    fun clear() {
        contents.clear()
    }

    /** Increases the unread count by the amount given in newMessageData. */
    fun updateUser(contact: ContactDisplayInfo, newMessageData: NewMessageData) {
        val current = contents[contact]
        val newValue = if (current != null) {
            val newUnreadCount = current.unreadCount + newMessageData.unreadCount
            NewMessageData(current.name, newMessageData.lastMessage, newMessageData.lastMessageTimestamp, newUnreadCount)
        }
        else
            newMessageData

        contents[contact] = newValue
    }

    fun clearUser(contact: ContactDisplayInfo) {
        contents.remove(contact)
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
    override fun clearMessageNotificationsForUser(contact: ContactDisplayInfo) {
        newMessagesNotification.clearUser(contact)
        updateNewMessagesNotification()
    }

    override fun clearAllMessageNotifications() {
        newMessagesNotification.clear()
        updateNewMessagesNotification()
    }

    override fun addNewMessageNotification(contact: ContactDisplayInfo, lastMessageInfo: MessageInfo, messageCount: Int) {
        val newMessageData = NewMessageData(contact.name, lastMessageInfo.message, lastMessageInfo.timestamp, messageCount)
        newMessagesNotification.updateUser(contact, newMessageData)
        updateNewMessagesNotification()
    }

    /* Other */

    //TODO sort notifications by last message timestamp
    private fun getInboxStyle(): Notification.InboxStyle? {
        val notification = newMessagesNotification
        if (notification.userCount() < 2)
            return null

        val inboxStyle = Notification.InboxStyle()

        val needsSummary = notification.userCount() > MAX_NOTIFICATION_LINES

        val entries = notification.contents.toList()

        for (i in 0..MAX_NOTIFICATION_LINES-1) {
            if (i >= entries.size)
                break

            val info = entries[i].second
            val messageInfo = if (info.unreadCount == 1) info.lastMessage else "${info.unreadCount} messages"
            val name = entries[i].first.name

            val line = SpannableString("$name $messageInfo")
            line.setSpan(StyleSpan(Typeface.BOLD), 0, name.length, 0)
            inboxStyle.addLine(line)
        }

        if (needsSummary) {
            //if we go over the limit, android adds a ... for us
            //so we just add this to trigger that behavior
            inboxStyle.addLine("...")

            var remainingCount = 0
            for (i in MAX_NOTIFICATION_LINES..entries.size-1)
                remainingCount += entries[i].second.unreadCount

            inboxStyle.setSummaryText("$remainingCount more messages")
        }

        return inboxStyle
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
            val users = notification.contents.values.map { it.name }
            users.joinToString(", ")
        }
    }

    private fun getNewMessagesNotificationTitle(): String {
        val notification = newMessagesNotification

        return if (notification.userCount() == 1) {
            notification.contents.entries.first().key.name
        }
        else {
            val totalUnreadCount = newMessagesNotification.contents.values.fold(0) { acc, v -> acc + v.unreadCount }
            "$totalUnreadCount new messages"
        }
    }

    private fun getNewMessagesNotificationIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
        intent.action = MainActivity.ACTION_VIEW_MESSAGES

        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)

        val isSingleUser = newMessagesNotification.userCount() == 1
        val typeExtra = if (isSingleUser)
            MainActivity.EXTRA_PENDING_MESSAGES_TYPE_SINGLE
        else
            MainActivity.EXTRA_PENDING_MESSAGES_TYPE_MULTI

        intent.putExtra(MainActivity.EXTRA_PENDING_MESSAGES_TYPE, typeExtra)

        if (isSingleUser) {
            val username = newMessagesNotification.contents.keys.first()
            intent.putExtra(MainActivity.EXTRA_USERID, username.id.id.toString())
        }

        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun getNewMessagesNotificationDeleteIntent(): PendingIntent {
        val intent = Intent(context, NotificationDeletionService::class.java)

        return PendingIntent.getService(context, 0, intent, PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    //updates the current new message notification based on the current data
    fun updateNewMessagesNotification() {
        if (!newMessagesNotification.hasNewMessages()) {
            notificationManager.cancel(NOTIFICATION_ID_NEW_MESSAGES)
            return
        }

        val pendingIntent = getNewMessagesNotificationIntent()
        val deletePendingIntent = getNewMessagesNotificationDeleteIntent()

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notification = Notification.Builder(context)
            .setContentTitle(getNewMessagesNotificationTitle())
            .setContentText(getNewMessagesNotificationContentText())
            .setSmallIcon(R.drawable.ic_mms_black_24dp)
            .setSound(soundUri)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setDeleteIntent(deletePendingIntent)
            .setStyle(getInboxStyle())
            .build()

        notificationManager.notify(NOTIFICATION_ID_NEW_MESSAGES, notification)
    }
}
