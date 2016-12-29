package io.slychat.messenger.android

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.currentTimestamp
import io.slychat.messenger.core.persistence.*
import io.slychat.messenger.core.randomMessageIds
import io.slychat.messenger.services.NotificationConversationInfo
import io.slychat.messenger.services.NotificationState
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

    private var currentNotificationState = HashMap<ConversationId, NotificationConversationInfo>()

    private val unreadCounts = HashMap<ConversationId, Int>()

    private fun pushState() {
        notificationService.updateNotificationState(NotificationState(currentNotificationState.values.toList()))
    }

    private fun incUnreadCount(conversationId: ConversationId, amount: Int) {
        val current = unreadCounts[conversationId] ?: 0
        unreadCounts[conversationId] = current + amount
    }

    private fun addToState(conversationDisplayInfo: ConversationDisplayInfo) {
        currentNotificationState[conversationDisplayInfo.conversationId] = NotificationConversationInfo(conversationDisplayInfo, true)
        pushState()
    }

    private fun removeAllFromState() {
        currentNotificationState = HashMap()
        unreadCounts.clear()
        pushState()
    }

    private fun removeConvoFromState(conversationId: ConversationId) {
        currentNotificationState.remove(conversationId)
        unreadCounts.remove(conversationId)
        pushState()
    }

    private fun getCurrentSelectedUserPos(): Int {
        return userSpinner.selectedItemPosition
    }

    private fun getCurrentConversationId(): ConversationId {
        val groupPos = groupSpinner.selectedItemPosition

        return if (groupPos != 0) {
            GroupId(groupPos.toString().repeat(32)).toConversationId()
        }
        else {
            UserId(getCurrentSelectedUserPos().toLong()).toConversationId()
        }
    }

    private fun getCurrentConversationDisplayInfo(conversationId: ConversationId): ConversationDisplayInfo {
        val speakerPos = when (conversationId) {
            is ConversationId.User -> conversationId.id.long
            is ConversationId.Group -> getCurrentSelectedUserPos().toLong()
        }

        val speakerName = dummyUsers[speakerPos.toInt()]
        val speakerId = UserId(speakerPos)

        val unreadCount = unreadCounts[conversationId] ?: 0

        val message = if (unreadCount == 0)
            getCurrentMessageText()
        else
            getNextMessageText()

        val lastMessageData = LastMessageData(speakerName, speakerId, message, currentTimestamp())

        val conversationName = when (conversationId) {
            is ConversationId.User -> speakerName
            is ConversationId.Group -> {
                val groupPos = groupSpinner.selectedItemPosition
                dummyGroups[groupPos - 1]
            }
        }

        return ConversationDisplayInfo(
            conversationId,
            conversationName,
            unreadCount,
            randomMessageIds(unreadCount),
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

    private fun randomUsers(): List<UserId> {
        val nUsers = randomInt(1, dummyUsers.size)

        val randomized = (0..dummyUsers.size-1).toMutableList()
        Collections.shuffle(randomized)
        return (0..nUsers-1).map {
            UserId(randomized[it].toLong())
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
            val conversationId = getCurrentConversationId()

            incUnreadCount(conversationId, 1)

            val conversationDisplayInfo = getCurrentConversationDisplayInfo(conversationId)
            addToState(conversationDisplayInfo)
        }

        val clearCurrentBtn = findViewById(R.id.clearCurrentBtn)
        clearCurrentBtn.setOnClickListener {
            removeConvoFromState(getCurrentConversationId())
        }

        val clearAllBtn = findViewById(R.id.clearAllBtn) as Button
        clearAllBtn.setOnClickListener {
            removeAllFromState()
        }

        //presets
        val multipleUsersBtn = findViewById(R.id.multipleUsersBtn) as Button
        multipleUsersBtn.setOnClickListener {
            randomUsers().forEach {
                val conversationId = it.toConversationId()
                incUnreadCount(conversationId, randomInt(1, 10))

                val conversationDisplayInfo = getCurrentConversationDisplayInfo(conversationId)
                addToState(conversationDisplayInfo)
            }
        }

        val summaryBtn = findViewById(R.id.summaryBtn) as Button
        summaryBtn.setOnClickListener {
            (0..AndroidNotificationService.MAX_NOTIFICATION_LINES).forEach {
                val userName = dummyUsers[it]
                val userId = UserId(it.toLong())
                val conversationId = ConversationId(userId)

                val conversationDisplayInfo = ConversationDisplayInfo(
                    conversationId,
                    userName,
                    1,
                    randomMessageIds(1),
                    LastMessageData(userName, userId, getNextMessageText(), currentTimestamp())
                )

                addToState(conversationDisplayInfo)
            }
        }
    }
}
