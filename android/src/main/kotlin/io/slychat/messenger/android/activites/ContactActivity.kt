package io.slychat.messenger.android.activites

import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.support.design.widget.FloatingActionButton
import android.support.v7.app.AppCompatActivity
import android.view.WindowManager
import android.support.v7.widget.AppCompatImageButton
import android.support.v7.widget.Toolbar
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MenuItem
import android.widget.LinearLayout
import android.widget.TextView
import io.slychat.messenger.android.AndroidApp
import io.slychat.messenger.android.R
import io.slychat.messenger.core.UserId
import io.slychat.messenger.services.ui.UIContactsService
import io.slychat.messenger.services.ui.UIConversation
import io.slychat.messenger.services.ui.UIMessengerService
import nl.komponents.kovenant.ui.successUi
import org.slf4j.LoggerFactory

class ContactActivity : AppCompatActivity() {
    private val log = LoggerFactory.getLogger(javaClass)

    private lateinit var app : AndroidApp
    private lateinit var messengerService : UIMessengerService

    private lateinit var conversations : List<UIConversation>

    private lateinit var recentContactList : LinearLayout
    private lateinit var contactList : LinearLayout
    private lateinit var addContactBtn : FloatingActionButton

    override fun onCreate (savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        log.debug("onCreate")

        app = AndroidApp.get(this)
        messengerService = app.appComponent.uiMessengerService

//        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_contact)

        init()
    }

    private fun init () {
        contactList = findViewById(R.id.contact_list) as LinearLayout
        recentContactList = findViewById(R.id.recent_contact_list) as LinearLayout
        addContactBtn = findViewById(R.id.contacts_add_contact_btn) as FloatingActionButton

        val actionBar = findViewById(R.id.my_toolbar) as Toolbar
        actionBar.title = "Address Book"
        setSupportActionBar(actionBar)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        createEventListeners()
        setListeners()

        fetchConversations()
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
        messengerService.getConversations() successUi {
            conversations = it
            displayContacts()
        }
    }

    private fun displayContacts () {
        if (conversations.isNotEmpty()) {
            conversations.forEach {
                createContactNode(it)
                if (it.status.lastMessage != null)
                    createRecentContactNode(it)
            }
        }
        else {
            LayoutInflater.from(this).inflate(R.layout.contact_empty_node, contactList, true)
        }
    }

    private fun createContactNode (conversation: UIConversation) {
        val contactNode = LayoutInflater.from(this).inflate(R.layout.contact_node, contactList, false)
        val contactNameNode = contactNode.findViewById(R.id.contact_name) as TextView
        val contactEmailNode = contactNode.findViewById(R.id.contact_email) as TextView
        contactNameNode.text = conversation.contact.name
        contactEmailNode.text = conversation.contact.email

        contactNode.setOnClickListener {
            loadChatPageFor(conversation.contact.id)
        }

        contactList.addView(contactNode)
    }

    private fun createRecentContactNode (conversation: UIConversation) {
        val recentContactNode = LayoutInflater.from(this).inflate(R.layout.recent_contact_node, recentContactList, false)
        val contactNameNode = recentContactNode.findViewById(R.id.contact_name) as TextView
        val contactInitial = recentContactNode.findViewById(R.id.contact_initial) as TextView
        contactNameNode.text = conversation.contact.name
        contactInitial.text = conversation.contact.name[0].toString()

        recentContactNode.setOnClickListener {
            loadChatPageFor(conversation.contact.id)
        }

        recentContactList.addView(recentContactNode)
    }

    private fun loadChatPageFor (id: UserId) {
        val intent = Intent(baseContext, ChatActivity::class.java)
        intent.putExtra("EXTRA_USERID", id.long)
        startActivity(intent)
    }

    private fun setListeners () {
    }

    private fun unsubscribeListeners () {
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