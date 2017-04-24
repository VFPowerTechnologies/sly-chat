package io.slychat.messenger.android.activites

import android.os.Bundle
import android.support.v7.widget.Toolbar
import android.view.MenuItem
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import io.slychat.messenger.android.AndroidApp
import io.slychat.messenger.android.R
import io.slychat.messenger.core.condError
import io.slychat.messenger.core.isNotNetworkError
import nl.komponents.kovenant.ui.failUi
import nl.komponents.kovenant.ui.successUi
import org.slf4j.LoggerFactory

class FeedbackActivity : BaseActivity() {
    private val log = LoggerFactory.getLogger(javaClass)

    private lateinit var mSubmitBtn: Button
    private lateinit var mFeedbackField: EditText
    private lateinit var app: AndroidApp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!successfullyLoaded)
            return

        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_feedback)

        init()
    }

    private fun init() {
        app = AndroidApp.get(this)

        val actionBar = findViewById(R.id.feedback_toolbar) as Toolbar
        actionBar.title = resources.getString(R.string.feedback_title)
        setSupportActionBar(actionBar)

        mSubmitBtn = findViewById(R.id.submit_feedback_btn) as Button
        mFeedbackField = findViewById(R.id.feedback_field) as EditText

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
        mSubmitBtn.setOnClickListener {
            handleFeedback()
        }
    }

    private fun handleFeedback() {
        val feedback = mFeedbackField.text.toString()
        if (feedback.isEmpty())
            return

        app.appComponent.uiFeedbackService.submitFeedback(feedback) successUi {
            mFeedbackField.setText("")
        } failUi {
            log.condError(isNotNetworkError(it), "${it.message}", it)
        }
    }
}