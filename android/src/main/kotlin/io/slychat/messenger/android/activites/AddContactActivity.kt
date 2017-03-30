package io.slychat.messenger.android.activites

import android.content.Intent
import android.os.Bundle
import android.support.v7.widget.Toolbar
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import io.slychat.messenger.android.AndroidApp
import io.slychat.messenger.android.R
import io.slychat.messenger.android.activites.services.impl.AndroidContactServiceImpl
import io.slychat.messenger.core.condError
import io.slychat.messenger.core.isNotNetworkError
import io.slychat.messenger.core.persistence.ContactInfo
import io.slychat.messenger.core.persistence.toConversationId
import nl.komponents.kovenant.ui.failUi
import nl.komponents.kovenant.ui.successUi
import org.slf4j.LoggerFactory

class AddContactActivity : BaseActivity() {
    private val log = LoggerFactory.getLogger(javaClass)
    private lateinit var contactService: AndroidContactServiceImpl

    private lateinit var app: AndroidApp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        log.debug("onCreate")

        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_add_contact)

        app = AndroidApp.get(this)

        contactService = AndroidContactServiceImpl(this)

        val actionBar = findViewById(R.id.add_contact_toolbar) as Toolbar
        actionBar.title = resources.getString(R.string.add_contact_title)
        setSupportActionBar(actionBar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        createEventListeners()
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item?.itemId) {
            android.R.id.home -> { finish() }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun createEventListeners() {
        val mSubmitBtn = findViewById(R.id.add_contact_submit_btn) as Button
        mSubmitBtn.setOnClickListener {
            handleAddContact()
        }
    }

    private fun handleAddContact() {
        val mUsernameField = findViewById(R.id.add_contact_field) as EditText
        val username = mUsernameField.text.toString()
        if (username.isEmpty())
            return

        contactService.fetchNewContactInfo(username) successUi { contactInfo ->
            if (contactInfo != null)
                createContactResultNode(contactInfo)
            else {
                mUsernameField.error = resources.getString(R.string.add_contact_no_result_error)
            }
        } failUi {
            log.condError(isNotNetworkError(it), "${it.message}", it)
        }
    }

    private fun createContactResultNode(contactInfo: ContactInfo) {
        val mContactResultList = findViewById(R.id.add_contact_search_result) as LinearLayout
        mContactResultList.removeAllViews()

        val node = LayoutInflater.from(this).inflate(R.layout.contact_result_node, mContactResultList, false)
        val contactNameNode = node.findViewById(R.id.contact_name) as TextView
        val contactEmailNode = node.findViewById(R.id.contact_email) as TextView
        val contactInitialNode = node.findViewById(R.id.contact_initial) as TextView

        contactNameNode.text = contactInfo.name
        contactEmailNode.text = contactInfo.email
        contactInitialNode.text = contactInfo.name[0].toString()

        val btn = node.findViewById(R.id.add_contact_btn) as Button
        btn.setOnClickListener {
            addContact(contactInfo)
        }

        mContactResultList.addView(node)
    }

    private fun addContact(contactInfo: ContactInfo) {
        val mUsernameField = findViewById(R.id.add_contact_field) as EditText
        contactService.addContact(contactInfo) successUi { success ->
                mUsernameField.setText("")
                val intent = getChatPageIntent(contactInfo.id.toConversationId())
                startActivity(intent)
        } failUi  {
            log.error("Something failed: {}", it.message, it)
        }
    }
}