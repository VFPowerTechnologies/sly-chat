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

    @Selector("init")
    external override fun init(): WebViewController

    fun initWithAppComponent(applicationComponent: ApplicationComponent): WebViewController {
        init()

        appComponent = applicationComponent

        return this
    }

    override fun loadView() {
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

        registerCoreServicesOnDispatcher(dispatcher, appComponent)

        val path = NSBundle.mainBundle().pathForResourceOfTypeInDirectory("index", "html", "ui")
        if (path != null)
            webView.loadRequest(NSURLRequest.requestWithURL(NSURL.fileURLWithPath(path)))
        else
            log.error("Unable to find ui/index.html resource in bundle")

        contentView.addSubview(webView)
    }
}

