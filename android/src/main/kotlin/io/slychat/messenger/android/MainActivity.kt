package io.slychat.messenger.android

import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import com.google.android.gms.common.GoogleApiAvailability
import io.slychat.messenger.android.activites.*
import io.slychat.messenger.services.LoginEvent
import io.slychat.messenger.services.ui.clearAllListenersOnDispatcher
import org.slf4j.LoggerFactory
import rx.Subscription

class MainActivity : BaseActivity() {
    private var loginListener: Subscription? = null

    private val log = LoggerFactory.getLogger(javaClass)

    //this is set whether or not initialization was successful
    //since we always quit the application on successful init, there's no need to retry it
    private var isInitialized = false

    private var loadCompleteSubscription: Subscription? = null

    private lateinit var app: AndroidApp

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

    private fun unsubscribeListeners() {
        loginListener?.unsubscribe()
    }

    private fun handleLoginEvent(event: LoginEvent) {
        when(event) {
            is LoginEvent.LoggedIn -> {
                handleLoggedInEvent(event)
            }
            is LoginEvent.LoggedOut,
            is LoginEvent.LoginFailed -> {
                startActivity(Intent(baseContext, LoginActivity::class.java))
                finish()
            }
        }
    }

    private fun handleLoggedInEvent(state: LoginEvent.LoggedIn) {
        app.accountInfo = state.accountInfo
        app.publicKey = state.publicKey

        startActivity(Intent(baseContext, RecentChatActivity::class.java))
        finish()
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