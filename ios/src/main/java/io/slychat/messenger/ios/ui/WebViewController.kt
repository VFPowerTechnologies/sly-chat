package io.slychat.messenger.ios.ui

import apple.foundation.NSBundle
import apple.foundation.NSNumber
import apple.foundation.NSURL
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
        //tested on ios 9
        //required to let XHR requests access file:// urls
        //requires its value to implement boolValue, so since neither moe's java bool/int classes do, we create an
        //NSNumber directly
        configuration.preferences().setValueForKey(NSNumber.alloc().initWithBool(true), "allowFileAccessFromFileURLs")

        val webView = WKWebView.alloc().initWithFrameConfiguration(contentView.frame(), configuration)

        val webEngineInterface = IOSWebEngineInterface(webView)

        dispatcher = Dispatcher(webEngineInterface)

        registerCoreServicesOnDispatcher(dispatcher, appComponent)

        val path = NSBundle.mainBundle().pathForResourceOfTypeInDirectory("index", "html", "ui")

        if (path != null) {
            val indexURL = NSURL.fileURLWithPath(path)
            val base = indexURL.URLByDeletingLastPathComponent()

            webView.loadFileURLAllowingReadAccessToURL(indexURL, base)
        }
        else
            log.error("Unable to find ui/index.html resource in bundle")

        contentView.addSubview(webView)
    }
}
