package io.slychat.messenger.android.activites

import android.app.ProgressDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import io.slychat.messenger.android.AndroidApp
import io.slychat.messenger.android.R
import io.slychat.messenger.services.LoginEvent
import org.slf4j.LoggerFactory
import rx.Subscription

class LoginActivity : BaseActivity() {
    private val log = LoggerFactory.getLogger(javaClass)

    private var loginListener: Subscription? = null

    private lateinit var app: AndroidApp

    private lateinit var progressDialog: ProgressDialog

    private var username: String? = null
    private var password: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        app = AndroidApp.get(this)

        setContentView(R.layout.activity_login)

        val mRememberMe = findViewById(R.id.login_remember_me) as Switch
        mRememberMe.isChecked = app.appComponent.uiConfigService.getLoginRememberMe()

        initProgressDialog()
        createEventListeners()
    }

    private fun init() {
        setLoginListener()
    }

    private fun createEventListeners() {
        val submitLoginBtn = findViewById(R.id.login_submit_btn) as Button
        submitLoginBtn.setOnClickListener {
            handleSubmitLogin()
        }

        val registerLink = findViewById(R.id.login_register_link)
        registerLink.setOnClickListener {
            startActivity(Intent(baseContext, RegistrationActivity::class.java))
        }

        val forgotPasswdLink = findViewById(R.id.login_forgot_password_link)
        forgotPasswdLink.setOnClickListener {
            startActivity(Intent(baseContext, ForgotPasswordActivity::class.java))
        }

        val mRememberMe = findViewById(R.id.login_remember_me) as Switch
        mRememberMe.setOnCheckedChangeListener { btn, checked ->
            app.appComponent.appConfigService.withEditor {
                loginRememberMe = checked
            }
        }
    }

    private fun initProgressDialog() {
        progressDialog = ProgressDialog(this)
        progressDialog.isIndeterminate = true
        progressDialog.setCancelable(false)
        progressDialog.setCanceledOnTouchOutside(false)
    }

    private fun handleSubmitLogin() {
        val usernameField = findViewById(R.id.login_username_field) as EditText
        val passwordField = findViewById(R.id.login_password_field) as EditText
        val rememberMe = findViewById(R.id.login_remember_me) as Switch

        username = usernameField.text.toString()
        password = passwordField.text.toString()
        if (!validate(username, password))
            return

        showProgressDialog(resources.getString(R.string.login_in_process_message))
        app.app.login(username!!, password!!, rememberMe.isChecked)
    }

    private fun validate (username: String?, password: String?): Boolean {
        var valid = true
        val usernameField = findViewById(R.id.login_username_field) as EditText
        val passwordField = findViewById(R.id.login_password_field) as EditText

        if (username == null || username.isEmpty()) {
            usernameField.error = resources.getString(R.string.login_username_required_error)
            valid = false
        }

        if (password == null || password.isEmpty()) {
            passwordField.error = resources.getString(R.string.login_password_required_error)
            valid = false
        }

        return valid
    }

    private fun setLoginListener() {
        loginListener?.unsubscribe()
        loginListener = app.app.loginEvents.subscribe { handleLoginEvent(it) }
    }

    private fun handleLoginEvent(event: LoginEvent) {
        when(event) {
            is LoginEvent.LoggedIn -> {
                handleLoggedInEvent(event)
            }
            is LoginEvent.LoginFailed -> { handleLoginFailedEvent(event) }
        }
    }

    private fun handleLoggedInEvent(state: LoginEvent.LoggedIn) {
        hideProgressDialog()
        app.accountInfo = state.accountInfo
        app.publicKey = state.publicKey

        startActivity(Intent(baseContext, RecentChatActivity::class.java))
        finish()
    }

    private fun handleLoginFailedEvent(event: LoginEvent.LoginFailed) {
        val error = event.errorMessage
        hideProgressDialog()
        if (error == "Phone confirmation needed")
            startSmsVerification()
        else if (error !== null)
            showLoginError(error)
    }

    fun showLoginError(error: String) {
        val errorView = findViewById(R.id.login_error) as TextView
        errorView.text = error
    }

    fun startSmsVerification() {
        hideProgressDialog()

        val rembemberMe = findViewById(R.id.login_remember_me) as Switch
        val registrationIntent = Intent(baseContext, RegistrationActivity::class.java)

        registrationIntent.putExtra("EXTRA_EMAIL", username)
        registrationIntent.putExtra("EXTRA_PASSWORD", password)
        registrationIntent.putExtra("EXTRA_REMEMBER_ME", rembemberMe.isChecked)
        registrationIntent.action = "sms_verification"
        startActivity(registrationIntent)
    }

    private fun showProgressDialog(message: String) {
        progressDialog.setMessage(message)
        progressDialog.show()
    }

    private fun hideProgressDialog() {
        progressDialog.dismiss()
    }

    private fun unsubscribeListeners() {
        loginListener?.unsubscribe()
    }

    override fun onPause() {
        super.onPause()
        unsubscribeListeners()
    }

    override fun onResume() {
        super.onResume()
        init()
    }
}