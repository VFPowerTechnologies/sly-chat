package io.slychat.messenger.android.activites

import android.content.Intent
import android.os.Bundle
import android.support.design.widget.FloatingActionButton
import android.support.v7.app.AppCompatActivity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import io.slychat.messenger.android.AndroidApp
import io.slychat.messenger.android.R
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.persistence.AllowedMessageLevel
import io.slychat.messenger.services.LoginEvent
import io.slychat.messenger.services.ui.*
import nl.komponents.kovenant.ui.successUi
import org.slf4j.LoggerFactory
import rx.Subscription
import java.sql.Timestamp
import java.util.*


class RecentChatActivity : AppCompatActivity() {
    private val log = LoggerFactory.getLogger(javaClass)

    private lateinit var app : AndroidApp
    private lateinit var contactService : UIContactsService
    private lateinit var messengerService : UIMessengerService

    private var loginListener : Subscription? = null

    private lateinit var contacts : List<UIContactInfo>
    private lateinit var conversations : List<UIConversation>

    private lateinit var logoutBtn : Button
    private lateinit var contactFloatBtn : FloatingActionButton

    override fun onCreate (savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        log.debug("onCreate")

//        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_recent_chat)

        init()
    }

    private fun init () {
        app = AndroidApp.get(this)
        contactService = app.appComponent.uiContactsService
        messengerService = app.appComponent.uiMessengerService

        contactFloatBtn = findViewById(R.id.contact_float_btn) as FloatingActionButton
        logoutBtn = findViewById(R.id.logout_button) as Button

        fetchContacts()
        fetchConversations()

        createEventListeners()
        setListeners()
    }

    private fun fetchConversations () {
        messengerService.getConversations() successUi { c ->
            conversations = c
            displayRecentChat()
        } fail {
            log.debug(it.message, it.stackTrace)
        }
    }

    private fun fetchContacts () {
        contactService.getContacts() success { c ->
            contacts = c
        } fail {
            log.debug(it.message, it.stackTrace)
        }
    }

    private fun displayRecentChat () {
        val recentChatNodes : MutableList<View> = arrayListOf()
        val container = findViewById(R.id.recent_chat_container) as LinearLayout

        conversations.forEach { conversation ->
            if (conversation.status.lastMessage != null) {
                recentChatNodes.add(createRecentChatNode(conversation))
            }
        }

        if (recentChatNodes.size > 0) {
            recentChatNodes.forEach { node ->
                container.addView(node)
            }
        }
        else {
            LayoutInflater.from(this).inflate(R.layout.recent_chat_empty_node, container, true)
        }
    }

    private fun createRecentChatNode (conversation: UIConversation) : View {
        val node = LayoutInflater.from(this).inflate(R.layout.recent_chat_node_layout, null, false)
        val nameView = node.findViewById(R.id.recent_chat_contact_name) as TextView
        val messageView = node.findViewById(R.id.recent_chat_contact_message) as TextView
        val timespanView = node.findViewById(R.id.recent_chat_contact_time) as TextView
        nameView.text = conversation.contact.name
        messageView.text = conversation.status.lastMessage
        timespanView.text = conversation.status.lastTimestamp.toString()

        node.setOnClickListener {
            log.debug("Clicked node for user : ${conversation.contact.id}")
        }

        return node
    }

    private fun createEventListeners () {
        contactFloatBtn.setOnClickListener {
            startActivity(Intent(baseContext, ContactActivity::class.java))
        }

        logoutBtn.setOnClickListener {
            app.app.logout()
        }
    }

    private fun setListeners () {
        loginListener?.unsubscribe()
        loginListener = app.app.loginEvents.subscribe {
            handleLoginEvent(it)
        }
    }

    private fun unsubscribeListener () {

    }

    private fun handleLoginEvent (event: LoginEvent) {
        when (event) {
            is LoginEvent.LoggedIn -> {
                log.debug("logged in")
            }
            is LoginEvent.LoggedOut -> { processLogout() }
            is LoginEvent.LoggingIn -> { log.debug("logging in") }
            is LoginEvent.LoginFailed -> { log.debug("login failed")}
        }
    }

    private fun processLogout () {
        startActivity(Intent(baseContext, LoginActivity::class.java))
        finish()
    }

    override fun onStart() {
        super.onStart()
        log.debug("onStart")
    }

    override fun onPause() {
        super.onPause()
        log.debug("onPause")
        unsubscribeListener()
    }

    override fun onResume() {
        super.onResume()
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