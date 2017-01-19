package io.slychat.messenger.android

import android.app.AlertDialog
import android.app.Dialog
import android.app.ProgressDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import com.google.android.gms.common.GoogleApiAvailability
import io.slychat.messenger.android.activites.*
import io.slychat.messenger.services.LoginEvent
import io.slychat.messenger.services.RegistrationProgress
import io.slychat.messenger.services.ui.clearAllListenersOnDispatcher
import org.slf4j.LoggerFactory
import rx.Subscription

class MainActivity : BaseActivity() {
    private var loginListener: Subscription? = null
    private var registrationListener: Subscription? = null

    private val log = LoggerFactory.getLogger(javaClass)

    //this is set whether or not initialization was successful
    //since we always quit the application on successful init, there's no need to retry it
    private var isInitialized = false

    private var loadCompleteSubscription: Subscription? = null

    private lateinit var app: AndroidApp

    lateinit var progressDialog: ProgressDialog

    data class RegistrationInfo(
        var name: String,
        var phoneNumber: String,
        var email: String,
        var password: String
    )

    var registrationInfo = RegistrationInfo("", "", "", "")

    override fun onCreate(savedInstanceState: Bundle?) {
        log.debug("onCreate")
        super.onCreate(savedInstanceState)

        app = AndroidApp.get(this)
        //XXX make optional? enable by default and change on user login after reading config
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        //hide titlebar
        supportActionBar?.hide()

        //display loading screen and wait for app to finish loading
        setContentView(R.layout.activity_main)

        progressDialog = ProgressDialog(this)
        progressDialog.isIndeterminate = true
        progressDialog.setCancelable(false)
        progressDialog.setCanceledOnTouchOutside(false)
    }

    private fun subToLoadComplete() {
        if (loadCompleteSubscription != null)
            return

        val app = AndroidApp.get(this)
        loadCompleteSubscription = app.loadComplete.subscribe { loadError ->
            isInitialized = true

            if (loadError == null)
                init()
            else
                handleLoadError(loadError)
        }
    }

    private fun handleLoadError(loadError: LoadError) {
        val dialog = when (loadError.type) {
            LoadErrorType.NO_PLAY_SERVICES -> handlePlayServicesError(loadError.errorCode)
            LoadErrorType.SSL_PROVIDER_INSTALLATION_FAILURE -> handleSslProviderInstallationFailure(loadError.errorCode)
            LoadErrorType.UNKNOWN -> handleUnknownLoadError(loadError.cause)
        }

        dialog.setOnDismissListener {
            finish()
        }

        dialog.show()
    }

    private fun getInitFailureDialog(message: String): AlertDialog {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Initialization Failure")
        builder.setPositiveButton("Close Application", { dialog, id ->
            finish()
        })

        builder.setMessage(message)

        return builder.create()
    }

    private fun handleUnknownLoadError(cause: Throwable?): AlertDialog {
        val message = if (cause != null)
            "An unexpected error occured: ${cause.message}"
        else
            //XXX shouldn't happen
            "An unknown error occured but not information is available"

        return getInitFailureDialog(message)
    }

    private fun handleSslProviderInstallationFailure(errorCode: Int): Dialog {
        return GoogleApiAvailability.getInstance().getErrorDialog(this, errorCode, 0)
    }

    private fun handlePlayServicesError(errorCode: Int): Dialog {
        val apiAvailability = GoogleApiAvailability.getInstance()

        return if (apiAvailability.isUserResolvableError(errorCode))
            apiAvailability.getErrorDialog(this, errorCode, 0)
        else
            getInitFailureDialog("Unsupported device")
    }

    private fun init() {
        setLoginListener()
    }

    private fun setLoginListener() {
        loginListener?.unsubscribe()
        loginListener = app.app.loginEvents.subscribe { handleLoginEvent(it) }
    }

    fun setRegistrationListener() {
        registrationListener?.unsubscribe()
        registrationListener = app.appComponent.registrationService.registrationEvents.subscribe { handleRegistrationEvents(it) }
    }


    private fun unsubscribeListeners() {
        registrationListener?.unsubscribe()
        loginListener?.unsubscribe()
    }

    private fun handleLoginEvent(event: LoginEvent) {
        when(event) {
            is LoginEvent.LoggedIn -> {
                handleLoggedInEvent(event)
            }
            is LoginEvent.LoggedOut -> { handleLoggedOutEvent() }
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
            startSmsVerification("login")

        val fragment = supportFragmentManager.findFragmentById(R.id.main_frag_container)
        if (fragment !is LoginFragment || error == null)
            return

        fragment.showLoginError(error)
    }

    private fun handleLoggedOutEvent () {
        var fragment = supportFragmentManager.findFragmentById(R.id.main_frag_container)
        if (fragment == null) {
            fragment = LoginFragment()
            fragment.view?.isFocusableInTouchMode = true
            fragment.view?.requestFocus()

            supportFragmentManager.beginTransaction().add(R.id.main_frag_container, LoginFragment()).commit()
            showFragContainer()
        }
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

        fragment.view?.isFocusableInTouchMode = true
        fragment.view?.requestFocus()
        fragment.arguments = bundle
        supportFragmentManager.beginTransaction().replace(R.id.main_frag_container, fragment).addToBackStack(fragmentId).commit()
    }

    private fun showFragContainer() {
        val splashImage = findViewById(R.id.splashImageView) as LinearLayout
        splashImage.visibility = View.GONE

        val container = findViewById(R.id.main_frag_container) as LinearLayout
        container.visibility = View.VISIBLE
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

    override fun onBackPressed() {
        if(supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStack()
        }
        else {
            super.onBackPressed()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        log.debug("onSaveInstanceState")
        super.onSaveInstanceState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle?) {
        log.debug("onRestoreInstanceState")
        super.onRestoreInstanceState(savedInstanceState)
    }

    override fun onPause() {
        super.onPause()
        unsubscribeListeners()

        val sub = loadCompleteSubscription
        if (sub != null) {
            sub.unsubscribe()
            loadCompleteSubscription = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        clearAllListenersOnDispatcher(AndroidApp.get(this).appComponent)
    }

    override fun onResume() {
        super.onResume()
        if (!isInitialized)
            subToLoadComplete()
    }
}