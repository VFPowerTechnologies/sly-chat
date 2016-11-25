package io.slychat.messenger.android.activites

import android.app.ProgressDialog
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.text.BoringLayout
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import io.slychat.messenger.android.AndroidApp
import io.slychat.messenger.android.R
import io.slychat.messenger.services.RegistrationProgress
import io.slychat.messenger.services.RegistrationService
import io.slychat.messenger.services.ui.UIRegistrationInfo
import org.slf4j.LoggerFactory
import rx.Subscription

class RegisterActivity : AppCompatActivity() {
    private val log = LoggerFactory.getLogger(javaClass)

    private var registrationListener : Subscription? = null

    private lateinit var app : AndroidApp
    private lateinit var registrationService : RegistrationService

    private lateinit var nameField : EditText
    private lateinit var emailField : EditText
    private lateinit var passwordField : EditText
    private lateinit var passwordConfField : EditText
    private lateinit var phoneField : EditText

    private lateinit var email : String
    private lateinit var password : String

    private lateinit var registerSubmitBtn : Button
    private lateinit var loginLink : View

    private lateinit var progressDialog : ProgressDialog

    override fun onCreate (savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        log.debug("onCreate")

        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_register)

        init()
    }

    private fun init () {
        app = AndroidApp.get(this)
        registrationService = app.appComponent.registrationService

        nameField = findViewById(R.id.register_name_field) as EditText
        emailField = findViewById(R.id.register_email_field) as EditText
        passwordField = findViewById(R.id.register_password_field) as EditText
        passwordConfField = findViewById(R.id.register_password_conf_field) as EditText
        phoneField = findViewById(R.id.register_phone_field) as EditText
        registerSubmitBtn = findViewById(R.id.register_submit_btn) as Button
        loginLink = findViewById(R.id.register_login_link)

        progressDialog = ProgressDialog(this)
        progressDialog.setIndeterminate(true)
        progressDialog.setCancelable(false)
        progressDialog.setCanceledOnTouchOutside(false)
        progressDialog.setMessage("Registration in progress")

        createEventListeners()
        setListeners()
    }

    private fun createEventListeners () {
        registerSubmitBtn.setOnClickListener {
            log.debug("Registration Submit Clicked")
            handleSubmitRegistration()
        }

        loginLink.setOnClickListener {
            log.debug("Login Link Clicked")
            startActivity(Intent(baseContext, LoginActivity::class.java))
        }
    }

    private fun setListeners () {
        registrationListener?.unsubscribe()
        registrationListener = registrationService.registrationEvents.subscribe { handleRegistrationEvents(it) }
    }

    private fun unsubscribeListener () {
        registrationListener?.unsubscribe()
    }

    private fun handleSubmitRegistration () {
        val registrationInfo = validate()
        if (registrationInfo != null) {
            registrationService.doRegistration(registrationInfo)
            progressDialog.show()
        }
    }

    private fun handleRegistrationEvents (event: RegistrationProgress) {
        when (event) {
            is RegistrationProgress.Complete -> { handleRegistrationComplete(event) }
            is RegistrationProgress.Update -> {
                log.debug("registration update")
                progressDialog.setMessage(event.progressText)
                log.debug(event.progressText)
            }
            is RegistrationProgress.Error -> {
                progressDialog.dismiss()
                log.debug("registration error", event.cause)
            }
            is RegistrationProgress.Waiting -> { log.debug("registration waiting") }
        }
    }

    private fun handleRegistrationComplete (event: RegistrationProgress.Complete) {
        log.debug("registration complete")
        registrationService.resetState()
        progressDialog.dismiss()

        if (event.successful) {
            val intent = Intent(baseContext, SmsVerificationActivity::class.java)
            intent.putExtra("EXTRA_USERNAME", email)
            intent.putExtra("EXTRA_PASSWORD", password)
            startActivity(intent)
            finish()
        }
        else {
            when (event.errorMessage) {
                "phone number is taken" -> {
                    phoneField.error = "Phone Number already in use"
                }
                "email is taken" -> {
                    emailField.error = "Email already in use"
                }
                else -> {
                    log.debug(event.errorMessage)
                    event.validationErrors?.forEach {
                        //TODO handle errors
                        log.debug(it.key + " : " + it.value)
                    }
                }
            }
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

    override fun onStart() {
        super.onStart()
        log.debug("onStart")
    }

    override fun onPause() {
        super.onPause()
        clearAppActivity()
        log.debug("onPause")
        unsubscribeListener()
    }

    override fun onResume() {
        super.onResume()
        setAppActivity()
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

    private fun validate (): UIRegistrationInfo? {
        val name = nameField.text.toString()
        email = emailField.text.toString()
        password = passwordField.text.toString()
        val passwordConf = passwordConfField.text.toString()
        val phone = phoneField.text.toString()
        var valid = true
        val passwordValid : Boolean

        if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailField.error = "Email is not valid"
            valid = false
        }

        if (name.isEmpty()) {
            nameField.error = "Your name is required"
            valid = false
        }

        if (password.isEmpty() || password.length < 6) {
            passwordField.error = "Password must be at least 6 characters"
            valid = false
            passwordValid = false
        }
        else {
            passwordValid = true
        }

        if (passwordConf != password && passwordValid) {
            passwordConfField.error = "Password does not match"
            valid = false
        }

        if (phone.isEmpty() || !android.util.Patterns.PHONE.matcher(phone).matches()) {
            phoneField.error = "Phone number is not valid"
        }

        if (valid)
            return UIRegistrationInfo(name, email, password, phone)
        else
            return null
    }

//    override fun onValidationFailed(errors: List<ValidationError>) {
//        for (error in errors) {
//            val view = error.view
//            val message = error.getCollatedErrorMessage(this)
//
//            // Display error messages ;)
//            if (view is EditText) {
//                view.error = message
//            } else {
//                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
//            }
//        }
//    }
}