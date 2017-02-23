package io.slychat.messenger.android.activites

import android.content.Intent
import android.os.Bundle
import android.support.v7.widget.Toolbar
import android.view.MenuItem
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import io.slychat.messenger.android.AndroidApp
import io.slychat.messenger.android.R
import org.slf4j.LoggerFactory

class InviteFriendsActivity : BaseActivity() {
    companion object {
        val PLAIN_TEXT_TYPE = "text/plain"
    }
    private val log = LoggerFactory.getLogger(javaClass)

    private lateinit var inviteFriendsBtn: Button
    private lateinit var inviteText: EditText
    private lateinit var app: AndroidApp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        log.debug("onCreate")

        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_invite_friends)

        val actionBar = findViewById(R.id.invite_friends_toolbar) as Toolbar
        actionBar.title = resources.getString(R.string.invite_friends_title)
        setSupportActionBar(actionBar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        inviteFriendsBtn = findViewById(R.id.invite_friends_btn) as Button
        inviteText = findViewById(R.id.invite_friends_field) as EditText

        createEventListeners()

        app = AndroidApp.get(this)
    }

    private fun createEventListeners() {
        inviteFriendsBtn.setOnClickListener {
            inviteFriends()
        }
    }

    private fun inviteFriends() {
        val text = inviteText.text.toString()
        if (text.isEmpty())
            return

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = PLAIN_TEXT_TYPE
            putExtra(Intent.EXTRA_TEXT, text)
        }

        val chooserIntent = Intent.createChooser(intent, resources.getString(R.string.invite_friends_intent_text)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        startActivity(chooserIntent)
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item?.itemId) {
            android.R.id.home -> { finish() }
        }
        return super.onOptionsItemSelected(item)
    }
}