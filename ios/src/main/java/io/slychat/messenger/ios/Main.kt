package io.slychat.messenger.ios

import apple.NSObject
import apple.foundation.NSDictionary
import apple.uikit.UIApplication
import apple.uikit.UIColor
import apple.uikit.UIScreen
import apple.uikit.UIWindow
import apple.uikit.c.UIKit
import apple.uikit.protocol.UIApplicationDelegate
import com.almworks.sqlite4java.SQLite
import io.slychat.messenger.core.SlyBuildConfig
import io.slychat.messenger.ios.kovenant.IOSDispatcher
import io.slychat.messenger.ios.rx.IOSMainScheduler
import io.slychat.messenger.ios.ui.WebViewController
import io.slychat.messenger.services.SlyApplication
import io.slychat.messenger.services.config.UserConfig
import io.slychat.messenger.services.di.ApplicationComponent
import io.slychat.messenger.services.di.PlatformModule
import io.slychat.messenger.services.ui.createAppDirectories
import nl.komponents.kovenant.ui.KovenantUi
import org.moe.natj.general.Pointer
import org.moe.natj.general.ann.RegisterOnStartup
import org.moe.natj.objc.ann.Selector
import org.slf4j.LoggerFactory
import rx.subjects.BehaviorSubject

@RegisterOnStartup
class Main private constructor(peer: Pointer) : NSObject(peer), UIApplicationDelegate {
    companion object {
        @Selector("alloc")
        external fun alloc(): Main

        @JvmStatic fun main(args: Array<String>) {
            UIKit.UIApplicationMain(0, null, null, Main::class.java.name)
        }

        val instance: Main
            get() = UIApplication.sharedApplication().delegate() as Main
    }

    private val log = LoggerFactory.getLogger(javaClass)

    private val app = SlyApplication()

    private var window: UIWindow? = null

    private val uiVisibility = BehaviorSubject.create<Boolean>()

    private lateinit var reachability: Reachability

    private lateinit var webViewController: WebViewController

    override fun applicationDidFinishLaunchingWithOptions(application: UIApplication, launchOptions: NSDictionary<*, *>?): Boolean {
        KovenantUi.uiContext {
            dispatcher = IOSDispatcher.instance
        }

        SQLite.loadLibrary()

        val platformInfo = IOSPlatformInfo()
        createAppDirectories(platformInfo)

        val notificationService = IOSNotificationService()

        reachability = Reachability()

        val networkStatus = reachability.connectionStatus.map {
            when (it) {
                ConnectionStatus.WIFI -> true
                ConnectionStatus.WWAN -> true
                ConnectionStatus.NONE -> false
            }
        }

        val platformModule = PlatformModule(
            IOSUIPlatformInfoService(),
            SlyBuildConfig.DESKTOP_SERVER_URLS,
            platformInfo,
            IOSTelephonyService(),
            IOSUIWindowService(),
            IOSPlatformContacts(),
            notificationService,
            IOSUIShareService(),
            IOSUIPlatformService(),
            IOSUILoadService(),
            uiVisibility,
            networkStatus,
            IOSMainScheduler.instance,
            UserConfig()
        )

        app.init(platformModule)

        buildUI(app.appComponent)

        app.isInBackground = false

        return true
    }

    private fun buildUI(appComponent: ApplicationComponent) {
        val screen = UIScreen.mainScreen()
        val window = UIWindow.alloc().initWithFrame(screen.bounds())

        val vc = WebViewController.alloc().initWithAppComponent(appComponent)

        webViewController = vc

        window.setRootViewController(vc)

        window.setBackgroundColor(UIColor.blackColor())

        window.makeKeyAndVisible()

        setWindow(window)
    }

    override fun setWindow(value: UIWindow?) {
        window = value
    }

    override fun window(): UIWindow? {
        return window
    }

    override fun applicationWillResignActive(application: UIApplication) {
        log.debug("Application will enter background")
        uiVisibility.onNext(false)
    }

    override fun applicationDidEnterBackground(application: UIApplication?) {
        log.debug("Application entered background")
        app.isInBackground = true
    }

    override fun applicationWillEnterForeground(application: UIApplication?) {
        log.debug("Application will enter foreground")
    }

    override fun applicationDidBecomeActive(application: UIApplication) {
        log.debug("Application has become active")
        //moved this here so that we have updated network status by this point, as the network status isn't actually
        //updated until we get here, even if we manually call SCNetworkReachabilityGetFlags beforehand
        app.isInBackground = false
        uiVisibility.onNext(true)
    }

    fun uiLoadComplete() {
        webViewController.hideLaunchScreenView()
    }
}
