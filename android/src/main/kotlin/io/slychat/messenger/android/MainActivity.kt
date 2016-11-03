package io.slychat.messenger.android

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v7.app.AppCompatActivity
import android.util.SparseArray
import android.view.KeyEvent
import android.view.Surface
import android.view.WindowManager
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import com.google.android.gms.common.GoogleApiAvailability
import com.vfpowertech.jsbridge.androidwebengine.AndroidWebEngineInterface
import com.vfpowertech.jsbridge.core.dispatcher.Dispatcher
import io.slychat.messenger.core.SlyBuildConfig
import io.slychat.messenger.core.persistence.ConversationId
import io.slychat.messenger.services.ui.UISelectionDialogResult
import io.slychat.messenger.services.ui.clearAllListenersOnDispatcher
import io.slychat.messenger.services.ui.js.NavigationService
import io.slychat.messenger.services.ui.js.javatojs.NavigationServiceToJSProxy
import io.slychat.messenger.services.ui.registerCoreServicesOnDispatcher
import nl.komponents.kovenant.Deferred
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.deferred
import org.slf4j.LoggerFactory
import rx.Subscription
import java.util.*

class MainActivity : AppCompatActivity() {
    companion object {
        val ACTION_VIEW_MESSAGES = "io.slychat.messenger.android.action.VIEW_MESSAGES"

        val EXTRA_PENDING_MESSAGES_TYPE = "pendingMessagesType"
        val EXTRA_PENDING_MESSAGES_TYPE_SINGLE = "single"
        val EXTRA_PENDING_MESSAGES_TYPE_MULTI = "multi"

        val EXTRA_CONVO_KEY = "conversationKey"

        private val RINGTONE_PICKER_REQUEST_CODE = 1
    }

    private var lastOrientation = Surface.ROTATION_0
    private var lastActivityHeight = 0

    private val log = LoggerFactory.getLogger(javaClass)

    //this is set whether or not initialization was successful
    //since we always quit the application on successful init, there's no need to retry it
    private var isInitialized = false
    private var isActive = false

    private var loadCompleteSubscription: Subscription? = null

    var navigationService: NavigationService? = null
    private lateinit var webView: WebView

    private var nextPermRequestCode = 0
    private val permRequestCodeToDeferred = SparseArray<Deferred<Boolean, Exception>>()

    //only one can run at once
    private var ringtonePickerDeferred: Deferred<UISelectionDialogResult<String?>, Exception>? = null

    /** Returns the initial page to launch after login, if any. Used when invoked via a notification intent. */
    private fun getInitialPage(intent: Intent): String? {
        if (intent.action != ACTION_VIEW_MESSAGES)
            return null

        val messagesType = intent.getStringExtra(EXTRA_PENDING_MESSAGES_TYPE) ?: return null

        val page = when (messagesType) {
            EXTRA_PENDING_MESSAGES_TYPE_SINGLE -> {
                val conversationKey = intent.getStringExtra(EXTRA_CONVO_KEY) ?: throw RuntimeException("Missing EXTRA_CONVO_KEY")
                val notificationKey = ConversationId.fromString(conversationKey)
                when (notificationKey) {
                    is ConversationId.User -> "user/${notificationKey.id}"
                    is ConversationId.Group -> "group/${notificationKey.id}"
                }
            }

            EXTRA_PENDING_MESSAGES_TYPE_MULTI -> "contacts"

            else -> throw RuntimeException("Unexpected value for EXTRA_PENDING_MESSAGES_TYPE: $messagesType")
        }

        return page
    }

    override fun onNewIntent(intent: Intent) {
        log.debug("onNewIntent")
        super.onNewIntent(intent)

        this.intent = intent

        //if the activity was destroyed but a notification caused to be recreated, then let init() handle setting the initial page
        if (!isInitialized)
            return

        val page = getInitialPage(intent) ?: return

        val navigationService = navigationService ?: return

        navigationService.goTo(page) fail { e ->
            log.error("navigationService.goTo failed: {}", e.message, e)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        log.debug("onCreate")
        super.onCreate(savedInstanceState)

        //XXX make optional? enable by default and change on user login after reading config
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        //hide titlebar
        supportActionBar?.hide()

        //display loading screen and wait for app to finish loading
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView) as WebView

        setAppActivity()

        addSoftKeyboardVisibilityListener()
    }

