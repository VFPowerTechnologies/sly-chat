package com.vfpowertech.keytap.core.relay.netty

import com.vfpowertech.keytap.core.relay.RelayConnectionEstablished
import com.vfpowertech.keytap.core.relay.RelayConnector
import com.vfpowertech.keytap.core.relay.ServerMessage
import io.netty.bootstrap.Bootstrap
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelOption
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioSocketChannel
import rx.Observable
import java.net.InetSocketAddress

class NettyRelayConnector : RelayConnector {
    override fun connect(address: InetSocketAddress): Observable<ServerMessage> =
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