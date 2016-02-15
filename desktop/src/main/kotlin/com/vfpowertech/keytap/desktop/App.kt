package com.vfpowertech.keytap.desktop

import com.fasterxml.jackson.databind.ObjectMapper
import com.vfpowertech.jsbridge.core.dispatcher.Dispatcher
import com.vfpowertech.jsbridge.desktopwebengine.JFXWebEngineInterface
import com.vfpowertech.keytap.core.BuildConfig
import com.vfpowertech.keytap.desktop.jfx.jsconsole.ConsoleMessageAdded
import com.vfpowertech.keytap.desktop.services.DesktopPlatformInfoService
import com.vfpowertech.keytap.ui.services.LoginService
import com.vfpowertech.keytap.ui.services.RegistrationService
import com.vfpowertech.keytap.ui.services.dummy.DummyContactsService
import com.vfpowertech.keytap.ui.services.dummy.DevelServiceImpl
import com.vfpowertech.keytap.ui.services.dummy.DummyHistoryService
import com.vfpowertech.keytap.ui.services.dummy.DummyLoginService
import com.vfpowertech.keytap.ui.services.dummy.MessengerServiceImpl
import com.vfpowertech.keytap.ui.services.dummy.DummyRegistrationService
import com.vfpowertech.keytap.ui.services.impl.LoginServiceImpl
import com.vfpowertech.keytap.ui.services.impl.RegistrationServiceImpl
import com.vfpowertech.keytap.ui.services.jstojava.RegistrationServiceToJavaProxy
import com.vfpowertech.keytap.ui.services.jstojava.PlatformInfoServiceToJavaProxy
import com.vfpowertech.keytap.ui.services.jstojava.MessengerServiceToJavaProxy
import com.vfpowertech.keytap.ui.services.jstojava.LoginServiceToJavaProxy
import com.vfpowertech.keytap.ui.services.jstojava.ContactsServiceToJavaProxy
import com.vfpowertech.keytap.ui.services.jstojava.HistoryServiceToJavaProxy
import com.vfpowertech.keytap.ui.services.jstojava.DevelServiceToJavaProxy
import javafx.application.Application
import javafx.scene.Scene
import javafx.scene.web.WebEngine
import javafx.scene.web.WebView
import javafx.stage.Stage
import nl.komponents.kovenant.jfx.JFXDispatcher
import nl.komponents.kovenant.ui.KovenantUi
import org.slf4j.LoggerFactory

class App : Application() {
    /** Enable the (hidden) debugger WebEngine feature */
    private fun enableDebugger(engine: WebEngine) {
        val objectMapper = ObjectMapper()

        val debugger = engine.impl_getDebugger()
        debugger.isEnabled = true
        val jsLog = LoggerFactory.getLogger("Javascript")
        debugger.setMessageCallback { msg ->
            val root = objectMapper.readTree(msg)
            if (root.has("method")) {
                val method = root.get("method").asText()
                if (method == "Console.messageAdded") {
                    val message = objectMapper.convertValue(root.get("params"), ConsoleMessageAdded::class.java).message
                    val level = message.level
                    val text = "[{}:{}] {}"
                    val args = arrayOf(message.url ?: "unknown", message.line, message.text)
                    if (level == "log")
                        jsLog.info(text, *args)
                    else if (level == "warning")
                        jsLog.warn(text, *args)
                    else if (level == "error")
                        jsLog.error(text, *args)
                    else
                        println("Unknown level: $level")

                }
            }
            null
        }
        debugger.sendMessage("{\"id\": 1, \"method\": \"Console.enable\"}")
    }

    override fun start(primaryStage: Stage) {
        KovenantUi.uiContext {
            dispatcher = JFXDispatcher.instance
        }

        val webView = WebView()

        val engine = webView.engine

        enableDebugger(engine)

        val engineInterface = JFXWebEngineInterface(engine)
        val dispatcher = Dispatcher(engineInterface)

        val serverUrls = BuildConfig.DESKTOP_SERVER_URLS

        //val registrationService = DummyRegistrationService()
        val registrationService = RegistrationServiceImpl(serverUrls.API_SERVER)
        dispatcher.registerService("RegistrationService", RegistrationServiceToJavaProxy(registrationService,  dispatcher))

        val platformInfoService = DesktopPlatformInfoService()
        dispatcher.registerService("PlatformInfoService", PlatformInfoServiceToJavaProxy(platformInfoService, dispatcher))

        //val loginService = DummyLoginService()
        val loginService = LoginServiceImpl(serverUrls.API_SERVER)
        dispatcher.registerService("LoginService", LoginServiceToJavaProxy(loginService, dispatcher))

        val contactsService = DummyContactsService()
        dispatcher.registerService("ContactsService", ContactsServiceToJavaProxy(contactsService, dispatcher))

        val messengerService = MessengerServiceImpl(contactsService)
        dispatcher.registerService("MessengerService", MessengerServiceToJavaProxy(messengerService, dispatcher))

        val historyService = DummyHistoryService()
        dispatcher.registerService("HistoryService", HistoryServiceToJavaProxy(historyService, dispatcher))

        val develService = DevelServiceImpl(messengerService)
        dispatcher.registerService("DevelService", DevelServiceToJavaProxy(develService, dispatcher))

        engine.load(javaClass.getResource("/ui/index.html").toExternalForm())

        primaryStage.scene = Scene(webView,  852.0, 480.0)
        primaryStage.show()
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            launch(App::class.java, *args)
        }
    }
}
