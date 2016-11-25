package io.slychat.messenger.android.activites

import android.content.Intent
import android.os.Bundle
import android.support.design.widget.FloatingActionButton
import android.support.v7.app.AppCompatActivity
import android.view.WindowManager
import android.support.v7.widget.Toolbar
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import io.slychat.messenger.android.AndroidApp
import io.slychat.messenger.android.R
import io.slychat.messenger.android.activites.services.impl.ContactServiceImpl
import io.slychat.messenger.android.activites.services.impl.MessengerServiceImpl
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.persistence.ContactInfo
import io.slychat.messenger.core.persistence.ConversationId
import io.slychat.messenger.core.persistence.UserConversation
import io.slychat.messenger.services.contacts.ContactEvent
import io.slychat.messenger.services.messaging.ConversationMessage
import nl.komponents.kovenant.ui.successUi
import org.slf4j.LoggerFactory

class ContactActivity : AppCompatActivity() {
    private val log = LoggerFactory.getLogger(javaClass)

    private lateinit var app : AndroidApp
    private lateinit var messengerService: MessengerServiceImpl
    private lateinit var contactService: ContactServiceImpl
    private lateinit var conversations : MutableMap<UserId, UserConversation>

    private lateinit var recentContactList : LinearLayout
    private lateinit var contactList : LinearLayout
    private lateinit var addContactBtn : FloatingActionButton

    private var contactListData: MutableMap<UserId, Int> = mutableMapOf()
    private var recentContactListData: MutableMap<UserId, Int> = mutableMapOf()

    override fun onCreate (savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        log.debug("onCreate")

        app = AndroidApp.get(this)
        messengerService = MessengerServiceImpl(this)
        contactService = ContactServiceImpl(this)

        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_contact)

        contactList = findViewById(R.id.contact_list) as LinearLayout
        recentContactList = findViewById(R.id.recent_contact_list) as LinearLayout
        addContactBtn = findViewById(R.id.contacts_add_contact_btn) as FloatingActionButton

