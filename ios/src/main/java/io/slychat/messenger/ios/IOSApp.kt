package io.slychat.messenger.ios

import apple.NSObject
import apple.coregraphics.struct.CGPoint
import apple.coregraphics.struct.CGRect
import apple.coregraphics.struct.CGSize
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
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.http.api.pushnotifications.PushNotificationService
import io.slychat.messenger.core.persistence.ConversationId
import io.slychat.messenger.core.pushnotifications.OfflineMessageInfo
import io.slychat.messenger.core.pushnotifications.OfflineMessagesPushNotification
import io.slychat.messenger.ios.kovenant.IOSDispatcher
import io.slychat.messenger.ios.rx.IOSMainScheduler
import io.slychat.messenger.ios.ui.WebViewController
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

        const val ACTION_CATEGORY_OFFLINE = "offline"
        const val ACTION_ID_UNREGISTER = "unregister"
        const val USERINFO_ADDRESS = "address"

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

        category.setIdentifier(ACTION_CATEGORY_OFFLINE)

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

        val addressString = userInfo[USERINFO_ADDRESS] as String?
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

        app.addOnInitListener {
            app.appComponent.pushNotificationsManager.unregister(address)
        }
    }

    override fun applicationHandleActionWithIdentifierForLocalNotificationCompletionHandler(application: UIApplication, identifier: String, notification: UILocalNotification, completionHandler: UIApplicationDelegate.Block_applicationHandleActionWithIdentifierForLocalNotificationCompletionHandler) {
        if (notification.category() == ACTION_CATEGORY_OFFLINE)
            handleOfflineCategoryAction(identifier, notification)
        else
            log.error("Unsupported action category: {}", notification.category())

        completionHandler.call_applicationHandleActionWithIdentifierForLocalNotificationCompletionHandler()
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

        notificationService = IOSNotificationService()

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
            IOSTokenFetcher(this),
            networkStatus,
            IOSMainScheduler.instance,
            UserConfig(),
            PushNotificationService.APN
        )

        app.init(platformModule)

        Sentry.setIOSDeviceName(UIDevice.currentDevice().model())

        val appComponent = app.appComponent

        buildUI(appComponent)
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
            log.info("Notifications disabled by user")

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
        val conversationData = notification.userInfo()[IOSNotificationService.CONVERSATION_ID_KEY]
        val conversationIdString = conversationData as String
        val conversationId = ConversationId.fromString(conversationIdString)

        log.debug("Opened from local notification for $conversationId")

        webViewController.navigationService?.goTo(getNavigationPageConversation(conversationId))
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

        @Suppress("UNCHECKED_CAST")
        val accountStr = data["account"] as NSDictionary<String, NSNumber>
        val userId = (accountStr["userId"] as NSNumber).longValue()
        val deviceId = (accountStr["deviceId"] as NSNumber).intValue()
        val account = SlyAddress(UserId(userId), deviceId)

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

        //TODO event for when no more messages to decrypt to end this
        taskId = application.beginBackgroundTaskWithExpirationHandler {
            log.info("Background time expired")
            application.endBackgroundTask(taskId)
        }

        app.addOnAutoLoginListener { app ->
            if (app.loginState == LoginState.LOGGED_IN) {
                //if (account == app.userComponent!!.userLoginData.address)
                app.fetchOfflineMessages()
                //else
                //    log.warn("Got GCM message for different account ($account); ignoring")
            }
            else {
                log.debug("Account offline")

                notificationService.showLoggedOutNotification(message)
            }
        }

        completionHandler.call_applicationDidReceiveRemoteNotificationFetchCompletionHandler(UIBackgroundFetchResult.NewData)
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
