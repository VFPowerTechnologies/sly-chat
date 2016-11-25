package io.slychat.messenger.android.activites

import android.app.ProgressDialog
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import io.slychat.messenger.android.AndroidApp
import io.slychat.messenger.android.R
import io.slychat.messenger.services.LoginEvent
import nl.komponents.kovenant.ui.failUi
import nl.komponents.kovenant.ui.successUi
import org.slf4j.LoggerFactory
import rx.Subscription

class SmsVerificationActivity : AppCompatActivity() {
    private val log = LoggerFactory.getLogger(javaClass)

    private lateinit var app : AndroidApp
    private lateinit var progressDialog : ProgressDialog

    private lateinit var username : String
    private lateinit var password : String

    private lateinit var smsCodeField : EditText
    private lateinit var smsSubmitBtn : Button
    private lateinit var updatePhoneLink : View
    private lateinit var resendCodeLink : View

    private var loginListener : Subscription? = null

    override fun onCreate (savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        log.debug("onCreate")

        username = intent.getStringExtra("EXTRA_USERNAME")
        password = intent.getStringExtra("EXTRA_PASSWORD")

        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_sms_verification)

        init()
    }

    private fun init () {
        app = AndroidApp.get(this)

        progressDialog = ProgressDialog(this)
        progressDialog.setIndeterminate(true)
        progressDialog.setCancelable(false)
        progressDialog.setCanceledOnTouchOutside(false)
        progressDialog.setMessage("We're logging you in")

        smsCodeField = findViewById(R.id.sms_verification_code_field) as EditText
        smsSubmitBtn = findViewById(R.id.sms_verification_submit_btn) as Button
        updatePhoneLink = findViewById(R.id.sms_verification_update_phone_link)
        resendCodeLink = findViewById(R.id.sms_verification_resend_link)

        setListeners()
        createEventListeners()
    }

    private fun createEventListeners () {
        smsSubmitBtn.setOnClickListener {
            handleSmsSubmit()
        }

        updatePhoneLink.setOnClickListener {
            val intent = Intent(baseContext, UpdatePhoneActivity::class.java)
            intent.putExtra("EXTRA_USERNAME", username)
            intent.putExtra("EXTRA_PASSWORD", password)
            startActivity(intent)
        }

        resendCodeLink.setOnClickListener {
            app.appComponent.registrationService.resendVerificationCode(username)
        }
    }

    private fun setListeners () {
        loginListener?.unsubscribe()
        loginListener = app.app.loginEvents.subscribe { handleLoginEvents(it) }
    }

    private fun unsubscribeListener () {
        loginListener?.unsubscribe()
    }

    private fun handleSmsSubmit () {
        val code = smsCodeField.text.toString()
        if (code.isEmpty()) {
            smsCodeField.error = "Verification Code is required"
            return
        }

        progressDialog.setMessage("Sending Verification Code")
        progressDialog.show()
        app.appComponent.registrationService.submitVerificationCode(username, code) successUi  { result ->
            if (result.successful) {
                progressDialog.setMessage("We're logging you in")
                loginUser()
            }
            else {
                progressDialog.dismiss()
                if (result.errorMessage != null && result.errorMessage == "invalid code") {
                    smsCodeField.error = "Invalid Code"
                }
            }
        } failUi {
            progressDialog.dismiss()
            log.debug(it.message, it.stackTrace)
        }
    }

    private fun loginUser () {
        app.app.login(username, password, false)
    }

    private fun handleLoginEvents (event: LoginEvent) {
        when (event) {
            is LoginEvent.LoggedIn -> {
                handleLoggedInEvent()
            }
            is LoginEvent.LoggedOut -> { log.debug("logged out") }
            is LoginEvent.LoggingIn -> { log.debug("logging in") }
            is LoginEvent.LoginFailed -> {
                progressDialog.dismiss()
                log.debug("login failed")
            }
        }
    }

    private fun handleLoggedInEvent () {
        progressDialog.dismiss()
        val intent = Intent(baseContext, RecentChatActivity::class.java)
        startActivity(intent)
        finish()
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
        clearAppActivity()
        log.debug("onPause")
        unsubscribeListener()
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