package io.slychat.messenger.android.activites

import android.content.Intent
import android.os.Bundle
import android.support.design.widget.FloatingActionButton
import android.support.v4.widget.DrawerLayout
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.view.WindowManager
import android.view.LayoutInflater
import android.view.View
import android.view.Menu
import android.view.MenuItem
import android.view.Gravity
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.LinearLayout
import android.widget.TextView
import io.slychat.messenger.android.AndroidApp
import io.slychat.messenger.android.R
import org.slf4j.LoggerFactory
import android.widget.AdapterView.OnItemClickListener
import io.slychat.messenger.android.activites.services.impl.MessengerServiceImpl
import io.slychat.messenger.android.formatTimeStamp
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.persistence.ConversationId
import io.slychat.messenger.core.persistence.UserConversation
import io.slychat.messenger.services.LoginEvent
import io.slychat.messenger.services.PageType
import io.slychat.messenger.services.messaging.ConversationMessage
import nl.komponents.kovenant.ui.successUi
import rx.Subscription

class RecentChatActivity : AppCompatActivity(), BaseActivityInterface {
    private val log = LoggerFactory.getLogger(javaClass)

    private lateinit var app: AndroidApp

    private var arrayAdapter: ArrayAdapter<String>? = null
    private lateinit var menuArray: Array<String>
    private lateinit var mDrawerLayout : DrawerLayout
    private lateinit var mDrawerList : ListView

    private lateinit var recentChatList: LinearLayout
    private lateinit var contactFloatBtn: FloatingActionButton

    private var loginListener: Subscription? = null

    private var recentNodeData: MutableMap<UserId, Int> = mutableMapOf()

    private lateinit var messengerService: MessengerServiceImpl

    override fun onCreate (savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        log.debug("onCreate")

        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_recent_chat)

        app = AndroidApp.get(this)
        messengerService = MessengerServiceImpl(this)

        val actionBar = findViewById(R.id.my_toolbar) as Toolbar
        actionBar.title = "  Sly Chat"
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP)
            actionBar.logo = getDrawable(R.drawable.ic_launcher)
        else
            actionBar.logo = resources.getDrawable(R.drawable.ic_launcher)

        setSupportActionBar(actionBar)
        createRightDrawerMenu()

        recentChatList = findViewById(R.id.recent_chat_container) as LinearLayout
        contactFloatBtn = findViewById(R.id.contact_float_btn) as FloatingActionButton

        createEventListeners()
    }

    private fun init () {
        recentChatList.removeAllViews()

        messengerService.fetchAllConversation() successUi {
            displayRecentChat(messengerService.getActualSortedConversation(it))
        }

        setListeners()
    }

    private fun createEventListeners () {
        contactFloatBtn.setOnClickListener {
            startActivity(Intent(baseContext, ContactActivity::class.java))
        }
    }

    private fun displayRecentChat (conversations: List<UserConversation>) {
        conversations.forEach {
            recentChatList.addView(createRecentChatView(it))
        }
    }

    private fun createRecentChatView (conversation: UserConversation): View {
        val node = LayoutInflater.from(this).inflate(R.layout.recent_chat_node_layout, recentChatList, false)
        val nameView = node.findViewById(R.id.recent_chat_contact_name) as TextView
        val messageView = node.findViewById(R.id.recent_chat_contact_message) as TextView
        val timespanView = node.findViewById(R.id.recent_chat_contact_time) as TextView
        nameView.text = conversation.contact.name
        messageView.text = conversation.info.lastMessage

        val time: String
        time = formatTimeStamp(conversation.info.lastTimestamp)
        timespanView.text = time

        if (conversation.info.unreadMessageCount > 0) {
            val badge = node.findViewById(R.id.new_message_badge) as LinearLayout
            val amount = badge.findViewById(R.id.new_message_quantity) as TextView
            badge.visibility = LinearLayout.VISIBLE
            amount.text = conversation.info.unreadMessageCount.toString()
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

    fun onNewMessage (data: ConversationMessage) {
        val conversationId = data.conversationId
        when (conversationId) {
            is ConversationId.User -> {
                val conversation = messengerService.conversations[conversationId.id]
                if (conversation != null)
                    updateSingleRecentChatNode(conversation)
            }
            is ConversationId.Group -> { log.debug(conversationId.id.toString()) }
        }
    }

    private fun updateSingleRecentChatNode (conversation: UserConversation) {
        val userId = conversation.contact.id
        val nodeId = recentNodeData[userId]
        if (nodeId !== null) {
            recentChatList.removeView(findViewById(nodeId))
        }

        recentChatList.addView(createRecentChatView(conversation), 0)
    }

    private fun setListeners () {
        loginListener?.unsubscribe()
        loginListener = app.app.loginEvents.subscribe {
            handleLoginEvent(it)
        }

        messengerService.addNewMessageListener({ onNewMessage(it) })
    }

    private fun clearListners () {
        loginListener?.unsubscribe()
        messengerService.clearListeners()
    }

    private fun handleLoginEvent (event: LoginEvent) {
        when (event) {
            is LoginEvent.LoggedOut -> { processLogout() }
        }
    }

    private fun processLogout () {
        startActivity(Intent(baseContext, LoginActivity::class.java))
        finish()
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
                        startActivity(Intent(baseContext, ProfileActivity::class.java))
                    }
                    "settings" -> {
                        startActivity(Intent(baseContext, SettingsActivity::class.java))
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
                        startActivity(Intent(baseContext, InviteFriendsActivity::class.java))
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

    private fun dispatchEvent () {
        app.dispatchEvent("PageChange", PageType.CONTACTS, "")
    }

    override fun setAppActivity() {
        log.debug("set ui visible")
        app.setCurrentActivity(this, true)
    }

    override fun clearAppActivity() {
        log.debug("set ui hidden")
        app.setCurrentActivity(this, false)
    }

    override fun onStart() {
        super.onStart()
        log.debug("onStart")
    }

    override fun onPause() {
        super.onPause()
        log.debug("onPause")
        clearAppActivity()
        clearListners()
    }

    override fun onResume() {
        super.onResume()
        init()
        dispatchEvent()
        setAppActivity()
        log.debug("onResume")
    }

    override fun onStop() {
        super.onStop()
        log.debug("onStop")
        clearListners()
    }

    override fun onDestroy() {
        super.onDestroy()
        clearAppActivity()
        log.debug("onDestroy")
    }
}