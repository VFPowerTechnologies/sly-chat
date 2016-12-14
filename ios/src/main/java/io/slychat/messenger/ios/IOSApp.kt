package io.slychat.messenger.ios

import apple.NSObject
import apple.coregraphics.struct.CGPoint
import apple.coregraphics.struct.CGRect
import apple.coregraphics.struct.CGSize
import apple.foundation.*
import apple.foundation.c.Foundation
import apple.uikit.*
import apple.uikit.c.UIKit
import apple.uikit.enums.UIModalPresentationStyle
import apple.uikit.protocol.UIApplicationDelegate
import apple.uikit.protocol.UIPopoverPresentationControllerDelegate
import com.almworks.sqlite4java.SQLite
import io.slychat.messenger.core.SlyBuildConfig
import io.slychat.messenger.ios.kovenant.IOSDispatcher
import io.slychat.messenger.ios.rx.IOSMainScheduler
import io.slychat.messenger.ios.ui.WebViewController
import io.slychat.messenger.services.Sentry
import io.slychat.messenger.services.SlyApplication
import io.slychat.messenger.services.config.UserConfig
import io.slychat.messenger.services.di.ApplicationComponent
import io.slychat.messenger.services.di.PlatformModule
import io.slychat.messenger.services.ui.createAppDirectories
import nl.komponents.kovenant.ui.KovenantUi
import org.moe.natj.general.Pointer
import org.moe.natj.general.ann.RegisterOnStartup
import org.moe.natj.general.ptr.Ptr
import org.moe.natj.general.ptr.impl.PtrFactory
import org.moe.natj.objc.ann.Selector
import org.slf4j.LoggerFactory
import rx.subjects.BehaviorSubject
import java.io.File

@RegisterOnStartup
class IOSApp private constructor(peer: Pointer) : NSObject(peer), UIApplicationDelegate, UIPopoverPresentationControllerDelegate {
    companion object {
        @JvmStatic
        @Selector("alloc")
        external fun alloc(): IOSApp

        @JvmStatic
        fun main(args: Array<String>) {
            UIKit.UIApplicationMain(0, null, null, IOSApp::class.java.name)
        }

        val instance: IOSApp
            get() = UIApplication.sharedApplication().delegate() as IOSApp
    }

    private val log = LoggerFactory.getLogger(javaClass)

    private val app = SlyApplication()

    private var window: UIWindow? = null

    private val uiVisibility = BehaviorSubject.create<Boolean>()

    private lateinit var reachability: Reachability

    private lateinit var webViewController: WebViewController

    private lateinit var screenProtectionWindow: UIWindow

    private fun excludeDirFromBackup(path: File) {
        val url = NSURL.fileURLWithPath(path.toString())

        val errorPtr = PtrFactory.newObjectReference(NSError::class.java)

        @Suppress("UNCHECKED_CAST")
        if (!url.setResourceValueForKeyError(NSNumber.alloc().initWithBool(true), Foundation.NSURLIsExcludedFromBackupKey(), errorPtr)) {
            val error = errorPtr.get()
            throw RuntimeException("Unable to exclude directory from backups: ${error.description()}")
        }
    }

    override fun applicationDidFinishLaunchingWithOptions(application: UIApplication, launchOptions: NSDictionary<*, *>?): Boolean {
        printBundleInfo()

        KovenantUi.uiContext {
            dispatcher = IOSDispatcher.instance
        }

        SQLite.loadLibrary()

        val platformInfo = IOSPlatformInfo()
        createAppDirectories(platformInfo)
        excludeDirFromBackup(platformInfo.appFileStorageDirectory)

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

        Sentry.setIOSDeviceName(UIDevice.currentDevice().model())

        buildUI(app.appComponent)
        initScreenProtection()

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

    private fun printBundleInfo() {
        val bundle = NSBundle.mainBundle()

        val infoDictionary = bundle.infoDictionary()

        val name = infoDictionary["CFBundleDisplayName"]

        val version = infoDictionary["CFBundleShortVersionString"]

        val build = infoDictionary["CFBundleVersion"]

        log.debug("Bundle info: name=$name; version=$version; build=$build")
    }

    //same as Signal's implementation
    private fun initScreenProtection() {
        val screen = UIScreen.mainScreen()
        val window = UIWindow.alloc().initWithFrame(screen.bounds())
        window.isHidden = true
        window.isOpaque = true
        window.isUserInteractionEnabled = false
        window.setWindowLevel(Double.MAX_VALUE)
        window.setBackgroundColor(UIColor.whiteColor())

        screenProtectionWindow = window
    }

    private fun showScreenProtection() {
        screenProtectionWindow.isHidden = false
    }

    private fun hideScreenProtection() {
        screenProtectionWindow.isHidden = true
    }

    override fun applicationWillResignActive(application: UIApplication) {
        log.debug("Application will enter background")

        showScreenProtection()

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

        hideScreenProtection()

        //moved this here so that we have updated network status by this point, as the network status isn't actually
        //updated until we get here, even if we manually call SCNetworkReachabilityGetFlags beforehand
        app.isInBackground = false

        uiVisibility.onNext(true)
    }

    fun uiLoadComplete() {
        webViewController.hideLaunchScreenView()
    }

    private fun calcSharePopoverRect(): CGRect {
        val frame = webViewController.view().frame()
        val x = frame.size().width() / 2

        return CGRect(CGPoint(x, 0.0), CGSize(0.0, 20.0))
    }

    fun inviteToSly(subject: String, text: String, htmlText: String?) {
        //here we provide something that describes itself as NSURL so facebook messenger shows up in the list (else it
        //won't list itself as a choice)

        //then, we return a null value for the url; this causes facebook messenger to use the text provided as the
        //message data; otherwise it'll only use the url

        //this is similar to how the message shows up in the android version

        //should this stop working, change EmptyURLActivityItemProvider to just return an NSURL when activityType() == com.facebook.Messenger.ShareExtension
        val activityItems = nsarray(
            text,
            EmptyURLActivityItemProvider.alloc().initWithPlaceholderItem(NSURL.URLWithString("https://slychat.io/get-app"))
        )

        val activityController = UIActivityViewController.alloc().initWithActivityItemsApplicationActivities(activityItems, null)

        val excludedTypes = nsarray(UIKit.UIActivityTypeAddToReadingList(), UIKit.UIActivityTypeAirDrop())

        activityController.setExcludedActivityTypes(excludedTypes)

        activityController.setModalPresentationStyle(UIModalPresentationStyle.Popover)

        webViewController.presentViewControllerAnimatedCompletion(activityController, true, null)

        val popoverController = activityController.popoverPresentationController()
        if (popoverController != null) {
            popoverController.setSourceView(webViewController.view())
            popoverController.setSourceRect(calcSharePopoverRect())
            //currently disabled, as this leads to a crash in MOE
            //popoverController.setDelegate(this)
        }
    }

    override fun popoverPresentationControllerWillRepositionPopoverToRectInView(popoverPresentationController: UIPopoverPresentationController, rect: CGRect, view: Ptr<UIView>) {
        val newRect = calcSharePopoverRect()
        rect.setOrigin(newRect.origin())
        rect.setSize(newRect.size())
    }
}
