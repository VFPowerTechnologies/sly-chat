package io.slychat.messenger.android

import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.WindowManager
import com.google.android.gms.common.GoogleApiAvailability
import io.slychat.messenger.android.activites.LoginActivity
import io.slychat.messenger.android.activites.RecentChatActivity
import io.slychat.messenger.services.LoginEvent
import io.slychat.messenger.services.ui.clearAllListenersOnDispatcher
import org.slf4j.LoggerFactory
import rx.Subscription

class MainActivity : AppCompatActivity() {
    companion object {
        val ACTION_VIEW_MESSAGES = "io.slychat.messenger.android.action.VIEW_MESSAGES"

        val EXTRA_PENDING_MESSAGES_TYPE = "pendingMessagesType"
        val EXTRA_PENDING_MESSAGES_TYPE_SINGLE = "single"
        val EXTRA_PENDING_MESSAGES_TYPE_MULTI = "multi"

        val EXTRA_CONVO_KEY = "conversationKey"
    }

    private var loginListener : Subscription? = null

    private val log = LoggerFactory.getLogger(javaClass)

    //this is set whether or not initialization was successful
    //since we always quit the application on successful init, there's no need to retry it
    private var isInitialized = false

    private var loadCompleteSubscription: Subscription? = null

    private lateinit var app: AndroidApp
//
//    /** Returns the initial page to launch after login, if any. Used when invoked via a notification intent. */
//    private fun getInitialPage(intent: Intent): String? {
//        if (intent.action != ACTION_VIEW_MESSAGES)
//            return null
//
//        val messagesType = intent.getStringExtra(EXTRA_PENDING_MESSAGES_TYPE) ?: return null
//
//        val page = when (messagesType) {
//            EXTRA_PENDING_MESSAGES_TYPE_SINGLE -> {
//                val conversationKey = intent.getStringExtra(EXTRA_CONVO_KEY) ?: throw RuntimeException("Missing EXTRA_CONVO_KEY")
//                val notificationKey = ConversationId.fromString(conversationKey)
//                when (notificationKey) {
//                    is ConversationId.User -> "user/${notificationKey.id}"
//                    is ConversationId.Group -> "group/${notificationKey.id}"
//                }
//            }
//
//            EXTRA_PENDING_MESSAGES_TYPE_MULTI -> "contacts"
//
//            else -> throw RuntimeException("Unexpected value for EXTRA_PENDING_MESSAGES_TYPE: $messagesType")
//        }
//
//        return page
//    }

    override fun onNewIntent(intent: Intent) {
        log.debug("onNewIntent")
        super.onNewIntent(intent)

        this.intent = intent

        //if the activity was destroyed but a notification caused to be recreated, then let init() handle setting the initial page
        if (!isInitialized)
            return
    }

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

    private fun setLoginListener () {
        val app = AndroidApp.get(this)
        loginListener?.unsubscribe()
        loginListener = app.app.loginEvents.subscribe {
            handleLoginEvent(it)
        }
    }

    private fun unsubscribeListeners () {
        loginListener?.unsubscribe()
    }

    private fun handleLoginEvent (event: LoginEvent) {
        when (event) {
            is LoginEvent.LoggedIn -> {
                handleLoggedInEvent(event)
            }
            is LoginEvent.LoggedOut -> { handleLoggedOutEvent() }
            is LoginEvent.LoggingIn -> { log.debug("logging in") }
            is LoginEvent.LoginFailed -> { log.debug("login failed") }
        }
    }

    private fun handleLoggedInEvent (state: LoginEvent.LoggedIn) {
        log.debug("logged in")
        app.accountInfo = state.accountInfo
        app.publicKey = state.publicKey
        val intent = Intent(baseContext, RecentChatActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun handleLoggedOutEvent () {
        log.debug("logged out")
        val intent = Intent(baseContext, LoginActivity::class.java)
        startActivity(intent)
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

    private fun setAppActivity() {
        app.setCurrentActivity(this, true)
    }

    private fun clearAppActivity() {
        app.setCurrentActivity(this, false)
    }


    override fun onRestart() {
        log.debug("onRestart")
        super.onRestart()
    }

    override fun onStop() {
        log.debug("onStop")
        super.onStop()
    }

    override fun onPause() {
        log.debug("onPause")
        clearAppActivity()
        unsubscribeListeners()
        super.onPause()

        val sub = loadCompleteSubscription
        if (sub != null) {
            sub.unsubscribe()
            loadCompleteSubscription = null
        }
    }

    override fun onDestroy() {
        log.debug("onDestroy")

        clearAppActivity()
        clearAllListenersOnDispatcher(AndroidApp.get(this).appComponent)

        super.onDestroy()
    }

    override fun onResume() {
        log.debug("onResume")
        super.onResume()
        setAppActivity()

        if (!isInitialized)
            subToLoadComplete()
    }
}