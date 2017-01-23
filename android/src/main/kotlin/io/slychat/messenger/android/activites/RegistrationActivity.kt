package io.slychat.messenger.android.activites

import android.app.ProgressDialog
import android.content.Intent
import android.os.Bundle
import io.slychat.messenger.android.AndroidApp
import io.slychat.messenger.android.R
import io.slychat.messenger.services.LoginEvent
import io.slychat.messenger.services.RegistrationProgress
import org.slf4j.LoggerFactory
import rx.Subscription

class RegistrationActivity : BaseActivity() {
    private val log = LoggerFactory.getLogger(javaClass)

    private var loginListener: Subscription? = null
    private var registrationListener: Subscription? = null

    private lateinit var app: AndroidApp

    private lateinit var progressDialog: ProgressDialog

    data class RegistrationInfo(
            var name: String,
            var phoneNumber: String,
            var email: String,
            var password: String
    )

    var registrationInfo = RegistrationInfo("", "", "", "")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        app = AndroidApp.get(this)

        setContentView(R.layout.activity_registration)

        var fragment = supportFragmentManager.findFragmentById(R.id.main_frag_container)
        if (fragment == null) {
            if (intent.action == "sms_verification") {
                val email = intent.getStringExtra("EXTRA_EMAIL")
                val password = intent.getStringExtra("EXTRA_PASSWORD")
                val rememberMe = intent.getBooleanExtra("EXTRA_REMEMBER_ME", false)
                fragment = SmsVerificationFragment()
                val bundle = Bundle()
                bundle.putString("EXTRA_EMAIL", email)
                bundle.putString("EXTRA_PASSWORD", password)
                bundle.putBoolean("EXTRA_REMEMBER_ME", rememberMe)

                fragment.view?.isFocusableInTouchMode = true
                fragment.view?.requestFocus()
                fragment.arguments = bundle
            }
            else {
                fragment = RegistrationOneFragment()
                fragment.view?.isFocusableInTouchMode = true
                fragment.view?.requestFocus()
            }

            supportFragmentManager.beginTransaction().add(R.id.main_frag_container, fragment).commit()
        }

        initProgressDialog()
    }

    private fun init() {
        setLoginListener()
        setRegistrationListener()
    }

    private fun initProgressDialog() {
        progressDialog = ProgressDialog(this)
        progressDialog.isIndeterminate = true
        progressDialog.setCancelable(false)
        progressDialog.setCanceledOnTouchOutside(false)
    }

    private fun setLoginListener() {
        loginListener?.unsubscribe()
        loginListener = app.app.loginEvents.subscribe { handleLoginEvent(it) }
    }

    private fun setRegistrationListener() {
        registrationListener?.unsubscribe()
        registrationListener = app.appComponent.registrationService.registrationEvents.subscribe { handleRegistrationEvents(it) }
    }

    private fun handleRegistrationEvents (event: RegistrationProgress) {
        when (event) {
            is RegistrationProgress.Complete -> { handleRegistrationComplete(event) }
            is RegistrationProgress.Update -> {
                setProgressDialogMessage(event.progressText)
            }
            is RegistrationProgress.Error -> {
                hideProgressDialog()
                log.debug("registration error", event.cause)
            }
            is RegistrationProgress.Waiting -> { log.debug("registration waiting") }
        }
    }

    private fun handleRegistrationComplete (event: RegistrationProgress.Complete) {
        app.appComponent.registrationService.resetState()
        hideProgressDialog()

        if (event.successful) {
            startSmsVerification("registration")
        }
        else {
            log.error(event.errorMessage)
        }
    }

    fun startSmsVerification(fragmentId: String) {
        hideProgressDialog()
        val fragment = SmsVerificationFragment()
        val bundle = Bundle()
        bundle.putString("EXTRA_EMAIL", registrationInfo.email)
        bundle.putString("EXTRA_PASSWORD", registrationInfo.password)
        bundle.putBoolean("EXTRA_REMEMBER_ME", false)

        fragment.view?.isFocusableInTouchMode = true
        fragment.view?.requestFocus()
        fragment.arguments = bundle
        supportFragmentManager.beginTransaction().replace(R.id.main_frag_container, fragment).addToBackStack(fragmentId).commit()
    }

    private fun handleLoginEvent(event: LoginEvent) {
        when(event) {
            is LoginEvent.LoggedIn -> {
                handleLoggedInEvent(event)
            }
        }
    }

    private fun handleLoggedInEvent(state: LoginEvent.LoggedIn) {
        hideProgressDialog()
        app.accountInfo = state.accountInfo
        app.publicKey = state.publicKey

        finishAffinity()
        startActivity(Intent(baseContext, RecentChatActivity::class.java))
    }

    private fun unsubscribeListeners() {
        registrationListener?.unsubscribe()
        loginListener?.unsubscribe()
    }

    fun showProgressDialog(message: String) {
        progressDialog.setMessage(message)
        progressDialog.show()
    }

    fun hideProgressDialog() {
        progressDialog.dismiss()
    }

    fun setProgressDialogMessage(message: String) {
        progressDialog.setMessage(message)
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