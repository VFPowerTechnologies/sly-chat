package com.vfpowertech.keytap.android

import android.app.Activity
import android.os.Bundle
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import com.vfpowertech.jsbridge.androidwebengine.AndroidWebEngineInterface
import com.vfpowertech.jsbridge.core.dispatcher.Dispatcher
import com.vfpowertech.keytap.android.services.AndroidPlatformInfoService
import com.vfpowertech.keytap.ui.services.impl.LoginServiceImpl
import com.vfpowertech.keytap.ui.services.impl.MessengerServiceImpl
import com.vfpowertech.keytap.ui.services.impl.RegistrationServiceImpl
import com.vfpowertech.keytap.ui.services.jstojava.RegistrationServiceToJavaProxy
import com.vfpowertech.keytap.ui.services.jstojava.PlatformInfoServiceToJavaProxy
import com.vfpowertech.keytap.ui.services.jstojava.MessengerServiceToJavaProxy
import com.vfpowertech.keytap.ui.services.jstojava.LoginServiceToJavaProxy
import org.slf4j.LoggerFactory

class MainActivity : Activity() {
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

        webView.loadUrl("file:///android_asset/ui/index.html")
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