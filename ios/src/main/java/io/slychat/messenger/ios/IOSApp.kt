package io.slychat.messenger.ios

import apple.NSObject
import apple.c.Globals
import apple.corefoundation.c.CoreFoundation
import apple.corefoundation.opaque.CFStringRef
import apple.coregraphics.struct.CGRect
import apple.foundation.*
import apple.foundation.c.Foundation
import apple.uikit.*
import apple.uikit.c.UIKit
import apple.uikit.enums.*
import apple.uikit.protocol.UIApplicationDelegate
import apple.uikit.protocol.UIPopoverPresentationControllerDelegate
import com.almworks.sqlite4java.SQLite
import io.slychat.messenger.core.SlyAddress
import io.slychat.messenger.core.SlyBuildConfig
import io.slychat.messenger.core.div
import io.slychat.messenger.core.http.api.pushnotifications.PushNotificationService
import io.slychat.messenger.core.persistence.ConversationId
import io.slychat.messenger.core.pushnotifications.OfflineMessageInfo
import io.slychat.messenger.core.pushnotifications.OfflineMessagesPushNotification
import io.slychat.messenger.ios.kovenant.IOSDispatcher
import io.slychat.messenger.ios.rx.IOSMainScheduler
import io.slychat.messenger.ios.ui.WebViewController
import io.slychat.messenger.logger.Markers
import io.slychat.messenger.services.DeviceTokens
import io.slychat.messenger.services.LoginState
import io.slychat.messenger.services.Sentry
import io.slychat.messenger.services.SlyApplication
import io.slychat.messenger.services.config.UserConfig
import io.slychat.messenger.services.di.ApplicationComponent
import io.slychat.messenger.services.di.PlatformModule
import io.slychat.messenger.services.ui.createAppDirectories
import io.slychat.messenger.services.ui.js.getNavigationPageConversation
import nl.komponents.kovenant.Deferred
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.deferred
import nl.komponents.kovenant.ui.KovenantUi
import org.moe.natj.general.Pointer
import org.moe.natj.general.ann.ReferenceInfo
import org.moe.natj.general.ann.RegisterOnStartup
import org.moe.natj.general.ptr.Ptr
import org.moe.natj.general.ptr.impl.PtrFactory
import org.moe.natj.objc.ObjCRuntime
import org.moe.natj.objc.ann.Selector
import org.slf4j.LoggerFactory
import rx.Observable
import rx.Subscription
import rx.subjects.BehaviorSubject
import java.io.File
import java.util.concurrent.TimeUnit

