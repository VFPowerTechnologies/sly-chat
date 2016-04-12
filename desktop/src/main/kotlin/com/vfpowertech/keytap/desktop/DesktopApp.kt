package com.vfpowertech.keytap.desktop

import com.fasterxml.jackson.databind.ObjectMapper
import com.vfpowertech.jsbridge.core.dispatcher.Dispatcher
import com.vfpowertech.jsbridge.desktopwebengine.JFXWebEngineInterface
import com.vfpowertech.keytap.core.BuildConfig
import com.vfpowertech.keytap.core.persistence.sqlite.loadSQLiteLibraryFromResources
import com.vfpowertech.keytap.desktop.jfx.jsconsole.ConsoleMessageAdded
import com.vfpowertech.keytap.desktop.services.DesktopUIPlatformInfoService
import com.vfpowertech.keytap.desktop.services.DesktopUIPlatformService
import com.vfpowertech.keytap.services.KeyTapApplication
import com.vfpowertech.keytap.services.di.PlatformModule
import com.vfpowertech.keytap.services.ui.createAppDirectories
import com.vfpowertech.keytap.services.ui.registerCoreServicesOnDispatcher
import javafx.application.Application
import javafx.scene.Scene
import javafx.scene.web.WebEngine
import javafx.scene.web.WebView
import javafx.stage.Stage
import nl.komponents.kovenant.jfx.JFXDispatcher
import nl.komponents.kovenant.ui.KovenantUi
import org.slf4j.LoggerFactory
import rx.schedulers.JavaFxScheduler

class DesktopApp : Application() {
    private val app: KeyTapApplication = KeyTapApplication()

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
                    else if (level == "debug")
                        jsLog.debug(text, *args)
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
        javaClass.loadSQLiteLibraryFromResources()

        val webView = WebView()

        webView.isContextMenuEnabled = false

        val engine = webView.engine

        enableDebugger(engine)

        val webEngineInterface = JFXWebEngineInterface(engine)

        val platformInfo = DesktopPlatformInfo()
        createAppDirectories(platformInfo)

        val hostServices = try {
           this.hostServices
        }
        catch (e: ClassNotFoundException) {
            null
        }

        val platformModule = PlatformModule(
            DesktopUIPlatformInfoService(),
            BuildConfig.DESKTOP_SERVER_URLS,
            platformInfo,
            DesktopTelephonyService(),
            DesktopWindowService(primaryStage),
            DesktopPlatformContacts(),
            DesktopNotificationService(),
            DesktopUIPlatformService(hostServices),
            JavaFxScheduler.getInstance()
        )

        app.init(platformModule)

        val appComponent = app.appComponent
        app.userSessionAvailable.subscribe {
            if (it == true)
                onUserSessionCreated()
        }

        val dispatcher = Dispatcher(webEngineInterface)

        registerCoreServicesOnDispatcher(dispatcher, appComponent)

        engine.load(javaClass.getResource("/ui/index.html").toExternalForm())

        primaryStage.scene = Scene(webView,  852.0, 480.0)
        primaryStage.show()

        //temp
        app.updateNetworkStatus(true)

        app.autoLogin()
    }

    private fun onUserSessionCreated() {
        app.userComponent!!.notifierService.init()
    }

    override fun stop() {
        super.stop()

        app.shutdown()
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            launch(DesktopApp::class.java, *args)
        }
    }
}
