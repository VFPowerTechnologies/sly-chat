package io.slychat.messenger.desktop

import ca.weblite.objc.RuntimeUtils.sel
import com.fasterxml.jackson.databind.ObjectMapper
import com.vfpowertech.jsbridge.core.dispatcher.Dispatcher
import com.vfpowertech.jsbridge.desktopwebengine.JFXWebEngineInterface
import de.codecentric.centerdevice.MenuToolkit
import de.codecentric.centerdevice.dialogs.about.AboutStageBuilder
import io.slychat.messenger.core.Os
import io.slychat.messenger.core.SlyAddress
import io.slychat.messenger.core.SlyBuildConfig
import io.slychat.messenger.core.currentOs
import io.slychat.messenger.core.persistence.ConversationId
import io.slychat.messenger.core.persistence.sqlite.loadSQLiteLibraryFromResources
import io.slychat.messenger.desktop.jfx.jsconsole.ConsoleMessageAdded
import io.slychat.messenger.desktop.osx.AppleEventHandler
import io.slychat.messenger.desktop.osx.GlassEventHandler
import io.slychat.messenger.desktop.osx.OSXNotificationService
import io.slychat.messenger.desktop.osx.UserNotificationCenterDelegate
import io.slychat.messenger.desktop.osx.ns.NSAppleEventManager
import io.slychat.messenger.desktop.osx.ns.NSAppleEventManager.Companion.kAEReopenApplication
import io.slychat.messenger.desktop.osx.ns.NSAppleEventManager.Companion.kCoreEventClass
import io.slychat.messenger.desktop.osx.ns.NSUserNotificationCenter
import io.slychat.messenger.desktop.services.*
import io.slychat.messenger.desktop.ui.SplashImage
import io.slychat.messenger.services.PlatformNotificationService
import io.slychat.messenger.services.SlyApplication
import io.slychat.messenger.services.config.AppConfig
import io.slychat.messenger.services.config.UserConfig
import io.slychat.messenger.services.di.PlatformModule
import io.slychat.messenger.services.di.UserComponent
import io.slychat.messenger.services.ui.createAppDirectories
import io.slychat.messenger.services.ui.js.NavigationService
import io.slychat.messenger.services.ui.js.getNavigationPageConversation
import io.slychat.messenger.services.ui.js.getNavigationPageSettings
import io.slychat.messenger.services.ui.js.javatojs.NavigationServiceToJSProxy
import io.slychat.messenger.services.ui.registerCoreServicesOnDispatcher
import javafx.animation.FadeTransition
import javafx.application.Application
import javafx.application.Platform
import javafx.scene.Node
import javafx.scene.Scene
import javafx.scene.control.Alert
import javafx.scene.control.MenuItem
import javafx.scene.control.SeparatorMenuItem
import javafx.scene.image.Image
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyCodeCombination
import javafx.scene.input.KeyCombination
import javafx.scene.input.KeyEvent
import javafx.scene.layout.Pane
import javafx.scene.layout.StackPane
import javafx.scene.paint.Color
import javafx.scene.web.WebEngine
import javafx.scene.web.WebView
import javafx.stage.Screen
import javafx.stage.Stage
import javafx.stage.StageStyle
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

    private val uiAvailableListeners = ArrayList<() -> Unit>()

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

    private fun getPlatformNotificationService(): PlatformNotificationService {
        return if (currentOs.type == Os.Type.OSX)
            OSXNotificationService(uiVisibility)
        else
            DesktopNotificationService(
                JfxAudioPlayback(),
                JfxNotificationDisplay()
            )
    }

    /** Perform AppComponent and other non-UI-related initialization. */
    override fun init() {
        //this'll be checked again in start() and it'll display an error
        if (isRestrictedCryptography())
            return

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

        val desktopNotificationService = getPlatformNotificationService()

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
            null,
            DesktopFileAccess()
        )

        app.init(platformModule)

        when (desktopNotificationService) {
            is DesktopNotificationService ->
                desktopNotificationService.init(app.userSessionAvailable)

            is OSXNotificationService ->
                desktopNotificationService.init(app.userSessionAvailable)
        }

        app.isInBackground = false
    }

    /** Creates the main ui window. */
    private fun initPrimaryStage(primaryStage: Stage, isInitialLoad: Boolean) {
        this.stage = primaryStage

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

        if (isInitialLoad) {
            val appearanceTheme = appComponent.appConfigService.appearanceTheme ?: AppConfig.APPEARANCE_THEME_VALUE_DEFAULT
            val backgroundColor = when (appearanceTheme) {
                AppConfig.APPEARANCE_THEME_VALUE_DARK -> Color.BLACK
                AppConfig.APPEARANCE_THEME_VALUE_WHITE -> Color.WHITE
                else -> Color.BLACK
            }

            val loadingScreen = SplashImage(splashImage, backgroundColor)
            stackPane.children.add(loadingScreen)
            this.loadingScreen = loadingScreen
        }

        app.addOnInitListener {
            engine.load(javaClass.getResource("/ui/index.html").toExternalForm())
        }

        app.userSessionAvailable.subscribe { onUserSessionAvailable(it) }

        javaClass.getResourceAsStream("/window-icon.png").use { primaryStage.icons.add(Image(it)) }
        primaryStage.title = "Sly"

        primaryStage.scene = Scene(stackPane, 852.0, 480.0)

        //we wanna show something to the user asap
        if (isInitialLoad)
            primaryStage.show()

        /*
            Calling show() before initializing the position is required due to resolution scaling on OSX
            the stage is rendered according to the main monitor
            if we change the position to a different monitor, then call show() the stage
            is still rendered in the main monitor's scale, not the associated monitor
            calling show() beforehand avoids this issue
        */
        initializeWindowPosition(primaryStage)

        primaryStage.setOnHidden { onWindowClosed() }

        primaryStage.addEventHandler(KeyEvent.KEY_RELEASED) { event ->
            handleKeyEvent(event)
        }
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

        //only do this once on startup
        checkForStartupNotification()

        //delay showing ui until app config is read so we have access to the current theme info

        //ideally we'd use this info to provide proper fill colors, but right now this doesn't prevent flickering
        //on startup, due to https://bugs.openjdk.java.net/browse/JDK-8088179
        //so if this ever gets backported to jre8, I'll come back and fix the stuff here
        app.addOnInitListener {
            initPrimaryStage(primaryStage, true)

            osxSetup()
        }
    }

    /** Check if the app was launched from a notification. */
    private fun checkForStartupNotification() {
        if (currentOs.type != Os.Type.OSX)
            return

        val notificationHandler = Main.startupNotificationObserver ?: return

        val conversationId = notificationHandler.startupConversationId ?: return
        notificationHandler.clear()

        val initialPage = getNavigationPageConversation(conversationId)
        app.appComponent.uiStateService.initialPage = initialPage
    }

    private fun onWindowClosed() {
        stage = null
        dispatcher = null
        navigationService = null
        loadingScreen = null
    }

    private fun updatePrefsState() {
        prefsItem?.isDisable = !isPrefsAvailable()
    }

    private fun isPrefsAvailable(): Boolean {
        return app.userComponent != null
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

    /**
     * Enable some OSX-specific functionality:
     *
     * Notification center support. (this is handled via a custom PlatformNotificationService)
     *
     * App won't shutdown when window is closed, but will continue to function in the background. UI will be restored
     * if the dock tile is clicked, or a notification is activated.
     *
     * A proper system menu part is set up.
     *
     * Stages will response to common OSX keybindings (such as cmd-w).
     */
    private fun osxSetup() {
        if (currentOs.type != Os.Type.OSX)
            return

        Platform.setImplicitExit(false)

        setupOsxMenu()

        registerOsxKeybindings()

        hookAppleEvents()

        val glassApplication = com.sun.glass.ui.Application.GetApplication()
        glassApplication.eventHandler = GlassEventHandler(glassApplication.eventHandler)

        val userNotificationCenter = NSUserNotificationCenter.defaultUserNotificationCenter
        userNotificationCenter.delegate = UserNotificationCenterDelegate(this, uiVisibility)
    }

    /**
     * Registers common window-level keybindings used on OSX.
     *
     * Currently registers the following bindings:
     *
     * cmd-m: minimize
     * cmd-ctrl-f: fullscreen
     * cmd-w: close window
     * cmd-,: preferences (if logged in)
     *
     * cmd-h, cmd-alt-h are handled via NSDeskFX's app menu accelerators.
     */
    private fun registerOsxKeybindings() {
        //note that we do all stage modifications in the next event loop iteration
        //if we don't do this, we get various issues
        //for closing a window, an NSArray objectAtIndex: is thrown (although no crash occurs)
        //also for closing, any other window will end up processing the event even if consume() is called; so cmd-w can
        //do something like closing the focused About window AND the main window with a single press/release
        //for fullscreening, something similar happens if you unfullscreen right away, and the titlebar icon gets corrupted
        //if you switch space after fullscreening, then go back and unfullscreen the jvm crashes from an uncaught CALayer exception
        //I haven't seen any adverse effects for minimization, but doing the same thing there anyways incase
        val minimize = KeyBinding(
            KeyCodeCombination(KeyCode.M, KeyCodeCombination.META_DOWN),
            {
                val stage = this.stage

                if (stage != null) {
                    Platform.runLater {
                        stage.isIconified = true
                    }
                }
            }
        )
        keyBindings.add(minimize)

        val fullScreen = KeyBinding(
            KeyCodeCombination(KeyCode.F, KeyCodeCombination.META_DOWN, KeyCodeCombination.CONTROL_DOWN),
            {
                val stage = this.stage

                if (stage != null) {
                    Platform.runLater {
                        stage.isFullScreen = !stage.isFullScreen
                    }
                }
            }
        )
        keyBindings.add(fullScreen)

        val closeWindow = KeyBinding(
            KeyCodeCombination(KeyCode.W, KeyCodeCombination.META_DOWN),
            {
                val stage = this.stage
                if (stage != null) {
                    Platform.runLater {
                        stage.close()
                    }
                }
            }
        )
        keyBindings.add(closeWindow)
    }

    /** Uses NSDeskFX to modify the app menu. */
    private fun setupOsxMenu() {
        val tk = MenuToolkit.toolkit()

        val aboutStage = AboutStageBuilder.start("Sly")
            .withAppName("Sly")
            .withImage(Image("/about-icon.png"))
            .withCopyright("Copyright 2016-2017 Keystream Systems Inc.")
            .withCloseOnFocusLoss()
            .build()

        //disable minimize button in titlebar
        aboutStage.initStyle(StageStyle.UTILITY)

        aboutStage.addEventHandler(KeyEvent.KEY_RELEASED) { event ->
            if (KeyCodeCombination(KeyCode.W, KeyCodeCombination.META_DOWN).match(event)) {
                Platform.runLater {
                    aboutStage.close()
                }

                event.consume()
            }
        }

        val appMenu = tk.createDefaultApplicationMenu("Sly", aboutStage)

        val prefsItem = MenuItem("Preferences")
        prefsItem.accelerator = KeyCodeCombination(KeyCode.COMMA, KeyCombination.META_DOWN)
        prefsItem.isDisable = isPrefsAvailable()
        prefsItem.setOnAction {
            addUIAvailableListener {
                navigationService?.goTo(getNavigationPageSettings())
            }
        }
        appMenu.items.addAll(1, listOf(
            SeparatorMenuItem(),
            prefsItem,
            SeparatorMenuItem()
        ))

        this.prefsItem = prefsItem

        tk.setApplicationMenu(appMenu)
    }

    override fun stop() {
        super.stop()

        app.shutdown()
    }

    companion object {
        private fun isRestrictedCryptography(): Boolean {
            return Cipher.getMaxAllowedKeyLength("AES") != Integer.MAX_VALUE
        }
    }

    fun uiLoadComplete() {
        val node = loadingScreen

        if (node != null) {
            loadingScreen = null

            val fade = FadeTransition(Duration.millis(600.0), node)

            fade.fromValue = 1.0
            fade.toValue = 0.0

            fade.play()

            fade.setOnFinished {
                (node.parent as Pane).children.remove(node)
            }
        }
        else
            stage?.show()

        if (navigationService == null) {
            //will never be null here
            navigationService = NavigationServiceToJSProxy(dispatcher!!)

            runUIAvailableListeners()
        }
        else
            log.warn("Attempt to hide splash screen twice")
    }

    private fun runUIAvailableListeners() {
        uiAvailableListeners.forEach { it() }
        uiAvailableListeners.clear()
    }

    /**
     * Runs the given listener when the webview ui has completed loading (via uiLoadComplete).
     * Will unminimize window if required.
     */
    private fun addUIAvailableListener(listener: () -> Unit) {
        if (navigationService != null) {
            listener()

            val stage = this.stage!!

            stage.toFront()
        }
        else {
            uiAvailableListeners.add(listener)
            restoreUI()
        }
    }

    fun handleSendConversationSendReply(account: SlyAddress, conversationId: ConversationId, message: String) {
        val userComponent = app.userComponent
        if (userComponent == null) {
            log.info("handleConversationSendReply called but not logged in, ignoring")
            return
        }

        if (userComponent.userLoginData.address != account) {
            log.info("Attempt to reply to a notification for a different account, ignoring")
            return
        }

        val messengerService = userComponent.messengerService

        when (conversationId) {
            is ConversationId.User -> messengerService.sendMessageTo(conversationId.id, message, 0)
            is ConversationId.Group -> messengerService.sendGroupMessageTo(conversationId.id, message, 0)
        } fail {
            log.error("Failed to send notification reply: {}", it.message, it)
        }
    }

    fun handleConversationNotificationActivated(account: SlyAddress, conversationId: ConversationId) {
        val userComponent = app.userComponent
        if (userComponent == null) {
            log.info("handleConversationNotificationActivated called but not logged in, ignoring")
            return
        }

        if (userComponent.userLoginData.address != account) {
            log.info("Attempt to load a notification for a different account, ignoring")
            return
        }

        addUIAvailableListener {
            navigationService?.goTo(getNavigationPageConversation(conversationId))
        }
    }

    //must be done after NSApplication has been created, so need to call via start(), and not in main() or init()
    //GlassApplication doesn't support the reopen event at all, so we can just hook it
    private fun hookAppleEvents() {
        val appleEventHandler = AppleEventHandler(this)

        val appleEventManager = NSAppleEventManager.sharedAppleEventManager

        appleEventManager.setEventHandler(appleEventHandler, sel("handleAppleEvent:withReplyEvent:"), kCoreEventClass, kAEReopenApplication)
    }

    private fun restoreUI() {
        if (stage != null)
            return

        val stage = Stage()

        //the window comes up much quicker here, so we don't display a loading page (looks odd anyways)
        initPrimaryStage(stage, false)
    }

    //only used on osx
    //implements default system behavior when docktile is clicked
    fun onReopenEvent() {
        val stage = this.stage

        if (stage != null) {
            //os handles refocusing for us
            stage.toFront()
        }
        else
            restoreUI()
    }
}
