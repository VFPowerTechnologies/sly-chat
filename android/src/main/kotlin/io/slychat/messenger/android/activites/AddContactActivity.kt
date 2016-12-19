package io.slychat.messenger.android.activites

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
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
import io.slychat.messenger.android.activites.services.impl.ContactServiceImpl
import io.slychat.messenger.core.persistence.ContactInfo
import nl.komponents.kovenant.ui.failUi
import nl.komponents.kovenant.ui.successUi
import org.slf4j.LoggerFactory

class AddContactActivity : AppCompatActivity() {
    private val log = LoggerFactory.getLogger(javaClass)
    private lateinit var contactService: ContactServiceImpl

    private lateinit var app : AndroidApp

    override fun onCreate (savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        log.debug("onCreate")

        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_add_contact)

        app = AndroidApp.get(this)

        contactService = ContactServiceImpl(this)

        val actionBar = findViewById(R.id.my_toolbar) as Toolbar
        actionBar.title = "Add Contact"
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

    private fun createEventListeners () {
        val mSubmitBtn = findViewById(R.id.add_contact_submit_btn) as Button
        mSubmitBtn.setOnClickListener {
            handleAddContact()
        }
    }

    private fun handleAddContact () {
        val mUsernameField = findViewById(R.id.add_contact_field) as EditText
        val username = mUsernameField.text.toString()
        if (username.isEmpty())
            return

        contactService.fetchNewContactInfo(username) successUi { contactInfo ->
            if (contactInfo != null)
                createContactResultNode(contactInfo)
            else {
                log.debug("No contact found for $username")
            }
        } failUi {
            log.debug("Failed To Fetch Contact Info", it.stackTrace)
        }
    }

    private fun createContactResultNode (contactInfo: ContactInfo) {
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

    private fun addContact (contactInfo: ContactInfo) {
        val mUsernameField = findViewById(R.id.add_contact_field) as EditText
        contactService.addContact(contactInfo) successUi { success ->
            if (success) {
                mUsernameField.setText("")
                val intent = Intent(baseContext, ChatActivity::class.java)
                intent.putExtra("EXTRA_ISGROUP", false)
                intent.putExtra("EXTRA_ID", contactInfo.id.long)
                startActivity(intent)
            }
            else {
                log.debug("Failed to add contact ${contactInfo.email}")
            }
        } fail {
            log.debug("Failed to add contact ${contactInfo.email}")
        }
    }

    private fun setAppActivity() {
        log.debug("set ui visible")
        app.setCurrentActivity(this, true)
    }

    private fun clearAppActivity() {
        log.debug("set ui hidden")
        app.setCurrentActivity(this, false)
    }

    override fun onStart () {
        super.onStart()
        log.debug("onStart")
    }

    override fun onPause () {
        super.onPause()
        log.debug("onPause")
        clearAppActivity()
    }

    override fun onResume () {
        super.onResume()
        setAppActivity()
        log.debug("onResume")
    }

    override fun onStop () {
        super.onStop()
        log.debug("onStop")
    }

    override fun onDestroy () {
        super.onDestroy()
        clearAppActivity()
        log.debug("onDestroy")
    }
}