        val actionBar = findViewById(R.id.my_toolbar) as Toolbar
        actionBar.title = "Address Book"
        setSupportActionBar(actionBar)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        createEventListeners()
    }

    private fun init () {
        fetchConversations()
        setAppActivity()
        setListeners()
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item?.itemId) {
            android.R.id.home -> { finish() }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun createEventListeners () {
        addContactBtn.setOnClickListener {
            startActivity(Intent(baseContext, AddContactActivity::class.java))
        }
    }

    private fun fetchConversations () {
        messengerService.fetchAllConversation() successUi {
            conversations = it
            displayContacts()
        }
    }

    private fun displayContacts () {
        contactList.removeAllViews()
        recentContactList.removeAllViews()
        if (conversations.isNotEmpty()) {
            conversations.forEach {
                contactList.addView(createContactNode(it.value.contact))
            }
            messengerService.getActualSortedConversation(conversations).forEach {
                recentContactList.addView(createRecentContactNode(it.contact.id, it.contact.name))
            }
        }
        else {
            LayoutInflater.from(this).inflate(R.layout.contact_empty_node, contactList, true)
        }
    }

    private fun createContactNode (contact: ContactInfo): View {
        val contactNode = LayoutInflater.from(this).inflate(R.layout.contact_node, contactList, false)
        val contactNameNode = contactNode.findViewById(R.id.contact_name) as TextView
        val contactEmailNode = contactNode.findViewById(R.id.contact_email) as TextView
        contactNameNode.text = contact.name
        contactEmailNode.text = contact.email

        contactNode.setOnClickListener {
            loadChatPageFor(contact.id)
        }

        val nodeId = View.generateViewId()
        contactListData.put(contact.id, nodeId)
        contactNode.id = nodeId

        return contactNode
    }

    private fun createRecentContactNode (id: UserId, name: String): View {
        val recentContactNode = LayoutInflater.from(this).inflate(R.layout.recent_contact_node, recentContactList, false)
        val contactNameNode = recentContactNode.findViewById(R.id.contact_name) as TextView
        val contactInitial = recentContactNode.findViewById(R.id.contact_initial) as TextView
        contactNameNode.text = name
        contactInitial.text = name[0].toString()

        recentContactNode.setOnClickListener {
            loadChatPageFor(id)
        }

        val nodeId = View.generateViewId()
        recentContactListData.put(id, nodeId)
        recentContactNode.id = nodeId

        log.debug("node id = ${recentContactListData[id]}")

        return recentContactNode
    }

    private fun loadChatPageFor (id: UserId) {
        val intent = Intent(baseContext, ChatActivity::class.java)
        intent.putExtra("EXTRA_USERID", id.long)
        startActivity(intent)
    }

    private fun handleContactEvent (event: ContactEvent) {
        when (event) {
            is ContactEvent.Added -> {
                event.contacts.forEach { contact ->
                    if (contactListData[contact.id] == null) {
                        contactList.addView(createContactNode(contact))
                    }
                }
            }
            is ContactEvent.Blocked -> {
                removeContactFromList(event.userId)
            }
            is ContactEvent.Removed -> {
                log.debug("Contact Removed Event")
                event.contacts.forEach {
                    log.debug("Contact to be removed ${it.name}")
                    removeContactFromList(it.id)
                }
            }
            is ContactEvent.Sync -> {
                log.debug("Is contact sync running: ${event.isRunning}")
                if(!event.isRunning) {
                    fetchConversations()
                }
            }
            is ContactEvent.Unblocked -> { log.debug("Contact was unblocked: ${event.userId}") }
            is ContactEvent.Updated -> {
                event.contacts.forEach {
                    updateContactFromList(it.old, it.new)
                }
            }
        }
    }

    private fun updateContactFromList (old: ContactInfo, new: ContactInfo) {
        val nodeId = contactListData[old.id]
        val recentNodeId = recentContactListData[old.id]
        if (nodeId != null) {
            val node = contactList.findViewById(nodeId) as LinearLayout
            val name = node.findViewById(R.id.contact_name) as TextView
            val email = node.findViewById(R.id.contact_email) as TextView
            name.text = new.name
            email.text = new.email
        }
        else {
            contactList.addView(createContactNode(new))
        }

        if (recentNodeId != null) {
            val recentNode = recentContactList.findViewById(recentNodeId) as LinearLayout
            val initial = recentNode.findViewById(R.id.contact_initial) as TextView
            val recentName = recentNode.findViewById(R.id.contact_name) as TextView

            initial.text = new.name[0].toString()
            recentName.text = new.name
        }

    }

    private fun removeContactFromList (userId: UserId) {
        log.debug("in remove contact from list")
        val nodeId = contactListData[userId]
        val recentNodeId = recentContactListData[userId]
        if (nodeId != null) {
            contactListData.remove(userId)
            contactList.removeView(findViewById(nodeId))
        }

        if (recentNodeId != null) {
            recentContactListData.remove(userId)
            recentContactList.removeView(findViewById(recentNodeId))
        }
    }

    private fun handleNewMessage (message: ConversationMessage) {
        val conversationId = message.conversationId
        if (conversationId is ConversationId.User) {
            val contactId = conversationId.id
            val nodeId = recentContactListData[contactId]
            if (nodeId != null) {
                recentContactList.removeView(findViewById(nodeId))
            }

            val contact = contactService.contactList[contactId]
            if (contact != null) {
                recentContactList.addView(createRecentContactNode(contact.id, contact.name), 0)
            }
            else {
                contactService.getContact(contactId) successUi {
                    if (it != null) {
                        recentContactList.addView(createRecentContactNode(it.id, it.name), 0)
                    }
                } fail {
                    log.debug("Failed fetching the contact")
                }
            }

        }
    }

    private fun setListeners () {
        contactService.addContactListener {
            handleContactEvent(it)
        }

        messengerService.addNewMessageListener {
            handleNewMessage(it)
        }
    }

    private fun unsubscribeListeners () {
        contactService.clearListeners()
        messengerService.clearListeners()
    }

    private fun setAppActivity() {
        log.debug("set ui visible")
        app.setCurrentActivity(this, true)
    }

    private fun clearAppActivity() {
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
        unsubscribeListeners()
    }

    override fun onResume() {
        super.onResume()
        init()
        log.debug("onResume")
    }

    override fun onStop() {
        super.onStop()
        log.debug("onStop")
    }

    override fun onDestroy() {
        super.onDestroy()
        clearAppActivity()
        log.debug("onDestroy")
    }
}