@RegisterOnStartup
class IOSApp private constructor(peer: Pointer) : NSObject(peer), UIApplicationDelegate, UIPopoverPresentationControllerDelegate {
    companion object {
        @JvmStatic
        @Selector("alloc")
        external fun alloc(): IOSApp

        const val NOTIFICATION_CATEGORY_OFFLINE = "offline"
        const val NOTIFICATION_CATEGORY_CHAT_MESSAGE = "chat-message"

        const val ACTION_ID_UNREGISTER = "unregister"

        const val USERINFO_ADDRESS_KEY = "address"
        const val USERINFO_CONVERSATION_ID_KEY = "conversationId"

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

    private lateinit var notificationService: IOSNotificationService

    private var notificationTokenDeferred: Deferred<DeviceTokens?, Exception>? = null

    private lateinit var crashReportPath: File

    private fun excludeDirFromBackup(path: File) {
        val url = NSURL.fileURLWithPath(path.toString())

        val errorPtr = PtrFactory.newObjectReference(NSError::class.java)

        @Suppress("UNCHECKED_CAST")
        if (!url.setResourceValueForKeyError(NSNumber.alloc().initWithBool(true), Foundation.NSURLIsExcludedFromBackupKey(), errorPtr)) {
            val error = errorPtr.get()
            throw RuntimeException("Unable to exclude directory from backups: ${error.description()}")
        }
    }

    private fun getOfflineCategory(): UIUserNotificationCategory {
        val category = UIMutableUserNotificationCategory.alloc().init()

        category.setIdentifier(NOTIFICATION_CATEGORY_OFFLINE)

        val unregisterAction = UIMutableUserNotificationAction.alloc().init()
        unregisterAction.setActivationMode(UIUserNotificationActivationMode.Background)
        unregisterAction.setIdentifier(ACTION_ID_UNREGISTER)
        unregisterAction.setTitle("Stop receiving notifications")
        unregisterAction.isDestructive = true
        unregisterAction.isAuthenticationRequired = true

        val actions = nsarray(
            unregisterAction
        );

        category.setActionsForContext(actions, UIUserNotificationActionContext.Default)

        return category
    }

    private fun getNotificationCategories(): NSSet<UIUserNotificationCategory>? {
        @Suppress("UNCHECKED_CAST")
        return NSSet.setWithObjects(
            getOfflineCategory(),
            null
        ) as NSSet<UIUserNotificationCategory>
    }

    /**
     * This promise will be fulfilled in one of the following places:
     *
     * applicationDidRegisterUserNotificationSettings with a null token if the user has notifications disabled
     * applicationDidRegisterForRemoteNotificationsWithDeviceToken with an APN token if notifications are enabled
     * applicationDidFailToRegisterForRemoteNotificationsWithError with an error if registration failed
     */
    fun registerForNotifications(): Promise<DeviceTokens?, Exception> {
        var d = notificationTokenDeferred

        if (d == null) {
            d = deferred<DeviceTokens?, Exception>()
            notificationTokenDeferred = d

            val application = UIApplication.sharedApplication()
            val types = UIUserNotificationType.Badge or UIUserNotificationType.Sound or UIUserNotificationType.Alert
            val settings = UIUserNotificationSettings.settingsForTypesCategories(types, getNotificationCategories())
            application.registerUserNotificationSettings(settings)
        }

        return d.promise
    }

    private fun handleOfflineCategoryAction(identifier: String, notification: UILocalNotification) {
        if (identifier != ACTION_ID_UNREGISTER) {
            log.error("Unsupported offline action id: {}", identifier)
            return
        }

        val userInfo = notification.userInfo()

        val addressString = userInfo[USERINFO_ADDRESS_KEY] as String?
        if (addressString == null) {
            log.error("Missing address from offline userInfo")
            return
        }

        val address = SlyAddress.fromString(addressString)
        if (address == null) {
            log.error("Unable to deserialize address: {}", addressString)
            return
        }

        log.debug("Unregistering from push notifications for {}", address)

        val application = UIApplication.sharedApplication()

        var taskId: Long = 0

        //TODO fix this
        taskId = application.beginBackgroundTaskWithExpirationHandler {
            log.info("Background time for token unregister expired")

            application.endBackgroundTask(taskId)
        }

        app.addOnInitListener {
            app.appComponent.pushNotificationsManager.unregister(address)
        }
    }

    override fun applicationHandleActionWithIdentifierForLocalNotificationCompletionHandler(application: UIApplication, identifier: String, notification: UILocalNotification, completionHandler: UIApplicationDelegate.Block_applicationHandleActionWithIdentifierForLocalNotificationCompletionHandler) {
        if (notification.category() == NOTIFICATION_CATEGORY_OFFLINE)
            handleOfflineCategoryAction(identifier, notification)
        else
            log.error("Unsupported notification category: {}; identifier={}", notification.category(), identifier)

        completionHandler.call_applicationHandleActionWithIdentifierForLocalNotificationCompletionHandler()
    }

    private fun setupSignalHandlers() {
        //app can crash due to a SIGPIPE; open app, bg, lock screen, unlock, click desktop icon will typically replicate the issue
        //https://developer.apple.com/library/content/documentation/NetworkingInternetWeb/Conceptual/NetworkingOverview/CommonPitfalls/CommonPitfalls.html
        //this does note that we should ignore SIGPIPE when using posix sockets
        //I guess MOE doesn't do this? I've never seen this be an issue on java before

        //Globals.sigignore exists but it's deprecated so let's not touch it
        //SIG_IGN isn't provided by MOE, so just do this instead (although it's just NULL, so maybe passing a null fn would work)
        val errno = CUtilFunctions.ignoreSIGPIPE()
        if (errno != 0)
            log.error("Unable to ignore SIGPIPE: {}", Globals.strerror(errno))
        else
            log.debug("Ignoring SIGPIPE")
    }

    private fun installCrashReporter() {
        val errno = CUtilFunctions.hookSignalCrashHandler(crashReportPath.toString())
        if (errno != 0)
            log.error("Failed to install crash reporter: {}", Globals.strerror(errno))
        else
            log.debug("Crash reporter installed")
    }

    private fun checkForCrashReport() {
        if (crashReportPath.exists()) {
            log.debug("Crash report found")

            val report = crashReportPath.readText()

            log.error(Markers.FATAL, report)

            crashReportPath.delete()
        }
        else
            log.debug("No crash report found")
    }

    override fun applicationDidFinishLaunchingWithOptions(application: UIApplication, launchOptions: NSDictionary<*, *>?): Boolean {
        val platformInfo = IOSPlatformInfo()

        createAppDirectories(platformInfo)

        crashReportPath = platformInfo.appFileStorageDirectory / "crash-report"

        installCrashReporter()

        printBundleInfo()

        KovenantUi.uiContext {
            dispatcher = IOSDispatcher.instance
        }

        SQLite.loadLibrary()

        excludeDirFromBackup(platformInfo.appFileStorageDirectory)

        notificationService = IOSNotificationService()

        reachability = Reachability()

        val networkStatus = reachability.connectionStatus.map {
            when (it) {
                ConnectionStatus.WIFI -> true
                ConnectionStatus.WWAN -> true
                ConnectionStatus.NONE -> false
            }
        }

        val windowService = IOSUIWindowService(null)

        val platformModule = PlatformModule(
            IOSUIPlatformInfoService(),
            SlyBuildConfig.DESKTOP_SERVER_URLS,
            platformInfo,
            IOSTelephonyService(),
            windowService,
            IOSPlatformContacts(),
            notificationService,
            IOSUIShareService(),
            IOSUIPlatformService(),
            IOSUILoadService(),
            uiVisibility,
            IOSTokenFetcher(this),
            networkStatus,
            IOSMainScheduler.instance,
            UserConfig(),
            PushNotificationService.APN,
            IOSFileAccess()
        )

        Sentry.setIOSDeviceName(UIDevice.currentDevice().model())
        Sentry.setBuildNumber(NSBundle.mainBundle().infoDictionary()["CFBundleVersion"] as String)

        initializeUnhandledExceptionHandlers()

        app.init(platformModule)

        //always call this after app.init, so Sentry is active
        checkForCrashReport()

        setupSignalHandlers()

        val appComponent = app.appComponent

        buildUI(appComponent)
        windowService.webViewController = webViewController

        initScreenProtection()

        app.isInBackground = false

        //we don't want the notifications perm prompt showing up the first time a user boots the app
        //in that case just defer it to login
        app.addOnInitListener {
            appComponent.tokenFetchService.refresh()
        }

        app.userSessionAvailable.filter { it != null }.subscribe {
            if (!appComponent.appConfigService.pushNotificationsPermRequested) {
                appComponent.appConfigService.withEditor {
                    pushNotificationsPermRequested = true
                }

                appComponent.tokenFetchService.refresh()
            }
        }

        return true
    }

    private fun initializeUnhandledExceptionHandlers() {
        log.debug("Initializing uncaught exception handlers")

        //default thread handler is already set by SlyApplication

        //WARNING
        //do NOT call System.exit with a negative value
        //this will cause the app not to exit if there are any remaining daemon threads

        //special handler for main thread crash
        Thread.currentThread().setUncaughtExceptionHandler { thread, throwable ->
            log.error(Markers.FATAL, "Uncaught exception on main thread: {}", throwable.message, throwable)

            try {
                showCrashDialog()
            }
            catch (t: Throwable) {
                log.error("Error attempting to display crash dialog: {}", t.message, t)
            }

            Sentry.waitForShutdown()

            System.exit(1)
        }

        //this can be called if our app throws an uncaught exception when called from native code
        //here, we can't display the crash dialog properly; the dialog shows up and the loop runs, but user input isn't
        //processed for some reason
        //I've tested this via objc and it's the same so it's not so issue caused by MOE or anything
        Foundation.NSSetUncaughtExceptionHandler { nsException ->
            //TODO would be nice to reparse this into a proper exception for logging
            //reason here'll be the (java) stacktrace as a string
            log.error(Markers.FATAL, "Uncaught NSException: {}", nsException.reason())

            Sentry.waitForShutdown()

            System.exit(1)
        }
    }

    private fun showCrashDialog() {
        val rootViewController = UIApplication.sharedApplication().keyWindow()?.rootViewController()
        if (rootViewController == null) {
            log.error("No rootViewController, unable to display crash dialog")
            return
        }

        val alert = UIAlertController.alertControllerWithTitleMessagePreferredStyle(
            "Unexpected Error",
            "Sly has unexpectedly crashed. An error report has been generated.",
            UIAlertControllerStyle.Alert
        )

        var dismissed = false

        val action = UIAlertAction.actionWithTitleStyleHandler(
            "Exit Application",
            UIAlertActionStyle.Cancel,
            { dismissed = true }
        )

        alert.addAction(action)

        rootViewController.presentViewControllerAnimatedCompletion(alert, true, null)

        val runLoop = CoreFoundation.CFRunLoopGetCurrent()
        val allModes = CoreFoundation.CFRunLoopCopyAllModes(runLoop)

        //since the main loop is no longer running, we need to run it to handle dialog errors
        try {
            @Suppress("UNCHECKED_CAST")
            val modes = ObjCRuntime.cast(allModes, NSArray::class.java) as NSArray<String>

            while (!dismissed) {
                for (mode in modes) {
                    val nsString = NSString.stringWithString(mode)

                    CoreFoundation.CFRunLoopRunInMode(
                        ObjCRuntime.cast(nsString, CFStringRef::class.java),
                        0.001,
                        0
                    )
                }
            }
        }
        finally {
            CoreFoundation.CFRelease(allModes)
        }
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

    override fun applicationDidRegisterUserNotificationSettings(application: UIApplication, notificationSettings: UIUserNotificationSettings) {
        if (notificationSettings.types() != UIUserNotificationType.None) {
            log.debug("Notifications allowed by user")

            application.registerForRemoteNotifications()
        }
        else {
            log.debug("Notifications disabled by user")

            val d = notificationTokenDeferred
            if (d == null) {
                log.error("Remote notification completed but no deferred available")
                return
            }

            notificationTokenDeferred = null

            d.resolve(null)
        }
    }

    override fun applicationDidRegisterForRemoteNotificationsWithDeviceToken(application: UIApplication, deviceToken: NSData) {
        val length = deviceToken.length().toInt()

        val builder = StringBuilder()

        val bytePtr = deviceToken.bytes().bytePtr

        (0..length-1).forEach {
            val b = bytePtr[it]
            builder.append("%02x".format(b))
        }

        val tokenString = builder.toString()

        log.debug("Successfully registered for remote notifications; token={}", tokenString)

        val d = notificationTokenDeferred
        if (d == null) {
            log.error("Got token for remote notification but no deferred available")
            return
        }

        notificationTokenDeferred = null

        d.resolve(DeviceTokens(tokenString, null))
    }

    override fun applicationDidFailToRegisterForRemoteNotificationsWithError(application: UIApplication, error: NSError) {
        log.error("Failed to register for remote notifications: {}", error.description())

        val d = notificationTokenDeferred
        if (d == null) {
            log.error("Remote notification failure but no deferred available")
            return
        }

        notificationTokenDeferred = null

        d.reject(NotificationRegistrationException(error.description()))
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

    override fun applicationDidEnterBackground(application: UIApplication) {
        log.debug("Application entered background")
        app.isInBackground = true
    }

    override fun applicationWillEnterForeground(application: UIApplication) {
        log.debug("Application will enter foreground")

        //we refresh here, incase the user modified notification settings while we were backgrounded/suspended
        if (app.appComponent.appConfigService.pushNotificationsPermRequested)
            app.appComponent.tokenFetchService.refresh()
    }

    override fun applicationDidBecomeActive(application: UIApplication) {
        log.debug("Application has become active")

        hideScreenProtection()

        //moved this here so that we have updated network status by this point, as the network status isn't actually
        //updated until we get here, even if we manually call SCNetworkReachabilityGetFlags beforehand
        app.isInBackground = false

        uiVisibility.onNext(true)
    }

    override fun applicationDidReceiveLocalNotification(application: UIApplication, notification: UILocalNotification) {
        if (notification.category() == NOTIFICATION_CATEGORY_CHAT_MESSAGE) {
            val conversationData = notification.userInfo()[USERINFO_CONVERSATION_ID_KEY]
            val conversationIdString = conversationData as String
            val conversationId = ConversationId.fromString(conversationIdString)

            log.debug("Opened from local notification for $conversationId")

            webViewController.navigationService?.goTo(getNavigationPageConversation(conversationId))
        }
        else {
            //some notifications don't support direct actions (eg: NOTIFICATION_CATEGORY_OFFLINE)
            log.debug("Ignoring local notification with category={}", notification.category())
        }
    }

    private fun deserializeNotification(userInfo: NSDictionary<*, *>): OfflineMessagesPushNotification? {
        @Suppress("UNCHECKED_CAST")
        val data = userInfo["data"] as NSDictionary<String, Any>

        val type = data["type"] as String
        val version = (data["version"] as NSNumber).intValue()

        if (type != OfflineMessagesPushNotification.TYPE) {
            log.warn("Received unknown remote notification: $type")
            return null
        }

        if (version != 1) {
            log.warn("Unsupported version for offline messages: {}", version)
            return null
        }

        val accountStr = data["account"] as String
        val account = SlyAddress.fromString(accountStr) ?: error("Invalid account format: $accountStr")

        val accountName = data["accountName"] as String

        @Suppress("UNCHECKED_CAST")
        val infoSerialized = data["info"] as NSArray<NSDictionary<String, *>>

        val info = infoSerialized.map {
            OfflineMessageInfo(
                it["name"] as String,
                (it["pendingCount"] as NSNumber).intValue()
            )
        }

        return OfflineMessagesPushNotification(account, accountName, info)
    }

    override fun applicationDidReceiveRemoteNotificationFetchCompletionHandler(application: UIApplication, userInfo: NSDictionary<*, *>, completionHandler: UIApplicationDelegate.Block_applicationDidReceiveRemoteNotificationFetchCompletionHandler) {
        log.debug("Received remote notification")

        val message = try {
            deserializeNotification(userInfo)
        }
        catch (e: Exception) {
            log.error("Failed to deserialize remote notification: {}", e.message, e)
            completionHandler.call_applicationDidReceiveRemoteNotificationFetchCompletionHandler(UIBackgroundFetchResult.NoData)
            return
        }

        if (message == null) {
            log.warn("Discarding unsupported message type")
            completionHandler.call_applicationDidReceiveRemoteNotificationFetchCompletionHandler(UIBackgroundFetchResult.NoData)
            return
        }

        var taskId: Long = 0

        taskId = application.beginBackgroundTaskWithExpirationHandler {
            log.info("Background time for offline message fetch expired")

            application.endBackgroundTask(taskId)
        }

        app.addOnAutoLoginListener { app ->
            if (app.loginState == LoginState.LOGGED_IN) {
                if (message.account == app.userComponent!!.userLoginData.address) {
                    var subscription: Subscription? = null

                    subscription = app.userComponent!!.readMessageQueueIsEmpty.subscribe {
                        log.info("Finished processing offline messages")

                        //XXX need a better way to do this... we need to wait until the NotificationService completes
                        //before ending our bg state
                        Observable.timer(2, TimeUnit.SECONDS).subscribe {
                            log.info("Ending background status")
                            application.endBackgroundTask(taskId)
                        }

                        subscription!!.unsubscribe()
                    }

                    app.fetchOfflineMessages()
                }
                else {
                    log.warn("Got offline message notification for different account (${message.account}); ignoring")
                    application.endBackgroundTask(taskId)
                }
            }
            else {
                log.debug("Account offline")

                notificationService.showLoggedOutNotification(message)
                application.endBackgroundTask(taskId)
            }
        }

        completionHandler.call_applicationDidReceiveRemoteNotificationFetchCompletionHandler(UIBackgroundFetchResult.NewData)
    }

    fun uiLoadComplete() {
        webViewController.hideLaunchScreenView()
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
            popoverController.setSourceRect(webViewController.calcPopoverRect())
            popoverController.setDelegate(this)
        }
    }

    //workaround as per https://github.com/multi-os-engine/multi-os-engine/issues/70
    override fun popoverPresentationControllerWillRepositionPopoverToRectInView(
        popoverPresentationController: UIPopoverPresentationController,
        rect: CGRect,
        @ReferenceInfo(depth = 1, type = UIView::class) view: Ptr<UIView>
    ) {
        val newRect = webViewController.calcPopoverRect()
        rect.setOrigin(newRect.origin())
        rect.setSize(newRect.size())
    }
}
