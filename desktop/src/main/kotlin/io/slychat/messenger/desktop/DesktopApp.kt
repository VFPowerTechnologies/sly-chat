package io.slychat.messenger.desktop

import com.fasterxml.jackson.databind.ObjectMapper
import com.vfpowertech.jsbridge.core.dispatcher.Dispatcher
import com.vfpowertech.jsbridge.desktopwebengine.JFXWebEngineInterface
import de.codecentric.centerdevice.MenuToolkit
import io.slychat.messenger.core.Os
import io.slychat.messenger.core.SlyBuildConfig
import io.slychat.messenger.core.currentOs
import io.slychat.messenger.core.persistence.sqlite.loadSQLiteLibraryFromResources
import io.slychat.messenger.desktop.jfx.jsconsole.ConsoleMessageAdded
import io.slychat.messenger.desktop.jna.CLibrary
import io.slychat.messenger.desktop.services.*
import io.slychat.messenger.desktop.ui.SplashImage
import io.slychat.messenger.services.SlyApplication
import io.slychat.messenger.services.config.UserConfig
import io.slychat.messenger.services.di.PlatformModule
import io.slychat.messenger.services.di.UserComponent
import io.slychat.messenger.services.ui.createAppDirectories
import io.slychat.messenger.services.ui.js.NavigationService
import io.slychat.messenger.services.ui.js.getNavigationPageSettings
import io.slychat.messenger.services.ui.js.javatojs.NavigationServiceToJSProxy
import io.slychat.messenger.services.ui.registerCoreServicesOnDispatcher
import javafx.animation.FadeTransition
import javafx.application.Application
import javafx.application.Platform
import javafx.scene.Node
import javafx.scene.Scene
import javafx.scene.control.Alert
import javafx.scene.control.MenuBar
import javafx.scene.control.MenuItem
import javafx.scene.control.SeparatorMenuItem
import javafx.scene.image.Image
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyCodeCombination
import javafx.scene.input.KeyCombination
import javafx.scene.input.KeyEvent
import javafx.scene.layout.Pane
import javafx.scene.layout.StackPane
import javafx.scene.web.WebEngine
import javafx.scene.web.WebView
import javafx.stage.Screen
import javafx.stage.Stage
import javafx.util.Duration
import nl.komponents.kovenant.jfx.JFXDispatcher
import nl.komponents.kovenant.ui.KovenantUi
import org.slf4j.LoggerFactory
import rx.schedulers.JavaFxScheduler
import rx.subjects.BehaviorSubject
import java.util.*
import javax.crypto.Cipher

class DesktopApp : Application() {
    private val log = LoggerFactory.getLogger(javaClass)

    private val app: SlyApplication = SlyApplication()

    private var stage: Stage? = null
    //only used on osx
    private var prefsItem: MenuItem? = null
    private var loadingScreen: Node? = null

    private var dispatcher: Dispatcher? = null
    private val windowService = DesktopUIWindowService(null)
    private var navigationService: NavigationService? = null
    private val uiVisibility = BehaviorSubject.create<Boolean>(false)

    private val keyBindings = ArrayList<KeyBinding>()

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

    /**
     * JavaFX centers the window on screen by default, but it has no concept of a currently focused screen and just
     * spawns it on the primary screen by default.
     *
     * Here we access the current cursor position and use it to locate the focused screen, then center the window on
     * that screen.
     */
    private fun initializeWindowPosition(primaryStage: Stage) {
        val robot = com.sun.glass.ui.Application.GetApplication().createRobot()
        val mouseX = robot.mouseX
        val mouseY = robot.mouseY

        val currentScreen = Screen.getScreensForRectangle(mouseX.toDouble(), mouseY.toDouble(), 1.0, 1.0).first()

        val visualBounds = currentScreen.visualBounds

        primaryStage.x = visualBounds.minX
        primaryStage.y = visualBounds.minY

        primaryStage.centerOnScreen()
    }

