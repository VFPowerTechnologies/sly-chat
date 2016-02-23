package com.vfpowertech.keytap.desktop

import com.fasterxml.jackson.databind.ObjectMapper
import com.vfpowertech.jsbridge.core.dispatcher.Dispatcher
import com.vfpowertech.jsbridge.desktopwebengine.JFXWebEngineInterface
import com.vfpowertech.keytap.core.BuildConfig
import com.vfpowertech.keytap.core.persistence.sqlite.SQLitePersistenceManager
import com.vfpowertech.keytap.core.persistence.sqlite.loadSQLiteLibraryFromResources
import com.vfpowertech.keytap.desktop.jfx.jsconsole.ConsoleMessageAdded
import com.vfpowertech.keytap.desktop.services.DesktopPlatformInfoService
import com.vfpowertech.keytap.ui.services.createAppDirectories
import com.vfpowertech.keytap.ui.services.di.DaggerUIServicesComponent
import com.vfpowertech.keytap.ui.services.di.PlatformModule
import com.vfpowertech.keytap.ui.services.registerCoreServicesOnDispatcher
import javafx.application.Application
import javafx.scene.Scene
import javafx.scene.web.WebEngine
import javafx.scene.web.WebView
import javafx.stage.Stage
import nl.komponents.kovenant.jfx.JFXDispatcher
import nl.komponents.kovenant.ui.KovenantUi
import org.slf4j.LoggerFactory

class App : Application() {
    private var sqlitePersistenceManager: SQLitePersistenceManager? = null

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
        javaClass.loadSQLiteLibraryFromResources()

        val webView = WebView()

        val engine = webView.engine

        enableDebugger(engine)

        val engineInterface = JFXWebEngineInterface(engine)
        val dispatcher = Dispatcher(engineInterface)

        val platformInfo = DesktopPlatformInfo()
        createAppDirectories(platformInfo)

        val platformModule = PlatformModule(
            DesktopPlatformInfoService(),
            BuildConfig.DESKTOP_SERVER_URLS,
            platformInfo
        )

        val uiServicesComponent = DaggerUIServicesComponent.builder()
            .platformModule(platformModule)
            .build()

        sqlitePersistenceManager = uiServicesComponent.sqlitePersistenceManager

        registerCoreServicesOnDispatcher(dispatcher, uiServicesComponent)

        engine.load(javaClass.getResource("/ui/index.html").toExternalForm())

        primaryStage.scene = Scene(webView,  852.0, 480.0)
        primaryStage.show()
    }

    override fun stop() {
        super.stop()
        
        sqlitePersistenceManager?.shutdown()
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            launch(App::class.java, *args)
        }
    }
}
