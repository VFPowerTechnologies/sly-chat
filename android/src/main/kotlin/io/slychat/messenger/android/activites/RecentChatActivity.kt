package io.slychat.messenger.android.activites

import android.content.Intent
import android.os.Bundle
import android.support.design.widget.FloatingActionButton
import android.support.v4.widget.DrawerLayout
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.view.*
import android.widget.*
import io.slychat.messenger.android.AndroidApp
import io.slychat.messenger.android.R
import io.slychat.messenger.services.LoginEvent
import io.slychat.messenger.services.ui.*
import nl.komponents.kovenant.ui.successUi
import org.slf4j.LoggerFactory
import rx.Subscription
import android.widget.AdapterView.OnItemClickListener
import android.widget.AdapterView.generateViewId
import io.slychat.messenger.android.formatTimeStamp
import io.slychat.messenger.core.UserId
import org.ocpsoft.prettytime.PrettyTime
import java.util.*

class RecentChatActivity : AppCompatActivity() {
    private val log = LoggerFactory.getLogger(javaClass)

    private lateinit var app : AndroidApp
    private lateinit var contactService : UIContactsService
    private lateinit var messengerService: UIMessengerService

    private var loginListener: Subscription? = null

    private lateinit var contacts: List<UIContactInfo>
    private lateinit var recentNodeData: MutableMap<UserId, Int>
    private lateinit var contactFloatBtn: FloatingActionButton
    private lateinit var recentChatContainer: LinearLayout

    private var arrayAdapter: ArrayAdapter<String>? = null
    private lateinit var menuArray: Array<String>

    private lateinit var mDrawerLayout : DrawerLayout
    private lateinit var mDrawerList : ListView

    private var prettyTime: PrettyTime? = null

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
        recentChatContainer = findViewById(R.id.recent_chat_container) as LinearLayout

        prettyTime = PrettyTime()

        val actionBar = findViewById(R.id.my_toolbar) as Toolbar
        actionBar.title = "  Sly Chat"
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP)
            actionBar.logo = getDrawable(R.drawable.ic_launcher)
        else
            actionBar.logo = resources.getDrawable(R.drawable.ic_launcher)

        setSupportActionBar(actionBar)
        createRightDrawerMenu()

        createEventListeners()
        setListeners()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.layout_menu, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.action_menu -> { mDrawerLayout.openDrawer(Gravity.END)}
        }
        return super.onOptionsItemSelected(item)
    }

    private fun addDrawerItems() {
        menuArray = resources.getStringArray(R.array.main_menu_list)
        arrayAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, menuArray)
        mDrawerList.adapter = arrayAdapter
    }

    private fun createRightDrawerMenu () {
        mDrawerList = findViewById(R.id.right_drawer) as ListView
        mDrawerLayout = findViewById(R.id.drawer_layout) as DrawerLayout
        addDrawerItems()

        mDrawerList.onItemClickListener =
            OnItemClickListener { adapterView, view, position, l ->
                when (menuArray[position].toLowerCase()) {
                    "profile" -> {
                        log.debug("profile")
                    }
                    "settings" -> {
                        log.debug("Settings")
                    }
                    "add contact" -> {
                        startActivity(Intent(baseContext, AddContactActivity::class.java))
                    }
                    "blocked contacts" -> {
                        log.debug("Blocked Contacts")
                    }
                    "create group" -> {
                        log.debug("Create Group")
                    }
                    "invite friends" -> {
                        log.debug("Invite Friends")
                    }
                    "feedback" -> {
                        startActivity(Intent(baseContext, FeedbackActivity::class.java))
                    }
                    "logout" -> {
                        app.app.logout()
                    }
                }

                mDrawerLayout.closeDrawer(mDrawerList)
            }


    }

    private fun fetchConversations () {
        messengerService.getConversations() successUi { conversations ->
            app.setConversationCache(conversations)
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

        recentChatContainer.removeAllViews()
        recentNodeData = mutableMapOf()
        app.conversationCache.forEach {
            if (it.value.status.lastMessage != null) {
                recentChatNodes.add(createRecentChatNode(it.value))
            }
        }

        if (recentChatNodes.size > 0) {
            recentChatNodes.forEach { node ->
                recentChatContainer.addView(node)
            }
        }
        else {
            LayoutInflater.from(this).inflate(R.layout.recent_chat_empty_node, recentChatContainer, true)
        }
    }

    private fun createRecentChatNode (conversation: UIConversation) : View {
        val node = LayoutInflater.from(this).inflate(R.layout.recent_chat_node_layout, recentChatContainer, false)
        val nameView = node.findViewById(R.id.recent_chat_contact_name) as TextView
        val messageView = node.findViewById(R.id.recent_chat_contact_message) as TextView
        val timespanView = node.findViewById(R.id.recent_chat_contact_time) as TextView
        nameView.text = conversation.contact.name
        messageView.text = conversation.status.lastMessage

        val time: String
        time = formatTimeStamp(conversation.status.lastTimestamp!!)
        timespanView.text = time

        if (conversation.status.unreadMessageCount > 0) {
            val badge = node.findViewById(R.id.new_message_badge) as LinearLayout
            val amount = badge.findViewById(R.id.new_message_quantity) as TextView
            badge.visibility = LinearLayout.VISIBLE
            amount.text = conversation.status.unreadMessageCount.toString()
        }

        node.setOnClickListener {
            val intent = Intent(baseContext, ChatActivity::class.java)
            intent.putExtra("EXTRA_USERID", conversation.contact.id.long)
            startActivity(intent)
        }

        val nodeId = View.generateViewId()
        node.id = nodeId

        recentNodeData.put(conversation.contact.id, nodeId)

        return node
    }

    private fun createEventListeners () {
        contactFloatBtn.setOnClickListener {
            startActivity(Intent(baseContext, ContactActivity::class.java))
        }
    }

    private fun setListeners () {
        loginListener?.unsubscribe()
        loginListener = app.app.loginEvents.subscribe {
            handleLoginEvent(it)
        }
    }

    private fun unsubscribeListener () {
        loginListener?.unsubscribe()
        app.appComponent.uiMessengerService.addNewMessageListener {
            handleNewMessageEvent(it)
        }
    }

    private fun handleNewMessageEvent (messageInfo: UIMessageInfo) {
        val contactId = messageInfo.contact
        if (contactId != null) {
            val nodeId = recentNodeData[contactId]
            if (nodeId != null) {
                val node = findViewById(nodeId)
                recentChatContainer.removeView(node)
            }
            //Add new messagae to list
        }
        else {
            //handle group message
        }
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
        fetchContacts()
        fetchConversations()
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