package com.vfpowertech.keytap.android

import android.app.Activity
import android.os.Bundle
import android.view.KeyEvent
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import com.vfpowertech.jsbridge.androidwebengine.AndroidWebEngineInterface
import com.vfpowertech.jsbridge.core.dispatcher.Dispatcher
import com.vfpowertech.keytap.android.services.AndroidPlatformInfoService
import com.vfpowertech.keytap.ui.services.impl.ContactsServiceImpl
import com.vfpowertech.keytap.ui.services.impl.HistoryServiceImpl
import com.vfpowertech.keytap.ui.services.impl.LoginServiceImpl
import com.vfpowertech.keytap.ui.services.impl.MessengerServiceImpl
import com.vfpowertech.keytap.ui.services.impl.RegistrationServiceImpl
import com.vfpowertech.keytap.ui.services.js.NavigationService
import com.vfpowertech.keytap.ui.services.js.javatojs.NavigationServiceToJSProxy
import com.vfpowertech.keytap.ui.services.jstojava.RegistrationServiceToJavaProxy
import com.vfpowertech.keytap.ui.services.jstojava.PlatformInfoServiceToJavaProxy
import com.vfpowertech.keytap.ui.services.jstojava.MessengerServiceToJavaProxy
import com.vfpowertech.keytap.ui.services.jstojava.LoginServiceToJavaProxy
import com.vfpowertech.keytap.ui.services.jstojava.ContactsServiceToJavaProxy
import com.vfpowertech.keytap.ui.services.jstojava.HistoryServiceToJavaProxy
import org.slf4j.LoggerFactory

class MainActivity : Activity() {
    private var navigationService: NavigationService? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val webView = findViewById(R.id.webView) as WebView
        webView.settings.javaScriptEnabled = true

        initJSLogging(webView)

        val engineInterface = AndroidWebEngineInterface(webView)
        val dispatcher = Dispatcher(engineInterface)

        val registrationService = RegistrationServiceImpl()
        dispatcher.registerService("RegistrationService", RegistrationServiceToJavaProxy(registrationService,  dispatcher))

        val platformInfoService = AndroidPlatformInfoService()
        dispatcher.registerService("PlatformInfoService", PlatformInfoServiceToJavaProxy(platformInfoService, dispatcher))

        val messengerService = MessengerServiceImpl()
        dispatcher.registerService("MessengerService", MessengerServiceToJavaProxy(messengerService, dispatcher))

        val loginService = LoginServiceImpl()
        dispatcher.registerService("LoginService", LoginServiceToJavaProxy(loginService, dispatcher))

        val contactsService = ContactsServiceImpl()
        dispatcher.registerService("ContactsService", ContactsServiceToJavaProxy(contactsService, dispatcher))

        val historyService = HistoryServiceImpl()
        dispatcher.registerService("HistoryService", HistoryServiceToJavaProxy(historyService, dispatcher))

        //TODO should init this only once the webview has loaded the page
        webView.setWebViewClient(object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                navigationService = NavigationServiceToJSProxy(dispatcher)
            }
        })

        webView.loadUrl("file:///android_asset/ui/index.html")
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