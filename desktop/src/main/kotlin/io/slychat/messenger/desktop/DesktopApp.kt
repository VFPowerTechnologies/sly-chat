package io.slychat.messenger.desktop

import com.fasterxml.jackson.databind.ObjectMapper
import com.vfpowertech.jsbridge.core.dispatcher.Dispatcher
import com.vfpowertech.jsbridge.desktopwebengine.JFXWebEngineInterface
import de.codecentric.centerdevice.MenuToolkit
import io.slychat.messenger.core.BuildConfig
import io.slychat.messenger.core.Os
import io.slychat.messenger.core.currentOs
import io.slychat.messenger.core.persistence.sqlite.loadSQLiteLibraryFromResources
import io.slychat.messenger.desktop.jfx.jsconsole.ConsoleMessageAdded
import io.slychat.messenger.desktop.services.DesktopUILoadService
import io.slychat.messenger.desktop.services.DesktopUIPlatformInfoService
import io.slychat.messenger.desktop.services.DesktopUIPlatformService
import io.slychat.messenger.services.SlyApplication
import io.slychat.messenger.services.di.PlatformModule
import io.slychat.messenger.services.ui.createAppDirectories
import io.slychat.messenger.services.ui.registerCoreServicesOnDispatcher
import javafx.animation.FadeTransition
import javafx.application.Application
import javafx.application.Platform
import javafx.scene.Scene
import javafx.scene.control.Alert
import javafx.scene.control.MenuBar
import javafx.scene.control.MenuItem
import javafx.scene.control.SeparatorMenuItem
import javafx.scene.image.Image
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyCodeCombination
import javafx.scene.input.KeyCombination
import javafx.scene.layout.StackPane
import javafx.scene.paint.Color
import javafx.scene.shape.Rectangle
import javafx.scene.web.WebEngine
import javafx.scene.web.WebView
import javafx.stage.Stage
import javafx.util.Duration
import nl.komponents.kovenant.jfx.JFXDispatcher
import nl.komponents.kovenant.ui.KovenantUi
import org.slf4j.LoggerFactory
import rx.schedulers.JavaFxScheduler
import rx.subjects.BehaviorSubject
import javax.crypto.Cipher

class DesktopApp : Application() {
    private val app: SlyApplication = SlyApplication()
    private lateinit var stage: Stage
    private lateinit var webView: WebView
    private lateinit var stackPane: StackPane

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
        //libsignal requires AES256 for message encryption+decryption
        if (isRestrictedCryptography()) {
            val alert = Alert(Alert.AlertType.ERROR)
            alert.title = "Unable to start Sly Chat"
            alert.headerText = "An error occurred during initialization."
            alert.contentText = "Restricted JCE policy detected. Please install Unlimited Strength Jurisdiction Policy Files from Oracle."
            alert.showAndWait()
            Platform.exit()
            return
        }

        stage = primaryStage

        KovenantUi.uiContext {
            dispatcher = JFXDispatcher.instance
        }
        javaClass.loadSQLiteLibraryFromResources()

        val platformInfo = DesktopPlatformInfo()
        createAppDirectories(platformInfo)

        val hostServices = try {
           this.hostServices
        }
        catch (e: ClassNotFoundException) {
            null
        }

        val uiVisibility = BehaviorSubject.create<Boolean>(!stage.isIconified)
        stage.iconifiedProperty().addListener { o, oldV, newV ->
            uiVisibility.onNext(!newV)
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
            DesktopUILoadService(this),
            uiVisibility,
            JavaFxScheduler.getInstance()
        )

        app.init(platformModule)
        app.isInBackground = false

        val appComponent = app.appComponent

        //temp
        app.updateNetworkStatus(true)

        stackPane = StackPane()

        webView = WebView()
        stackPane.children.add(webView)

        webView.isContextMenuEnabled = false

        val engine = webView.engine

        enableDebugger(engine)

        val webEngineInterface = JFXWebEngineInterface(engine)

        val dispatcher = Dispatcher(webEngineInterface)

        registerCoreServicesOnDispatcher(dispatcher, appComponent)

        //TODO loading screen
        val loadingScreen = Rectangle()
        loadingScreen.fill = Color.BLACK
        loadingScreen.heightProperty().bind(primaryStage.heightProperty())
        loadingScreen.widthProperty().bind(primaryStage.widthProperty())
        stackPane.children.add(loadingScreen)

        app.addOnInitListener {
            engine.load(javaClass.getResource("/ui/index.html").toExternalForm())
        }

        javaClass.getResourceAsStream("/sly-messenger.png").use { primaryStage.icons.add(Image(it)) }
        primaryStage.title = "Sly Chat"

        primaryStage.scene = Scene(stackPane, 852.0, 480.0)
        primaryStage.show()

        setupOsxMenu()
    }

    private fun setupOsxMenu() {
        if (currentOs.type != Os.Type.OSX)
            return

        val tk = MenuToolkit.toolkit()

        val appMenuBar = MenuBar()

        val appMenu = tk.createDefaultApplicationMenu("Sly Chat")
        appMenuBar.menus.add(appMenu)

        //TODO
        val prefsItem = MenuItem("Preferences")
        prefsItem.accelerator = KeyCodeCombination(KeyCode.P, KeyCombination.META_DOWN)
        appMenu.items.addAll(1, listOf(
            SeparatorMenuItem(),
            prefsItem,
            SeparatorMenuItem()
        ))

        tk.setGlobalMenuBar(appMenuBar)
    }

    private fun onUserSessionCreated() {
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

        private fun isRestrictedCryptography(): Boolean {
            return Cipher.getMaxAllowedKeyLength("AES") != Integer.MAX_VALUE
        }
    }

    fun uiLoadComplete() {
        val node = stackPane.children[1]
        val fade = FadeTransition(Duration.millis(1000.0), node)
        fade.fromValue = 1.0
        fade.toValue = 0.0

        fade.play()

        fade.setOnFinished {
            stackPane.children.remove(node)
        }
    }
}
