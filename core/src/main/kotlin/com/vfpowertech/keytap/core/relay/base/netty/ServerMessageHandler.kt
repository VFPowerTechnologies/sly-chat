package com.vfpowertech.keytap.core.relay.base.netty

import com.vfpowertech.keytap.core.relay.base.*
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.ByteToMessageDecoder
import rx.Observer

/** Handles converting received server messages into message instances. */
class ServerMessageHandler(private val observer: Observer<in RelayConnectionEvent>) : ByteToMessageDecoder() {
    private var lastHeader: Header? = null
    private var observerableComplete = false

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