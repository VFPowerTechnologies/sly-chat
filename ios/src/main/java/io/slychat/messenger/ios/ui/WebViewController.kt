package io.slychat.messenger.ios.ui

import apple.foundation.NSBundle
import apple.foundation.NSURL
import apple.foundation.NSURLRequest
import apple.uikit.UIColor
import apple.uikit.UIScreen
import apple.uikit.UIView
import apple.uikit.UIViewController
import apple.webkit.WKUserContentController
import apple.webkit.WKWebView
import apple.webkit.WKWebViewConfiguration
import com.vfpowertech.jsbridge.core.dispatcher.Dispatcher
import io.slychat.messenger.ios.webengine.IOSWebEngineInterface
import io.slychat.messenger.services.di.ApplicationComponent
import io.slychat.messenger.services.ui.registerCoreServicesOnDispatcher
import org.moe.natj.general.Pointer
import org.moe.natj.general.ann.Owned
import org.moe.natj.general.ann.RegisterOnStartup
import org.moe.natj.objc.ann.Selector

@RegisterOnStartup
class WebViewController private constructor(peer: Pointer) : UIViewController(peer) {
    @Selector("init")
    external override fun init(): WebViewController

    private lateinit var dispatcher: Dispatcher

    private lateinit var appComponent: ApplicationComponent

    fun initWithAppComponent(applicationComponent: ApplicationComponent): WebViewController {
        init()

        appComponent = applicationComponent

        return this
    }

    override fun viewDidLoad() {
        val bounds = UIScreen.mainScreen().bounds()
        val contentView = UIView.alloc().initWithFrame(bounds)
        contentView.setBackgroundColor(UIColor.darkGrayColor())
        setView(contentView)

        val configuration = WKWebViewConfiguration.alloc().init()
        val userContentController = WKUserContentController.alloc().init()
        configuration.setUserContentController(userContentController)

        val webView = WKWebView.alloc().initWithFrameConfiguration(contentView.frame(), configuration)

        val webEngineInterface = IOSWebEngineInterface(webView)

        dispatcher = Dispatcher(webEngineInterface)

        println("Registering services")
        registerCoreServicesOnDispatcher(dispatcher, appComponent)

        val path = NSBundle.mainBundle().pathForResourceOfTypeInDirectory("index", "html", "ui")
        if (path != null) {
            println("Loading file at $path")
            webView.loadRequest(NSURLRequest.requestWithURL(NSURL.fileURLWithPath(path)))
        }
        else
            println("Unable to find resource in bundle")

        contentView.addSubview(webView)
    }

    companion object {
        @JvmStatic
        @Owned
        external fun alloc(): WebViewController
    }
}

