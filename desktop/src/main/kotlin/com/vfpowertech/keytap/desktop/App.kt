package com.vfpowertech.keytap.desktop

import com.fasterxml.jackson.databind.ObjectMapper
import com.vfpowertech.jsbridge.core.dispatcher.Dispatcher
import com.vfpowertech.jsbridge.desktopwebengine.JFXWebEngineInterface
import com.vfpowertech.keytap.desktop.jfx.jsconsole.ConsoleMessageAdded
import com.vfpowertech.keytap.desktop.services.DesktopPlatformInfoService
import com.vfpowertech.keytap.ui.services.LoginService
import com.vfpowertech.keytap.ui.services.RegistrationService
import com.vfpowertech.keytap.ui.services.impl.LoginServiceImpl
import com.vfpowertech.keytap.ui.services.impl.MessengerServiceImpl
import com.vfpowertech.keytap.ui.services.impl.RegistrationServiceImpl
import com.vfpowertech.keytap.ui.services.jstojava.RegistrationServiceToJavaProxy
import com.vfpowertech.keytap.ui.services.jstojava.PlatformInfoServiceToJavaProxy
import com.vfpowertech.keytap.ui.services.jstojava.MessengerServiceToJavaProxy
import com.vfpowertech.keytap.ui.services.jstojava.LoginServiceToJavaProxy
import javafx.application.Application
import javafx.scene.Scene
import javafx.scene.web.WebEngine
import javafx.scene.web.WebView
import javafx.stage.Stage
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
                    val args = arrayOf(message.url, message.line, message.text)
                    if (level == "log")
                        jsLog.info(text, *args)
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
        val webView = WebView()

        val engine = webView.engine

        enableDebugger(engine)

        val engineInterface = JFXWebEngineInterface(engine)
        val dispatcher = Dispatcher(engineInterface)

        val registrationService = RegistrationServiceImpl()
        dispatcher.registerService("RegistrationService", RegistrationServiceToJavaProxy(registrationService,  dispatcher))

        val platformInfoService = DesktopPlatformInfoService()
        dispatcher.registerService("PlatformInfoService", PlatformInfoServiceToJavaProxy(platformInfoService, dispatcher))

        val messengerService = MessengerServiceImpl()
        dispatcher.registerService("MessengerService", MessengerServiceToJavaProxy(messengerService, dispatcher))

        val loginService = LoginServiceImpl()
        dispatcher.registerService("LoginService", LoginServiceToJavaProxy(loginService, dispatcher))

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