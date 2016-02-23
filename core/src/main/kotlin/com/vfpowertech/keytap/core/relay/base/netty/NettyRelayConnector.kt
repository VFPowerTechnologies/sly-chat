package com.vfpowertech.keytap.core.relay.base.netty

import com.vfpowertech.keytap.core.relay.base.RelayConnectionEstablished
import com.vfpowertech.keytap.core.relay.base.RelayConnector
import com.vfpowertech.keytap.core.relay.base.RelayConnectionEvent
import io.netty.bootstrap.Bootstrap
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelOption
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioSocketChannel
import rx.Observable
import java.net.InetSocketAddress

class NettyRelayConnector : RelayConnector {
    override fun connect(address: InetSocketAddress): Observable<RelayConnectionEvent> =
        Observable.create({ subscriber ->
            val bootstrap = Bootstrap()
                .group(NioEventLoopGroup())
                .channel(NioSocketChannel::class.java)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .handler(RelayConnectionInitializer(subscriber))

            val channelFuture = bootstrap.connect(address)
            channelFuture.addListener(ChannelFutureListener { cf ->
                if (!cf.isSuccess)
                    subscriber.onError(cf.cause())
                else
                    subscriber.onNext(RelayConnectionEstablished(NettyRelayConnection(cf.channel())))
            })

        })
}