package com.vfpowertech.keytap.android

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import com.vfpowertech.keytap.services.PlatformNotificationService
import java.util.*

data class NewMessageData(val name: String, val unreadCount: Int)

class NewMessagesNotification {
    val contents = HashMap<String, NewMessageData>()

    fun hasNewMessages(): Boolean = contents.isNotEmpty()
    fun userCount(): Int = contents.size

    fun clear() {
        contents.clear()
    }

    /** Increases the unread count by the amount given in newMessageData. */
    fun updateUser(username: String, newMessageData: NewMessageData) {
        val current = contents[username]
        val newValue = if (current != null) {
            NewMessageData(current.name, current.unreadCount + newMessageData.unreadCount)
        }
        else
            newMessageData

        contents[username] = newValue
    }

    fun clearUser(username: String) {
        contents.remove(username)
    }
}

class AndroidNotificationService(private val context: Context) : PlatformNotificationService {
    companion object {
        val NOTIFICATION_ID_NEW_MESSAGES: Int = 0
    }

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private val newMessagesNotification = NewMessagesNotification()

    /* PlatformNotificationService methods */
    override fun clearMessageNotificationsForUser(contactEmail: String) {
        newMessagesNotification.clearUser(contactEmail)
        updateNewMessagesNotification()
    }

    override fun clearAllMessageNotifications() {
        newMessagesNotification.clear()
        updateNewMessagesNotification()
    }

    override fun addNewMessageNotification(contactEmail: String, messageCount: Int) {
        //TODO name
        newMessagesNotification.updateUser(contactEmail, NewMessageData(contactEmail, messageCount))
        updateNewMessagesNotification()
    }

    /* Other */

    private fun getNewMessagesNotificationContentText(): String {
        val notification = newMessagesNotification

        return if (notification.userCount() == 1) {
            val info = notification.contents.values.first()
            val username = info.name
            val count = info.unreadCount
            //TODO fix this when we add i18n
            val plural = if (count > 1) "s" else ""
            "You have $count new message$plural from $username"
        }
        else {
            val users = notification.contents.values.map { it.name }
            users.joinToString(", ")
        }
    }

    private fun getNewMessagesNotificationTitle(): String {
        val totalUnreadCount = newMessagesNotification.contents.values.fold(0) { acc, v -> acc + v.unreadCount }
        return "$totalUnreadCount new messages"
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
            intent.putExtra(MainActivity.EXTRA_USERNAME, username)
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

        //TODO inbox style notification
        //we have up to 5 lines
        //so if > 5 users, show 4 + "More"

        val notification = Notification.Builder(context)
            .setContentTitle(getNewMessagesNotificationTitle())
            .setContentText(getNewMessagesNotificationContentText())
            .setSmallIcon(R.drawable.ic_mms_black_24dp)
            .setSound(soundUri)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setDeleteIntent(deletePendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID_NEW_MESSAGES, notification)
    }
}
