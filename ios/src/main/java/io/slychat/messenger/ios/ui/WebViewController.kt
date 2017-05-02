package io.slychat.messenger.ios.ui

import apple.coregraphics.struct.CGPoint
import apple.coregraphics.struct.CGRect
import apple.coregraphics.struct.CGSize
import apple.foundation.NSBundle
import apple.foundation.NSDictionary
import apple.foundation.NSNumber
import apple.foundation.NSURL
import apple.mobilecoreservices.c.MobileCoreServices
import apple.uikit.*
import apple.uikit.c.UIKit
import apple.uikit.enums.*
import apple.uikit.protocol.UIDocumentMenuDelegate
import apple.uikit.protocol.UIDocumentPickerDelegate
import apple.uikit.protocol.UIImagePickerControllerDelegate
import apple.uikit.protocol.UIPopoverPresentationControllerDelegate
import apple.webkit.WKNavigation
import apple.webkit.WKUserContentController
import apple.webkit.WKWebView
import apple.webkit.WKWebViewConfiguration
import apple.webkit.protocol.WKNavigationDelegate
import com.vfpowertech.jsbridge.core.dispatcher.Dispatcher
import io.slychat.messenger.ios.*
import io.slychat.messenger.ios.webengine.IOSWebEngineInterface
import io.slychat.messenger.services.di.ApplicationComponent
import io.slychat.messenger.services.ui.js.NavigationService
import io.slychat.messenger.services.ui.js.javatojs.NavigationServiceToJSProxy
import io.slychat.messenger.services.ui.registerCoreServicesOnDispatcher
import nl.komponents.kovenant.Deferred
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.deferred
import org.moe.natj.general.Pointer
import org.moe.natj.general.ann.Owned
import org.moe.natj.general.ann.ReferenceInfo
import org.moe.natj.general.ann.RegisterOnStartup
import org.moe.natj.general.ptr.Ptr
import org.moe.natj.objc.ann.Selector
import org.slf4j.LoggerFactory

