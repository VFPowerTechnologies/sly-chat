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
import io.slychat.messenger.services.ui.UIUpdatePhoneInfo
import nl.komponents.kovenant.ui.failUi
import nl.komponents.kovenant.ui.successUi
import org.slf4j.LoggerFactory

class UpdatePhoneActivity : AppCompatActivity() {
    private val log = LoggerFactory.getLogger(javaClass)

    private lateinit var progressDialog : ProgressDialog

    private lateinit var username : String
    private lateinit var password : String

    private lateinit var phoneField : EditText
    private lateinit var updateSubmitbtn : Button
    private lateinit var cancelLink : View
    private lateinit var verifyLink : View

    override fun onCreate (savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        log.debug("onCreate")

        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_update_phone)

        init()
    }

    private fun init () {
        username = intent.getStringExtra("EXTRA_USERNAME")
        password = intent.getStringExtra("EXTRA_PASSWORD")

        phoneField = findViewById(R.id.update_phone_phone_field) as EditText
        updateSubmitbtn = findViewById(R.id.update_phone_submit_btn) as Button
        cancelLink = findViewById(R.id.update_phone_login_link)
        verifyLink = findViewById(R.id.update_phone_verify_link)

        progressDialog = ProgressDialog(this)
        progressDialog.setIndeterminate(true)
        progressDialog.setCancelable(false)
        progressDialog.setCanceledOnTouchOutside(false)
        progressDialog.setMessage("Updating Phone")

        createEventListeners()
    }

    private fun createEventListeners () {
        updateSubmitbtn.setOnClickListener {
            handleSubmitUpdate()
        }

        cancelLink.setOnClickListener {
            startActivity(Intent(baseContext, LoginActivity::class.java))
            finish()
        }

        verifyLink.setOnClickListener {
            loadSmsVerification()
        }
    }

    private fun handleSubmitUpdate () {
        val phone = phoneField.text.toString()
        if (phone.isEmpty()) {
            phoneField.error = "Phone is required"
            return
        }

        progressDialog.show()

        val app = AndroidApp.get(this)
        app.appComponent.registrationService.updatePhone(UIUpdatePhoneInfo(username, password, phone)) successUi { result ->
            progressDialog.hide()
            if (result.successful) {
                loadSmsVerification()
            }
            else {
                log.debug(result.errorMessage)
            }
        } failUi {
            progressDialog.hide()
            log.debug(it.message, it.stackTrace)
        }

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