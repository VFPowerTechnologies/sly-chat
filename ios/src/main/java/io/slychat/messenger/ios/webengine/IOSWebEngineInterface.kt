package io.slychat.messenger.ios.webengine

import apple.foundation.NSArray
import apple.foundation.NSNumber
import apple.webkit.WKScriptMessage
import apple.webkit.WKUserContentController
import apple.webkit.WKWebView
import apple.webkit.protocol.WKScriptMessageHandler
import com.vfpowertech.jsbridge.core.dispatcher.Dispatcher
import com.vfpowertech.jsbridge.core.dispatcher.WebEngineInterface
import org.slf4j.LoggerFactory

class IOSWebEngineInterface(private val webView: WKWebView) : WKScriptMessageHandler, WebEngineInterface {
    private val log = LoggerFactory.getLogger(javaClass)
    private lateinit var dispatcher: Dispatcher

    init {
        val userContentController = webView.configuration().userContentController()

        //since there's no way to hook console.log
        userContentController.addScriptMessageHandlerName(this, "log")
        userContentController.addScriptMessageHandlerName(this, "call")
        userContentController.addScriptMessageHandlerName(this, "callbackFromJS")
    }

    override fun register(dispatcher: Dispatcher) {
        this.dispatcher = dispatcher
    }

    override fun runJS(js: String) {
        webView.evaluateJavaScriptCompletionHandler(js) { obj, error ->
            if (error != null) {
                log.error("Error during js execution: $error")
            }
        }
    }

    override fun userContentControllerDidReceiveScriptMessage(userContentController: WKUserContentController, message: WKScriptMessage) {
        val name = message.name()
        val body = message.body()
        when (name) {
            "call" -> {
                val args = body as NSArray<*>
                dispatcher.call(
                        args[0].toString(),
                        args[1].toString(),
                        args[2].toString(),
                        args[3].toString())
            }

            "callbackFromJS" -> {
                val args = body as NSArray<*>

                dispatcher.callbackFromJS(
                        args[0].toString(),
                        (args[1] as NSNumber).boolValue(),
                        args[2].toString())
            }

            else -> log.error("Unsupported handler: {}", name)
        }
    }
}