@RegisterOnStartup
class WebViewController private constructor(peer: Pointer) :
    UIViewController(peer),
    UIPopoverPresentationControllerDelegate,
    UIDocumentMenuDelegate,
    UIDocumentPickerDelegate,
    UIImagePickerControllerDelegate
{
    companion object {
        @JvmStatic
        @Owned
        external fun alloc(): WebViewController
    }

    //null = cancelled
    private var fileSelectionDeferred: Deferred<String?, Exception>? = null

    private val log = LoggerFactory.getLogger(javaClass)

    private lateinit var dispatcher: Dispatcher

    private lateinit var appComponent: ApplicationComponent

    private var launchScreenView: UIView? = null
    private var isLaunchScreenClosing = false

    var navigationService: NavigationService? = null
        private set

    @Selector("init")
    external override fun init(): WebViewController

    fun initWithAppComponent(applicationComponent: ApplicationComponent): WebViewController {
        init()

        appComponent = applicationComponent

        return this
    }

    override fun loadView() {
        val bounds = UIScreen.mainScreen().bounds()
        val container = UIView.alloc().initWithFrame(bounds)
        container.setBackgroundColor(UIColor.darkGrayColor())
        container.setAutoresizingMask(UIViewAutoresizing.FlexibleHeight or UIViewAutoresizing.FlexibleWidth)
        setView(container)

        val configuration = WKWebViewConfiguration.alloc().init()
        val userContentController = WKUserContentController.alloc().init()
        configuration.setUserContentController(userContentController)
        //tested on ios 9
        //required to let XHR requests access file:// urls
        //requires its value to implement boolValue, so since neither moe's java bool/int classes do, we create an
        //NSNumber directly
        configuration.preferences().setValueForKey(NSNumber.alloc().initWithBool(true), "allowFileAccessFromFileURLs")

        val webView = WKWebView.alloc().initWithFrameConfiguration(container.frame(), configuration)
        webView.setAutoresizingMask(UIViewAutoresizing.FlexibleWidth or UIViewAutoresizing.FlexibleHeight)
        val scrollView = webView.scrollView()
        scrollView.setBounces(false)

        val webEngineInterface = IOSWebEngineInterface(webView)

        dispatcher = Dispatcher(webEngineInterface)

        registerCoreServicesOnDispatcher(dispatcher, appComponent)

        val path = NSBundle.mainBundle().pathForResourceOfTypeInDirectory("index", "html", "ui")

        if (path != null) {
            val indexURL = NSURL.fileURLWithPath(path)
            val base = indexURL.URLByDeletingLastPathComponent()

            webView.loadFileURLAllowingReadAccessToURL(indexURL, base)

            webView.setNavigationDelegate(object : WKNavigationDelegate {
                override fun webViewDidFinishNavigation(webView: WKWebView, navigation: WKNavigation) {
                    log.debug("Webview navigation complete")
                    navigationService = NavigationServiceToJSProxy(dispatcher)
                }
            })
        }
        else
            log.error("Unable to find ui/index.html resource in bundle")

        container.addSubview(webView)

        val storyboard = UIStoryboard.storyboardWithNameBundle("LaunchScreen", null)

        val controller = storyboard.instantiateInitialViewController()
        val launchScreenView = controller.view()
        container.addSubview(launchScreenView)

        this.launchScreenView = launchScreenView
    }

    fun hideLaunchScreenView() {
        if (isLaunchScreenClosing)
            return

        val launchView = this.launchScreenView ?: return

        isLaunchScreenClosing = true

        UIView.transitionWithViewDurationOptionsAnimationsCompletion(
            view(),
            0.4,
            UIViewAnimationOptions.TransitionCrossDissolve,
            {
                launchView.removeFromSuperview()
            },
            {
                launchScreenView = null
                isLaunchScreenClosing = false
            }
        )
    }

    /** Dismiss the current modal controller. */
    private fun dismissModalController() {
        dismissViewControllerAnimatedCompletion(true, null)
    }

    private fun resolveFileSelection(path: String?) {
        val d = fileSelectionDeferred ?: return
        d.resolve(path)

        fileSelectionDeferred = null
    }

    fun displayFileSelectMenu(): Promise<String?, Exception> {
        //TODO verify
        val d = deferred<String?, Exception>()
        fileSelectionDeferred = d

        displayDocumentPickerMenu()

        return d.promise
    }

    private fun displayDocumentPickerMenu() {
        val utis = nsarray(
            MobileCoreServices.kUTTypeData().toNSString().toString(),
            MobileCoreServices.kUTTypeContent().toNSString().toString()
        )

        val controller = UIDocumentMenuViewController.alloc().initWithDocumentTypesInMode(
            utis,
            UIDocumentPickerMode.Open
        )

        controller.addOptionWithTitleImageOrderHandler("Photo Library", null, UIDocumentMenuOrder.First) {
            dismissModalController()
            displayImagePicker()
        }

        controller.setDelegate(this)

        presentViewControllerAnimatedCompletion(controller, true, null)
    }

    private fun displayImagePicker() {
        val picker = UIImagePickerController.alloc().init()
        picker.setAllowsEditing(false)
        picker.setSourceType(UIImagePickerControllerSourceType.PhotoLibrary)
        picker.setDelegate(this)

        val isIPad = UIDevice.currentDevice().userInterfaceIdiom() == UIUserInterfaceIdiom.Pad

        if (isIPad) {
            picker.setModalPresentationStyle(UIModalPresentationStyle.Popover)
        }

        presentViewControllerAnimatedCompletion(picker, true, null)

        val controller = picker.popoverPresentationController()
        if (controller != null) {
            controller.setSourceView(view())
            controller.setSourceRect(calcPopoverRect())
            controller.setDelegate(this)
        }
    }

    fun calcPopoverRect(): CGRect {
        val frame = view().frame()
        val x = frame.size().width() / 2

        return CGRect(CGPoint(x, 0.0), CGSize(0.0, 20.0))
    }

    /* UIDocumentMenuDelegate */
    override fun documentMenuDidPickDocumentPicker(documentMenu: UIDocumentMenuViewController, documentPicker: UIDocumentPickerViewController) {
        dismissModalController()

        documentPicker.setDelegate(this)
        presentViewControllerAnimatedCompletion(documentPicker, true, null)
    }

    override fun documentMenuWasCancelled(documentMenu: UIDocumentMenuViewController) {
        log.info("Document provider menu cancelled")

        dismissModalController()

        resolveFileSelection(null)
    }

    /* UIDocumentPickerDelegate */
    override fun documentPickerDidPickDocumentAtURL(controller: UIDocumentPickerViewController, url: NSURL) {
        dismissModalController()
        val bookmark = url.access {
            url.bookmark().base64EncodedStringWithOptions(0)
        }

        resolveFileSelection(IOSFileAccess.BOOKMARK_SCHEMA + bookmark)
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        log.info("Image picker cancelled")

        dismissModalController()

        resolveFileSelection(null)
    }

    /* UIImagePickerControllerDelegate  */
    override fun imagePickerControllerDidFinishPickingMediaWithInfo(picker: UIImagePickerController, info: NSDictionary<String, *>) {
        dismissModalController()

        val url = info[UIKit.UIImagePickerControllerReferenceURL()] as NSURL
        resolveFileSelection(url.absoluteString())
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        log.info("Image picker cancelled")

        dismissModalController()
        resolveFileSelection(null)
    }

    /* UIPopoverPresentationControllerDelegate */

    //copied from IOSApp; should consolidate these into here
    //workaround as per https://github.com/multi-os-engine/multi-os-engine/issues/70
    override fun popoverPresentationControllerWillRepositionPopoverToRectInView(
        popoverPresentationController: UIPopoverPresentationController,
        rect: CGRect,
        @ReferenceInfo(depth = 1, type = UIView::class) view: Ptr<UIView>
    ) {
        val newRect = calcPopoverRect()
        rect.setOrigin(newRect.origin())
        rect.setSize(newRect.size())
    }
}
