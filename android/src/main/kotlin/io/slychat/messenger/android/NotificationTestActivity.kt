package io.slychat.messenger.android

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.currentTimestamp
import io.slychat.messenger.core.persistence.ConversationDisplayInfo
import io.slychat.messenger.core.persistence.ConversationId
import io.slychat.messenger.core.persistence.GroupId
import io.slychat.messenger.core.persistence.LastMessageData
import java.util.*

/** Used to test notification display for various configurations. */
class NotificationTestActivity : AppCompatActivity() {
    companion object {
        val dummyUsers = listOf(
            "One",
            "Two",
            "Three",
            "Four",
            "Five",
            "Six",
            "Seven",
            "Eight",
            "Nine"
        )

        val NO_GROUP = "No group"

        val dummyGroups = listOf(
            NO_GROUP,
            "G-One",
            "G-Two",
            "G-Three"
        )
    }

    private lateinit var userSpinner: Spinner
    private lateinit var groupSpinner: Spinner

    private lateinit var notificationService: AndroidNotificationService

    private var messageCounter = 0

    private fun getCurrentSelectedUserPos(): Int {
        return userSpinner.selectedItemPosition
    }

    private fun getCurrentConversationDisplayInfo(userPos: Int, unreadCount: Int): ConversationDisplayInfo {
        val userId = UserId(userPos.toLong())

        val groupPos = groupSpinner.selectedItemPosition
        val (conversationId, groupName) = if (groupPos != 0) {
            val groupName = dummyGroups[groupPos-1]
            val id = GroupId(groupPos.toString().repeat(32))
            val key = ConversationId.Group(id)
            key to groupName
        }
        else {
            val key = ConversationId.User(userId)
            key to null
        }

        val message = if (unreadCount == 0)
            getCurrentMessageText()
        else
            getNextMessageText()

        val lastMessageData = LastMessageData(dummyUsers[userPos], message, currentTimestamp())

        return ConversationDisplayInfo(
            conversationId,
            groupName,
            unreadCount,
            lastMessageData
        )
    }

    private fun getCurrentMessageText(): String {
        return "Message $messageCounter"
    }

    private fun getNextMessageText(): String {
        val v = messageCounter
        ++messageCounter

        return "Message $v"
    }

    //[low, high]
    private fun randomInt(low: Int, high: Int): Int {
        return low + Math.abs(Random().nextInt()) % (high - low + 1)
    }

    private fun randomUsers(): List<Int> {
        val nUsers = randomInt(1, dummyUsers.size)

        val randomized = (0..dummyUsers.size-1).toMutableList()
        Collections.shuffle(randomized)
        return (0..nUsers-1).map {
            randomized[it]
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        notificationService = AndroidNotificationService(this)

        setContentView(R.layout.activity_notification_test)

        userSpinner = findViewById(R.id.userSpinner) as Spinner
        val userAdapter = ArrayAdapter<String>(this, android.R.layout.simple_spinner_item)
        userAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        userAdapter.addAll(dummyUsers)
        userSpinner.adapter = userAdapter

        groupSpinner = findViewById(R.id.groupSpinner) as Spinner
        val groupAdapter = ArrayAdapter<String>(this, android.R.layout.simple_spinner_item)
        groupAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        groupAdapter.addAll(dummyGroups)
        groupSpinner.adapter = groupAdapter

        val triggerBtn = findViewById(R.id.triggerNotificationBtn) as Button
        triggerBtn.setOnClickListener {
            val displayInfo = getCurrentConversationDisplayInfo(getCurrentSelectedUserPos(), 1)
            notificationService.updateConversationNotification(displayInfo)
        }

        val clearCurrentBtn = findViewById(R.id.clearCurrentBtn)
        clearCurrentBtn.setOnClickListener {
            val conversationInfo = getCurrentConversationDisplayInfo(getCurrentSelectedUserPos(), 0)
            notificationService.updateConversationNotification(conversationInfo)
        }

        val clearAllBtn = findViewById(R.id.clearAllBtn) as Button
        clearAllBtn.setOnClickListener {
            notificationService.clearAllMessageNotifications()
        }

        //presets
        val multipleUsersBtn = findViewById(R.id.multipleUsersBtn) as Button
        multipleUsersBtn.setOnClickListener {
            randomUsers().forEach {
                val conversationInfo = getCurrentConversationDisplayInfo(it, randomInt(1, 10))
                notificationService.updateConversationNotification(conversationInfo)
            }
        }

        val summaryBtn = findViewById(R.id.summaryBtn) as Button
        summaryBtn.setOnClickListener {
            (0..AndroidNotificationService.MAX_NOTIFICATION_LINES).forEach {
                val userName = dummyUsers[it]
                val conversationId = ConversationId(UserId(it.toLong()))

                val conversationDisplayInfo = ConversationDisplayInfo(
                    conversationId,
                    null,
                    1,
                    LastMessageData(userName, getNextMessageText(), currentTimestamp())
                )

                notificationService.updateConversationNotification(conversationDisplayInfo)
            }
        }
    }
}
