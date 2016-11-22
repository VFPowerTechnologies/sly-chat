package io.slychat.messenger.android.activites

import android.app.AlertDialog
import android.content.DialogInterface
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.WindowManager
import android.support.v7.widget.AppCompatImageButton
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import io.slychat.messenger.android.AndroidApp
import io.slychat.messenger.android.R
import io.slychat.messenger.android.formatTimeStamp
import io.slychat.messenger.core.UserId
import io.slychat.messenger.services.PageType
import io.slychat.messenger.services.ui.UIMessengerService
import io.slychat.messenger.services.ui.UIContactInfo
import io.slychat.messenger.services.ui.UIMessage
import io.slychat.messenger.services.ui.UIMessageUpdateEvent
import nl.komponents.kovenant.ui.failUi
import nl.komponents.kovenant.ui.successUi
import org.slf4j.LoggerFactory
import rx.Subscription

class ChatActivity : AppCompatActivity() {
    private val log = LoggerFactory.getLogger(javaClass)

    private lateinit var app : AndroidApp
    private lateinit var messengerService : UIMessengerService

    private lateinit var pageTitle : TextView
    private lateinit var backBtn : AppCompatImageButton
    private lateinit var chatList : LinearLayout
    private lateinit var submitBtn : ImageButton
    private lateinit var chatInput : EditText

    private var userId : Long = -1
    private lateinit var contactInfo : UIContactInfo

    override fun onCreate (savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        log.debug("onCreate")

        userId = intent.getLongExtra("EXTRA_USERID", -1.toLong())
        if (userId == -1.toLong())
            finish()

        app = AndroidApp.get(this)

        messengerService = app.appComponent.uiMessengerService

        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_chat)

        init()
    }

    private fun init () {
        chatList = findViewById(R.id.chat_list) as LinearLayout
        backBtn = findViewById(R.id.back_button) as AppCompatImageButton
        pageTitle = findViewById(R.id.chat_page_title) as TextView
        submitBtn = findViewById(R.id.submit_chat_btn) as ImageButton
        chatInput = findViewById(R.id.chat_input) as EditText

        createEventListeners()
        setListeners()
    }

    private fun createEventListeners () {
        submitBtn.setOnClickListener {
            handleNewMessageSubmit()
        }
        backBtn.setOnClickListener {
            finish()
        }
    }

    private fun setListeners () {
        messengerService.addNewMessageListener { messageInfo ->
            val messages = messageInfo.messages
            if (messageInfo.contact != null && messageInfo.contact == UserId(userId)) {
                messages.forEach { message ->
                    chatList.addView(createMessageNode(message))
                }
            }
        }
        messengerService.addMessageStatusUpdateListener { event ->
            when (event) {
                is UIMessageUpdateEvent.Expired -> { log.debug("Expired") }
                is UIMessageUpdateEvent.Deleted -> { log.debug("Deleted") }
                is UIMessageUpdateEvent.DeletedAll -> { log.debug("Deleted All") }
                is UIMessageUpdateEvent.Delivered -> { log.debug("Delivered") }
                is UIMessageUpdateEvent.DeliveryFailed -> { log.debug("Delivery Failed") }
                is UIMessageUpdateEvent.Expiring -> { log.debug("Expiring") }
            }
        }
    }

    private fun unsubscribeListeners () {
        messengerService.clearListeners()
    }

    private fun getContact () {
        app.appComponent.uiContactsService.getContact(UserId(userId)) successUi {
            if (it == null)
                finish()
            else {
                contactInfo = it
                pageTitle.text = it.name
            }
        } failUi {
            finish()
        }
    }

    private fun getMessages () {
        messengerService.getLastMessagesFor(UserId(userId), 0, 100) successUi { messages ->
            chatList.removeAllViews()
            messages.reversed().forEach { message ->
                chatList.addView(createMessageNode(message))
            }
        }
    }

    private fun handleNewMessageSubmit () {
        val messageValue = chatInput.text.toString()
        if (messageValue.isEmpty())
            return

        messengerService.sendMessageTo(UserId(userId), messageValue, 0) successUi {
            chatInput.setText("")
        } failUi {
            log.debug("Send message failed", it.stackTrace)
        }
    }

    private fun createMessageNode(messageInfo: UIMessage): View {
        val layout : Int
        if (messageInfo.isSent)
            layout = R.layout.sent_message_node
        else
            layout = R.layout.received_message_node

        val messageNode = LayoutInflater.from(this).inflate(layout, chatList, false)
        val message = messageNode.findViewById(R.id.message) as TextView
        val timespan = messageNode.findViewById(R.id.timespan) as TextView

        val time: String

        if (messageInfo.receivedTimestamp == 0L)
            time = "Delivering..."
        else
            time = formatTimeStamp(messageInfo.receivedTimestamp)

        timespan.text = time
        message.text = messageInfo.message

        return messageNode
    }

    private fun displayErrorMessage (errorMessage: String) {
        log.debug("displaying error message : " + errorMessage)
        val dialog = AlertDialog.Builder(this).create()
        dialog.setMessage(errorMessage)
        dialog.setButton(AlertDialog.BUTTON_NEGATIVE, "OK", DialogInterface.OnClickListener { dialogInterface, i -> dialog.dismiss() })
        dialog.show()
    }

    override fun onStart() {
        super.onStart()
        log.debug("onStart")
    }

    override fun onPause() {
        super.onPause()
        log.debug("onPause")
        unsubscribeListeners()
    }

    override fun onResume() {
        super.onResume()
        app.dispatchEvent("PageChange", PageType.CONVO, userId.toString())
        getContact()
        getMessages()
        log.debug("onResume")
    }

    override fun onStop() {
        super.onStop()
        log.debug("onStop")
    }

    override fun onDestroy() {
        super.onDestroy()
        log.debug("onDestroy")
    }
}