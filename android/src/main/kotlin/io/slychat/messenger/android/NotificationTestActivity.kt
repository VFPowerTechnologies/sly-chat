package io.slychat.messenger.android

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.currentTimestamp
import io.slychat.messenger.core.persistence.GroupId
import io.slychat.messenger.services.contacts.NotificationConversationInfo
import io.slychat.messenger.services.contacts.NotificationKey
import io.slychat.messenger.services.contacts.NotificationMessageInfo
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

    private fun getCurrentConversationInfo(): NotificationConversationInfo {
        val userPos = userSpinner.selectedItemPosition

        val groupPos = groupSpinner.selectedItemPosition
        return if (groupPos != 0) {
            val groupName = dummyGroups[groupPos-1]
            val id = GroupId(groupPos.toString())
            val key = NotificationKey.idToKey(id)
            NotificationConversationInfo(key, groupName)
        }
        else {
            val userId = UserId(userPos.toLong())
            val key = NotificationKey.idToKey(userId)
            NotificationConversationInfo(key, null)
        }
    }

    private fun getNextMessageText(): String {
        val v = messageCounter
        ++messageCounter

        return "Message $v"
    }

    private fun getNextMessageInfo(): NotificationMessageInfo {
        val userName = userSpinner.selectedItem as String

        return NotificationMessageInfo(userName, getNextMessageText(), currentTimestamp())
    }

    //[low, high]
    private fun randomInt(low: Int, high: Int): Int {
        return low + Math.abs(Random().nextInt()) % (high - low + 1)
    }

    private fun randomUsers(): List<NotificationConversationInfo> {
        val nUsers = randomInt(1, dummyUsers.size)

        val randomized = (0..dummyUsers.size-1).toMutableList()
        Collections.shuffle(randomized)

        return (0..nUsers-1).map {
            val pos = randomized[it]

            val id = UserId(pos.toLong())
            val key = NotificationKey.idToKey(id)
            NotificationConversationInfo(key, null)
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
            val displayInfo = getCurrentConversationInfo()
            val messageInfo = getNextMessageInfo()
            notificationService.addNewMessageNotification(displayInfo, messageInfo, 1)
        }

        val clearCurrentBtn = findViewById(R.id.clearCurrentBtn)
        clearCurrentBtn.setOnClickListener {
            val conversationInfo = getCurrentConversationInfo()
            notificationService.clearMessageNotificationsFor(conversationInfo)
        }

        val clearAllBtn = findViewById(R.id.clearAllBtn) as Button
        clearAllBtn.setOnClickListener {
            notificationService.clearAllMessageNotifications()
        }

        //presets
        val multipleUsersBtn = findViewById(R.id.multipleUsersBtn) as Button
        multipleUsersBtn.setOnClickListener {
            randomUsers().forEach {
                notificationService.addNewMessageNotification(it, getNextMessageInfo(), 1)
            }
        }

        val summaryBtn = findViewById(R.id.summaryBtn) as Button
        summaryBtn.setOnClickListener {
            (0..AndroidNotificationService.MAX_NOTIFICATION_LINES).forEach {
                val userName = dummyUsers[it]
                val key = NotificationKey.idToKey(UserId(it.toLong()))
                val convoInfo = NotificationConversationInfo(key, null)

                val messageInfo = NotificationMessageInfo(userName, getNextMessageText(), currentTimestamp())

                notificationService.addNewMessageNotification(convoInfo, messageInfo, 1)
            }
        }
    }
}
