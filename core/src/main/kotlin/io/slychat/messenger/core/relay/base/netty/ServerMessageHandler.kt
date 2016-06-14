package io.slychat.messenger.core.relay.base.netty

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.ByteToMessageDecoder
import io.netty.handler.ssl.SslHandler
import io.netty.handler.ssl.SslHandshakeCompletionEvent
import io.slychat.messenger.core.BuildConfig
import io.slychat.messenger.core.relay.base.*
import nl.komponents.kovenant.Deferred
import rx.Observer
import java.net.InetSocketAddress
import java.security.cert.CertificateException
import javax.net.ssl.HttpsURLConnection

/** Handles converting received server messages into message instances. */
class ServerMessageHandler(
    private val observer: Observer<in RelayConnectionEvent>,
    private val sslHandshakeComplete: Deferred<Boolean, Exception>
) : ByteToMessageDecoder() {
    private var lastHeader: Header? = null
    private var observerableComplete = false

    override fun userEventTriggered(ctx: ChannelHandlerContext, evt: Any) {
        if (evt is SslHandshakeCompletionEvent) {
            if (!evt.isSuccess) {
                //in this case, error'll call exceptionCaught, so no need to care
                sslHandshakeComplete.resolve(false)
                return
            }

            val hostname = (ctx.channel().remoteAddress() as InetSocketAddress).hostName

            val isHostVerified = if (BuildConfig.DISABLE_HOST_VERIFICATION)
                true
            else {
                val sslHandler = ctx.pipeline().get(SslHandler::class.java)
                val session = sslHandler.engine().session
                HttpsURLConnection.getDefaultHostnameVerifier().verify(hostname, session)
            }

            sslHandshakeComplete.resolve(isHostVerified)

            //mimic HttpsURLConnection's behavior
            if (!isHostVerified)
                throw CertificateException("No name matching $hostname found")
        }
    }

    override fun decode(ctx: ChannelHandlerContext, `in`: ByteBuf, out: MutableList<Any>) {
        while (`in`.isReadable) {
            var header = lastHeader

            if (header == null) {
                val available = `in`.readableBytes()

                if (available < HEADER_SIZE)
                    break

                val buffer = ByteArray(HEADER_SIZE)
                `in`.readBytes(buffer)
                header = headerFromBytes(buffer)
                if (header.contentLength == 0) {
                    observer.onNext(RelayMessage(header, ByteArray(0)))
                    continue
                }

                lastHeader = header
            }

            val available = `in`.readableBytes()
            if (available < header.contentLength)
                break

            val content = ByteArray(header.contentLength)
            `in`.readBytes(content)

            lastHeader = null
            observer.onNext(RelayMessage(header, content))
        }
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        //we don't use onNext here, as due to using observeOn, calling onNext followed by onError will ignore the
        //onNext value; see https://github.com/ReactiveX/RxJava/issues/2887
        observer.onError(cause)
        ctx.close()
        observerableComplete = true
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        if (!observerableComplete) {
            observer.onNext(RelayConnectionLost())
            observer.onCompleted()
        }
    }
}