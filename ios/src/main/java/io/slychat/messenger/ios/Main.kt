package io.slychat.messenger.ios

import apple.NSObject
import apple.foundation.NSDictionary
import apple.uikit.*
import apple.uikit.c.UIKit
import apple.uikit.protocol.UIApplicationDelegate
import io.slychat.messenger.ios.ui.WebViewController
import nl.komponents.kovenant.ui.KovenantUi
import org.moe.natj.general.Pointer
import org.moe.natj.general.ann.RegisterOnStartup
import org.moe.natj.objc.ann.Selector

@RegisterOnStartup
class Main private constructor(peer: Pointer) : NSObject(peer), UIApplicationDelegate {
    private var window: UIWindow? = null

    override fun applicationDidFinishLaunchingWithOptions(application: UIApplication, launchOptions: NSDictionary<*, *>?): Boolean {
        KovenantUi.uiContext {
            dispatcher = IOSDispatcher.instance
        }

        val screen = UIScreen.mainScreen()
        val window = UIWindow.alloc().initWithFrame(screen.bounds())

        val vc = WebViewController.alloc().init()
        val navigationController = UINavigationController.alloc().initWithRootViewController(vc)

        window.setRootViewController(navigationController)

        window.setBackgroundColor(UIColor.blackColor())

        window.makeKeyAndVisible()

        return true
    }

    override fun setWindow(value: UIWindow?) {
        window = value
    }

    override fun window(): UIWindow? {
        return window
    }

    companion object {
        @JvmStatic fun main(args: Array<String>) {
            UIKit.UIApplicationMain(0, null, null, Main::class.java.name)
        }

        @Selector("alloc")
        external fun alloc(): Main
    }
}
