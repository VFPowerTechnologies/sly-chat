package io.slychat.messenger.android.activites

import android.os.Bundle
import android.support.v7.widget.Toolbar
import android.view.MenuItem
import android.view.WindowManager
import android.widget.TextView
import io.slychat.messenger.android.AndroidApp
import io.slychat.messenger.android.R
import org.slf4j.LoggerFactory

class ContactInfoActivity : BaseActivity() {
    companion object {
        val EXTRA_USER_ID = "io.slychat.messenger.android.activities.ContactInfoActivity.userId"
        val EXTRA_USER_NAME = "io.slychat.messenger.android.activities.ContactInfoActivity.userName"
        val EXTRA_USER_EMAIL = "io.slychat.messenger.android.activities.ContactInfoActivity.userEmail"
        val EXTRA_USER_PUBKEY = "io.slychat.messenger.android.activities.ContactInfoActivity.userPubKey"
    }

    private val log = LoggerFactory.getLogger(javaClass)

    private lateinit var app: AndroidApp

    private var contactName: String? = null
    private var contactEmail: String? = null
    private var contactPubKey: String? = null

    private var emailVal: TextView? = null
    private var nameVal: TextView? = null
    private var publicKeyVal: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!successfullyLoaded)
            return

        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_contact_info)

        contactName = intent.extras[EXTRA_USER_NAME] as String
        contactPubKey = intent.extras[EXTRA_USER_PUBKEY] as String
        contactEmail = intent.extras[EXTRA_USER_EMAIL] as String


        val actionBar = findViewById(R.id.contact_info_toolbar) as Toolbar
        actionBar.title = resources.getString(R.string.contact_info_title)
        setSupportActionBar(actionBar)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        app = AndroidApp.get(this)

        emailVal = findViewById(R.id.contact_info_email_value) as TextView
        nameVal = findViewById(R.id.contact_info_name_value) as TextView
        publicKeyVal = findViewById(R.id.contact_info_public_key_value) as TextView
    }

    private fun init() {
        setAppActivity()
        displayInfo()
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item?.itemId) {
            android.R.id.home -> { finish() }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun displayInfo() {
        emailVal?.text = contactEmail
        nameVal?.text = contactName
        publicKeyVal?.text = contactPubKey
    }

    override fun onResume() {
        super.onResume()
        init()
    }
}