    override fun init() {
        //this'll be checked again in start() and it'll display an error
        if (isRestrictedCryptography())
            return

        MenuItem("Testing")

        KovenantUi.uiContext {
            dispatcher = JFXDispatcher.instance
        }

        javaClass.loadSQLiteLibraryFromResources()

        val platformInfo = DesktopPlatformInfo()
        createAppDirectories(platformInfo)

        //for some reason this isn't present on certain linux systems (tested on ubuntu 16.04, arch linux)
        val hostServices = try {
            //if this fails to load, you end up with a HostServices with a null delegate that just throws on every method
            //so we check to see if we've actually loaded the class or not
            val hostServices = this.hostServices

            hostServices.documentBase

            hostServices
        }
        catch (e: NullPointerException) {
            null
        }

        val browser = if (hostServices != null)
            JFXBrowser(hostServices)
        else {
            if (currentOs.type == Os.Type.LINUX)
                LinuxBrowser()
            else {
                log.error("Unable to load HostServices and not on Linux")
                DummyBrowser()
            }
        }

        val desktopNotificationService = DesktopNotificationService(
            JfxAudioPlayback(),
            JfxNotificationDisplay()
        )

        val platformModule = PlatformModule(
            DesktopUIPlatformInfoService(),
            SlyBuildConfig.DESKTOP_SERVER_URLS,
            platformInfo,
            DesktopTelephonyService(),
            windowService,
            DesktopPlatformContacts(),
            desktopNotificationService,
            DesktopUIShareService(),
            DesktopUIPlatformService(browser),
            DesktopUILoadService(this),
            uiVisibility,
            DesktopTokenFetcher(),
            BehaviorSubject.create(true),
            JavaFxScheduler.getInstance(),
            UserConfig(),
            null
        )

        app.init(platformModule)

        desktopNotificationService.init(app.userSessionAvailable)

        app.isInBackground = false
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

        windowService.stage = stage

        uiVisibility.onNext(!primaryStage.isIconified)

        primaryStage.focusedProperty().addListener { o, oldV, newV ->
            uiVisibility.onNext(newV)
        }

        primaryStage.iconifiedProperty().addListener { o, oldV, newV ->
            uiVisibility.onNext(!newV)
        }

        val appComponent = app.appComponent

        val stackPane = StackPane()

        val webView = WebView()
        stackPane.children.add(webView)

        webView.isContextMenuEnabled = false

        val engine = webView.engine

        enableDebugger(engine)

        val webEngineInterface = JFXWebEngineInterface(engine)

        val dispatcher = Dispatcher(webEngineInterface)
        this.dispatcher = dispatcher

        registerCoreServicesOnDispatcher(dispatcher, appComponent)

        val splashImage = Image("/icon_512x512.png")

        val loadingScreen = SplashImage(splashImage)
        stackPane.children.add(loadingScreen)
        this.loadingScreen = loadingScreen

        //TODO refresh prefs
        app.addOnInitListener {
            engine.load(javaClass.getResource("/ui/index.html").toExternalForm())
        }

        app.userSessionAvailable.subscribe { onUserSessionAvailable(it) }

        javaClass.getResourceAsStream("/sly-messenger.png").use { primaryStage.icons.add(Image(it)) }
        primaryStage.title = "Sly Chat"

        primaryStage.scene = Scene(stackPane, 852.0, 480.0)
        initializeWindowPosition(primaryStage)
        primaryStage.show()

        primaryStage.setOnHidden { onWindowClosed() }

        primaryStage.addEventHandler(KeyEvent.KEY_RELEASED) { event ->
            handleKeyEvent(event)
        }

        osxSetup()
    }

    private fun onWindowClosed() {
        stage = null
        dispatcher = null
        navigationService = null
    }

    private fun updatePrefsState() {
        prefsItem?.isDisable = !isPrefsAvailable()
    }

    private fun isPrefsAvailable(): Boolean {
        return navigationService != null && app.userComponent != null
    }

    private fun onUserSessionAvailable(userComponent: UserComponent?) {
        updatePrefsState()
    }

    private fun handleKeyEvent(event: KeyEvent) {
        for (keyBinding in keyBindings) {
            if (keyBinding.matches(event)) {
                keyBinding.action()
                event.consume()
                break
            }
        }
    }

    private fun osxSetup() {
        if (currentOs.type != Os.Type.OSX)
            return

        setupOsxMenu()

        addOsxKeybindings()
    }

    private fun addOsxKeybindings() {
        val minimize = KeyBinding(
            KeyCodeCombination(KeyCode.M, KeyCodeCombination.META_DOWN),
            {
                stage?.isIconified = true
            }
        )

        val fullScreen = KeyBinding(
            KeyCodeCombination(KeyCode.F, KeyCodeCombination.META_DOWN, KeyCodeCombination.CONTROL_DOWN),
            {
                val stage = this.stage

                if (stage != null)
                    stage.isFullScreen = !stage.isFullScreen
            }
        )

        val closeWindow = KeyBinding(
            KeyCodeCombination(KeyCode.W, KeyCodeCombination.META_DOWN),
            {
                stage?.close()
            }
        )

        keyBindings.add(minimize)
        keyBindings.add(fullScreen)
        keyBindings.add(closeWindow)
    }

    private fun setupOsxMenu() {
        val tk = MenuToolkit.toolkit()

        val appMenuBar = MenuBar()

        val appMenu = tk.createDefaultApplicationMenu("Sly Chat")
        appMenuBar.menus.add(appMenu)

        val prefsItem = MenuItem("Preferences")
        prefsItem.accelerator = KeyCodeCombination(KeyCode.COMMA, KeyCombination.META_DOWN)
        prefsItem.isDisable = isPrefsAvailable()
        prefsItem.setOnAction {
            navigationService?.goTo(getNavigationPageSettings())
        }
        appMenu.items.addAll(1, listOf(
            SeparatorMenuItem(),
            prefsItem,
            SeparatorMenuItem()
        ))

        this.prefsItem = prefsItem

        tk.setGlobalMenuBar(appMenuBar)
    }

    override fun stop() {
        super.stop()

        app.shutdown()
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            if (currentOs.type.isPosix) {
                val libc = CLibrary.INSTANCE
                //077, kotlin doesn't support octal literals
                libc.umask(63)
            }

            launch(DesktopApp::class.java, *args)
        }

        private fun isRestrictedCryptography(): Boolean {
            return Cipher.getMaxAllowedKeyLength("AES") != Integer.MAX_VALUE
        }
    }

    fun uiLoadComplete() {
        val node = loadingScreen
        if (node == null) {
            log.warn("Attempted to hide splash screen twice!")
            return
        }

        loadingScreen = null

        val fade = FadeTransition(Duration.millis(600.0), node)

        fade.fromValue = 1.0
        fade.toValue = 0.0

        fade.play()

        fade.setOnFinished {
            (node.parent as Pane).children.remove(node)
        }

        //will never be null here
        navigationService = NavigationServiceToJSProxy(dispatcher!!)
        updatePrefsState()
    }
}