    private fun addSoftKeyboardVisibilityListener() {
        val activityRootView = findViewById(android.R.id.content)!!

        //derived from https://stackoverflow.com/questions/2150078/how-to-check-visibility-of-software-keyboard-in-android
        activityRootView.viewTreeObserver.addOnGlobalLayoutListener {
            val visibleArea = Rect()
            activityRootView.getWindowVisibleDisplayFrame(visibleArea)

            val rootViewHeight = activityRootView.rootView.height
            val activityVisibleHeight = visibleArea.bottom - visibleArea.top
            val heightDiff = rootViewHeight - activityVisibleHeight
            val diffPercent = heightDiff / rootViewHeight.toFloat()

            //this is usually ~0.50%, but I haven't tested it on tablets yet, so this may need tweaking
            val isVisible = diffPercent >= 0.30

            val currentOrientation = getOrientation()

            //on rotation, reset the last recorded activity height
            //the keyboard will always be shown after resize has complete, so this is safe
            if (currentOrientation != lastOrientation) {
                lastActivityHeight = 0
                lastOrientation = currentOrientation
            }

            //we may be called multiple times during rotation for resizes
            //so we just keep the largest activity height
            if (activityVisibleHeight > lastActivityHeight)
                lastActivityHeight = activityVisibleHeight

            val keyboardHeight = if (lastActivityHeight > activityVisibleHeight)
                lastActivityHeight - activityVisibleHeight
            else
                activityVisibleHeight - lastActivityHeight

            AndroidApp.get(this).updateSoftKeyboardVisibility(isVisible, keyboardHeight)
        }
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
        val app = AndroidApp.get(this)

        val initialPage = getInitialPage(intent)
        app.appComponent.uiStateService.initialPage = initialPage

        log.debug("UI initial page: {}", initialPage)

        loadCompleteSubscription?.unsubscribe()
        loadCompleteSubscription = null

        if (SlyBuildConfig.DEBUG)
            WebView.setWebContentsDebuggingEnabled(true)

        webView.settings.javaScriptEnabled = true
        webView.settings.allowFileAccessFromFileURLs = true
        webView.settings.blockNetworkLoads = true

        //Allow javascript to push history state to the webview
        webView.settings.allowUniversalAccessFromFileURLs = true

        initJSLogging(webView)

        val webEngineInterface = AndroidWebEngineInterface(webView)

        val dispatcher = Dispatcher(webEngineInterface)

        registerCoreServicesOnDispatcher(dispatcher, AndroidApp.get(this).appComponent)

        //TODO should init this only once the webview has loaded the page
        webView.setWebViewClient(object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                navigationService = NavigationServiceToJSProxy(dispatcher)
            }
        })

        webView.loadUrl("file:///android_asset/ui/index.html")
    }

    fun hideSplashImage() {
        val splashView = findViewById(R.id.splashImageView)
        if (splashView == null) {
            log.warn("Attempted to hide splash screen twice!")
            return
        }

        val animation = AlphaAnimation(1f, 0f)
        animation.duration = 500
        animation.startOffset = 500
        animation.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationEnd(animation: Animation) {
                val layout = findViewById(R.id.frameLayout) as FrameLayout
                layout.removeViewInLayout(splashView)
            }

            override fun onAnimationRepeat(animation: Animation) {}

            override fun onAnimationStart(animation: Animation) {}
        })

        splashView.startAnimation(animation)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        log.debug("onSaveInstanceState")
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle?) {
        log.debug("onRestoreInstanceState")
        super.onRestoreInstanceState(savedInstanceState)
        webView.restoreState(savedInstanceState)
    }

    private fun setAppActivity() {
        isActive = true
        AndroidApp.get(this).currentActivity = this
    }

    private fun clearAppActivity() {
        isActive = false
        AndroidApp.get(this).currentActivity = null
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

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (navigationService != null)
                navigationService!!.goBack()
            //if we haven't loaded the web ui, we're either still in the loading screen or on some error dialog that'll
            //terminate the app anyways
            else
                finish()

            return true
        }

        return super.onKeyDown(keyCode, event)
    }

    fun requestPermission(permission: String): Promise<Boolean, Exception> {
        val requestCode = nextPermRequestCode
        nextPermRequestCode += 1

        val deferred = deferred<Boolean, Exception>()
        permRequestCodeToDeferred.put(requestCode, deferred)

        ActivityCompat.requestPermissions(this, arrayOf(permission), requestCode)

        return deferred.promise
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        val deferred = permRequestCodeToDeferred[requestCode]

        if (deferred == null) {
            log.error("Got response for unknown request code ({}); permissions={}", requestCode, Arrays.toString(permissions))
            return
        }

        permRequestCodeToDeferred.remove(requestCode)

        val granted = grantResults[0] == PackageManager.PERMISSION_GRANTED

        deferred.resolve(granted)
    }

    /** Capture console.log output into android's log */
    private fun initJSLogging(webView: WebView) {
        val jsLog = LoggerFactory.getLogger("Javascript")
        webView.setWebChromeClient(object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                val msg = "[${consoleMessage.sourceId()}:${consoleMessage.lineNumber()}] ${consoleMessage.message()}"
                when (consoleMessage.messageLevel()) {
                    ConsoleMessage.MessageLevel.DEBUG -> jsLog.debug(msg)
                    ConsoleMessage.MessageLevel.ERROR -> jsLog.error(msg)
                    ConsoleMessage.MessageLevel.LOG -> jsLog.info(msg)
                    ConsoleMessage.MessageLevel.TIP -> jsLog.info(msg)
                    ConsoleMessage.MessageLevel.WARNING -> jsLog.warn(msg)
                }
                return true
            }
        })
    }

    fun openRingtonePicker(previous: String?): Promise<UISelectionDialogResult<String?>, Exception> {
        if (ringtonePickerDeferred != null)
            error("Deferred still pending")

        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)

        val previousRingtoneUri = previous?.let { Uri.parse(it) }

        intent.apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Message notification sound")
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)

            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, previousRingtoneUri)
        }

        startActivityForResult(intent, RINGTONE_PICKER_REQUEST_CODE)

        val deferred = deferred<UISelectionDialogResult<String?>, Exception>()

        ringtonePickerDeferred = deferred

        return deferred.promise
    }

    private fun getRingtoneName(uri: Uri): String {
        val ringtone = RingtoneManager.getRingtone(this, uri)
        return ringtone.getTitle(this)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            RINGTONE_PICKER_REQUEST_CODE -> {
                val deferred = ringtonePickerDeferred
                if (deferred == null) {
                    log.error("No deferred pending for ringtone picker")
                    return
                }

                ringtonePickerDeferred = null

                if (resultCode != Activity.RESULT_OK) {
                    deferred.resolve(UISelectionDialogResult(false, null))
                    return
                }

                //should never occur
                if (data == null) {
                    log.error("No data returned for ringtone picker")
                    deferred.resolve(UISelectionDialogResult(false, null))
                    return
                }

                val uri = data.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)

                val value = uri?.toString()

                deferred.resolve(UISelectionDialogResult(true, value))
            }

            else -> {
                log.error("Unknown request code: {}", requestCode)
            }
        }
    }

    fun inviteToSly(subject: String, text: String, htmlText: String?) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"

            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, text)
            if (htmlText != null)
                putExtra(Intent.EXTRA_HTML_TEXT, htmlText)
        }

        val chooserIntent = Intent.createChooser(intent, "Invite a friend to Sly").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        startActivity(chooserIntent)
    }

    private fun getOrientation(): Int {
        return windowManager.defaultDisplay.rotation
    }

    private fun isLandscape(): Boolean {
        val rotation = getOrientation()
        return rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270
    }
}