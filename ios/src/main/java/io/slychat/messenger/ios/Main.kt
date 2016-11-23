package io.slychat.messenger.ios

import apple.NSObject
import apple.foundation.NSDictionary
import apple.uikit.*
import apple.uikit.c.UIKit
import apple.uikit.protocol.UIApplicationDelegate
import com.almworks.sqlite4java.SQLite
import io.slychat.messenger.core.SlyBuildConfig
import io.slychat.messenger.ios.ui.WebViewController
import io.slychat.messenger.services.SlyApplication
import io.slychat.messenger.services.config.UserConfig
import io.slychat.messenger.services.di.PlatformModule
import io.slychat.messenger.services.ui.createAppDirectories
import nl.komponents.kovenant.ui.KovenantUi
import org.moe.natj.general.Pointer
import org.moe.natj.general.ann.RegisterOnStartup
import org.moe.natj.objc.ann.Selector
import rx.subjects.BehaviorSubject

@RegisterOnStartup
class Main private constructor(peer: Pointer) : NSObject(peer), UIApplicationDelegate {
    companion object {
        @JvmStatic fun main(args: Array<String>) {
            UIKit.UIApplicationMain(0, null, null, Main::class.java.name)
        }

        @Selector("alloc")
        external fun alloc(): Main
    }

    private val app = SlyApplication()

    private var window: UIWindow? = null
    private val uiVisibility = BehaviorSubject.create<Boolean>()

    override fun applicationDidFinishLaunchingWithOptions(application: UIApplication, launchOptions: NSDictionary<*, *>?): Boolean {
        KovenantUi.uiContext {
            dispatcher = IOSDispatcher.instance
        }

        SQLite.loadLibrary()

        val platformInfo = IOSPlatformInfo()
        createAppDirectories(platformInfo)

        val notificationService = IOSNotificationService()

        val networkStatus = BehaviorSubject.create(true)

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

        buildUI()

        return true
    }

    private fun buildUI() {
        val screen = UIScreen.mainScreen()
        val window = UIWindow.alloc().initWithFrame(screen.bounds())

        val vc = WebViewController.alloc().init()
        val navigationController = UINavigationController.alloc().initWithRootViewController(vc)

        window.setRootViewController(navigationController)

        window.setBackgroundColor(UIColor.blackColor())

        window.makeKeyAndVisible()
    }

    override fun setWindow(value: UIWindow?) {
        window = value
    }

    override fun window(): UIWindow? {
        return window
    }

    override fun applicationWillResignActive(application: UIApplication) {
        uiVisibility.onNext(false)
    }

    override fun applicationDidEnterBackground(application: UIApplication?) {
        app.isInBackground = true
    }

    override fun applicationWillEnterForeground(application: UIApplication?) {
        app.isInBackground = false
    }

    override fun applicationDidBecomeActive(application: UIApplication) {
        uiVisibility.onNext(true)
    }
}
