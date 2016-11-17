package io.slychat.messenger.android.activites

import android.app.ProgressDialog
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import io.slychat.messenger.android.AndroidApp
import io.slychat.messenger.android.R
import io.slychat.messenger.services.LoginEvent
import org.slf4j.LoggerFactory
import rx.Subscription

class LoginActivity : AppCompatActivity() {
    private val log = LoggerFactory.getLogger(javaClass)

    private lateinit var app : AndroidApp
    private var loginListener : Subscription? = null

    private lateinit var usernameField : EditText
    private lateinit var passwordField : EditText
    private lateinit var submitLoginBtn : Button
    private lateinit var registerLink : View
    private lateinit var rememberMe : Switch

    private lateinit var username : String
    private lateinit var password : String

    private lateinit var progressDialog : ProgressDialog

    override fun onCreate (savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        log.debug("onCreate")

        app = AndroidApp.get(this)

        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_login)

        init()
    }

    private fun init () {
        usernameField = findViewById(R.id.login_username_field) as EditText
        passwordField = findViewById(R.id.login_password_field) as EditText
        submitLoginBtn = findViewById(R.id.login_submit_btn) as Button
        registerLink = findViewById(R.id.login_register_link)
        rememberMe = findViewById(R.id.login_remember_me) as Switch

        progressDialog = ProgressDialog(this)
        progressDialog.setIndeterminate(true)
        progressDialog.setCancelable(false)
        progressDialog.setCanceledOnTouchOutside(false)
        progressDialog.setMessage("We're logging you in")


        createEventListeners()
        setListeners()
    }

    private fun createEventListeners () {
        submitLoginBtn.setOnClickListener {
            handleSubmitLogin()
        }

        registerLink.setOnClickListener {
            startActivity(Intent(baseContext, RegisterActivity::class.java))
        }
    }

    private fun setListeners () {
        loginListener?.unsubscribe()
        loginListener = app.app.loginEvents.subscribe { handleLoginEvent(it) }
    }

    private fun unsubscribeListeners () {
        loginListener?.unsubscribe()
    }

    private fun handleLoginEvent (event: LoginEvent) {
        when (event) {
            is LoginEvent.LoggedIn -> {
                handleLoggedInEvent()
            }
            is LoginEvent.LoggedOut -> { log.debug("logged out") }
            is LoginEvent.LoggingIn -> { log.debug("logging in") }
            is LoginEvent.LoginFailed -> {
                progressDialog.dismiss()
                log.debug("login failed")
                if (event.errorMessage != null) {
                    val message = event.errorMessage as String
                    log.debug(message)
                    if (message == "Phone confirmation needed")
                        loadSmsVerification()
                    else
                        displayErrorMessage(message)
                }
            }
        }
    }

    private fun displayErrorMessage (errorMessage: String) {
        log.debug("displaying error message : " + errorMessage)
        val dialog = AlertDialog.Builder(this).create()
        dialog.setMessage(errorMessage)
        dialog.setButton(AlertDialog.BUTTON_NEGATIVE, "OK", DialogInterface.OnClickListener { dialogInterface, i -> dialog.dismiss() })
        dialog.show()
    }

    private fun handleLoggedInEvent () {
        progressDialog.dismiss()
        val intent = Intent(baseContext, RecentChatActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun handleSubmitLogin () {
        username = usernameField.text.toString()
        password = passwordField.text.toString()
        if (!validate(username, password))
            return

        progressDialog.show()
        app.app.login(username, password, rememberMe.isChecked)
    }

    private fun validate (username: String, password: String): Boolean {
        var valid = true

        if (username.isEmpty()) {
            usernameField.error = "Username is required"
            valid = false
        }

        if (password.isEmpty()) {
            passwordField.error = "Password is required"
            valid = false
        }

        return valid
    }

    private fun loadSmsVerification () {
        val intent = Intent(baseContext, SmsVerificationActivity::class.java)
        intent.putExtra("EXTRA_USERNAME", username)
        intent.putExtra("EXTRA_PASSWORD", password)
        startActivity(intent)
        finish()
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