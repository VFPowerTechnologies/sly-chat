package com.vfpowertech.keytap.android

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v7.app.AppCompatActivity
import android.view.KeyEvent
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import com.vfpowertech.jsbridge.androidwebengine.AndroidWebEngineInterface
import com.vfpowertech.jsbridge.core.dispatcher.Dispatcher
import com.vfpowertech.keytap.core.BuildConfig
import com.vfpowertech.keytap.services.ui.js.NavigationService
import com.vfpowertech.keytap.services.ui.js.javatojs.NavigationServiceToJSProxy
import com.vfpowertech.keytap.services.ui.registerCoreServicesOnDispatcher
import nl.komponents.kovenant.Deferred
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.deferred
import org.slf4j.LoggerFactory
import java.util.*

class MainActivity : AppCompatActivity() {
    companion object {
        val ACTION_VIEW_MESSAGES = "com.vfpowertech.keytap.android.action.VIEW_MESSAGES"

        val EXTRA_PENDING_MESSAGES_TYPE = "pendingMessagesType"
        val EXTRA_PENDING_MESSAGES_TYPE_SINGLE = "single"
        val EXTRA_PENDING_MESSAGES_TYPE_MULTI = "multi"

        val EXTRA_USERNAME = "username"
    }

    private val log = LoggerFactory.getLogger(javaClass)

    var navigationService: NavigationService? = null
    private lateinit var webView: WebView

    private var nextPermRequestCode = 0
    private val permRequestCodeToDeferred = HashMap<Int, Deferred<Boolean, Exception>>()

    //TODO
    /** Returns the initial page to launch after login, if any. Used when invoked via a notification intent. */
    private fun getInitialPage(intent: Intent): String? {
        if (intent.action != ACTION_VIEW_MESSAGES)
            return null

        val messagesType = intent.getStringExtra(EXTRA_PENDING_MESSAGES_TYPE)
        val page = when (messagesType) {
            null -> return null
            EXTRA_PENDING_MESSAGES_TYPE_SINGLE -> {
                val username = intent.getStringExtra(EXTRA_USERNAME) ?: throw RuntimeException("Missing EXTRA_USERNAME")
                "user/$username"
            }
            EXTRA_PENDING_MESSAGES_TYPE_MULTI -> "contacts"
            else -> throw RuntimeException("Unexpected value for EXTRA_PENDING_MESSAGES_TYPE: $messagesType")
        }

        return page
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        val page = getInitialPage(intent) ?: return

        val navigationService = navigationService ?: return

        navigationService.goTo(page) fail { e ->
            log.error("navigationService.goTo failed: {}", e.message, e)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //TODO
        //probably need to set some special postLogin value for the ui to access after it's logged in
        getInitialPage(intent)

        //hide titlebar
        supportActionBar?.hide()

        setContentView(R.layout.activity_main)

        if (BuildConfig.DEBUG)
            WebView.setWebContentsDebuggingEnabled(true)

        webView = findViewById(R.id.webView) as WebView
        webView.settings.javaScriptEnabled = true
        webView.settings.allowFileAccessFromFileURLs = true

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

        if (savedInstanceState == null)
            webView.loadUrl("file:///android_asset/ui/index.html")

        setAppActivity()
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle?) {
        super.onRestoreInstanceState(savedInstanceState)
        webView.restoreState(savedInstanceState)
    }

    private fun setAppActivity() {
        AndroidApp.get(this).currentActivity = this
    }

    private fun clearAppActivity() {
        AndroidApp.get(this).currentActivity = null
    }

    override fun onPause() {
        clearAppActivity()
        super.onPause()
    }

    override fun onDestroy() {
        clearAppActivity()
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        setAppActivity()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (navigationService != null) {
                navigationService!!.goBack()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    fun requestPermission(permission: String): Promise<Boolean, Exception> {
        val requestCode = nextPermRequestCode
        nextPermRequestCode += 1

        val deferred = deferred<Boolean, Exception>()
        permRequestCodeToDeferred[requestCode] = deferred

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
                return true;
            }
        })
    }
}