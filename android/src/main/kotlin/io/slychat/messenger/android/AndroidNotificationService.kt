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
import io.slychat.messenger.core.SlyAddress
import io.slychat.messenger.services.NotificationState
import io.slychat.messenger.services.PlatformNotificationService
import io.slychat.messenger.services.config.UserConfigService
import io.slychat.messenger.services.di.UserComponent
import rx.Observable
import rx.subscriptions.CompositeSubscription

class AndroidNotificationService(private val context: Context) : PlatformNotificationService {
    companion object {
        val NOTIFICATION_ID_NEW_MESSAGES: Int = 0
        //api docs say 5, but 19+ allow up to 7
        val MAX_NOTIFICATION_LINES = 7
    }
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private val newMessagesNotification = NewMessagesNotification()

    private var userConfigService: UserConfigService? = null

    private val subscriptions = CompositeSubscription()

    /* PlatformNotificationService methods */
    override fun updateNotificationState(notificationState: NotificationState) {
        newMessagesNotification.update(notificationState)
        updateNewMessagesNotification()
    }

    override fun getNotificationSoundDisplayName(soundUri: String): String {
        val ringtone = RingtoneManager.getRingtone(context, Uri.parse(soundUri))
        return ringtone.getTitle(context)
    }

    /* Other */

    fun init(userSessionAvailable: Observable<UserComponent?>) {
        userSessionAvailable.subscribe { onUserSessionAvailabilityChanged(it) }
    }

    private fun onUserSessionAvailabilityChanged(userComponent: UserComponent?) {
        if (userComponent == null) {
            userConfigService = null
            subscriptions.clear()
        }
        else {
            userConfigService = userComponent.userConfigService
        }
    }

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

            val remainingCount = (MAX_NOTIFICATION_LINES..userCount-1).sumBy { adapter.getEntryUnreadCount(it) }

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
            intent.putExtra(MainActivity.EXTRA_CONVO_KEY, conversationInfo.asString())
        }

        return getPendingIntentForActivity(intent)
    }

    private fun getNewMessagesNotificationDeleteIntent(): PendingIntent {
        val intent = Intent(context, NotificationDeletionService::class.java)

        return getPendingIntentForService(intent)
    }

    //updates the current new message notification based on the current data
    private fun updateNewMessagesNotification() {
        if (!newMessagesNotification.hasNewMessages()) {
            notificationManager.cancel(NOTIFICATION_ID_NEW_MESSAGES)
            return
        }

        val pendingIntent = getNewMessagesNotificationIntent()
        val deletePendingIntent = getNewMessagesNotificationDeleteIntent()

        val soundUri = getMessageNotificationSound()

        val notificationBuilder = Notification.Builder(context)
            .setContentTitle(getNewMessagesNotificationTitle())
            .setContentText(getNewMessagesNotificationContentText())
            .setSmallIcon(R.drawable.notification)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setDeleteIntent(deletePendingIntent)
            .setStyle(getLoggedInInboxStyle())

        if (newMessagesNotification.hasNew && soundUri != null)
            notificationBuilder.setSound(soundUri)

        val notification = notificationBuilder.build()

        notificationManager.notify(NOTIFICATION_ID_NEW_MESSAGES, notification)
    }

    private fun getLoggedOffNotificationIntent(): PendingIntent {
        val intent = getMainActivityIntent()
        intent.action = Intent.ACTION_MAIN
        intent.addCategory(Intent.CATEGORY_LAUNCHER)
        return getPendingIntentForActivity(intent)
    }

    private fun getStopMessagesIntent(account: SlyAddress): PendingIntent {
        val intent = Intent(context, NotificationStopService::class.java)
        intent.putExtra(NotificationStopService.EXTRA_ACCOUNT, account.asString())
        return getPendingIntentForService(intent)
    }

    private fun getMessageNotificationSound(): Uri? {
        val userConfigService = userConfigService ?: return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        return userConfigService.notificationsSound?.let { Uri.parse(it) }
    }

    //XXX this shares a decent bit of code with getInboxStyle, maybe find a way to centralize it
    //biggest diff is accessing the fields, as well as logged in having actual messages
    //write an adaptor class so we only have one of these (the overall setup is the same for both cases)
    private fun getLoggedOutInboxStyle(info: List<OfflineMessageInfo>): Notification.InboxStyle? {
        return getInboxStyle(OfflineMessageInfoInboxStyleAdapter(info))
    }

    fun showLoggedOutNotification(account: SlyAddress, accountName: String, info: List<OfflineMessageInfo>) {
        val pendingIntent = getLoggedOffNotificationIntent()

        val soundUri = getMessageNotificationSound()

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

        val notificationBuilder = Notification.Builder(context)
            .setContentTitle(notificationTitle)
            .setContentText(notificationText)
            .setSmallIcon(R.drawable.notification)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setStyle(getLoggedOutInboxStyle(info))
            //FIXME icon
            .addAction(R.drawable.notification, "Stop receiving notifications", getStopMessagesIntent(account))

        if (soundUri != null)
            notificationBuilder.setSound(soundUri)

        val notification = notificationBuilder.build()

        //just reuse the same notification id, as both of these don't currently coexist
        notificationManager.notify(NOTIFICATION_ID_NEW_MESSAGES, notification)
    }
}
