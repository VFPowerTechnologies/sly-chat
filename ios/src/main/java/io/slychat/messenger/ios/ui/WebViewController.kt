package io.slychat.messenger.ios.ui

import apple.foundation.NSBundle
import apple.foundation.NSNumber
import apple.foundation.NSURL
import apple.uikit.*
import apple.uikit.enums.UIViewAnimationOptions
import apple.uikit.enums.UIViewAutoresizing
import apple.webkit.WKNavigation
import apple.webkit.WKUserContentController
import apple.webkit.WKWebView
import apple.webkit.WKWebViewConfiguration
import apple.webkit.protocol.WKNavigationDelegate
import com.vfpowertech.jsbridge.core.dispatcher.Dispatcher
import io.slychat.messenger.ios.webengine.IOSWebEngineInterface
import io.slychat.messenger.services.di.ApplicationComponent
import io.slychat.messenger.services.ui.js.NavigationService
import io.slychat.messenger.services.ui.js.javatojs.NavigationServiceToJSProxy
import io.slychat.messenger.services.ui.registerCoreServicesOnDispatcher
import org.moe.natj.general.Pointer
import org.moe.natj.general.ann.Owned
import org.moe.natj.general.ann.RegisterOnStartup
import org.moe.natj.objc.ann.Selector
import org.slf4j.LoggerFactory

@RegisterOnStartup
class WebViewController private constructor(peer: Pointer) : UIViewController(peer) {
    companion object {
        @JvmStatic
        @Owned
        external fun alloc(): WebViewController
    }

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
}
