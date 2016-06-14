package io.slychat.messenger.core.relay.base.netty

import io.netty.channel.ChannelInitializer
import io.netty.channel.socket.SocketChannel
import io.netty.handler.ssl.SslHandler
import io.slychat.messenger.core.crypto.tls.configureSSL
import io.slychat.messenger.core.crypto.tls.getTrustManagers
import io.slychat.messenger.core.relay.base.RelayConnectionEvent
import nl.komponents.kovenant.Deferred
import rx.Observer
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine

/**
 * Initializes the netty pipeline for the relay server.
 *
 * Pipeline:
 *
 * server <-> SslHandler -> ServerMessageHandler
 *                       <- ClientMessageHandler <- client
 */
class RelayConnectionInitializer(
    private val observer: Observer<in RelayConnectionEvent>,
    private val sslHandshakeComplete: Deferred<Boolean, Exception>
) : ChannelInitializer<SocketChannel>() {
    private fun getSSLEngine(): SSLEngine {
        val sslContext = SSLContext.getInstance("TLSv1.2")

        val trustManagers = getTrustManagers()

        sslContext.init(null, trustManagers, null)

        val engine = sslContext.createSSLEngine()
        configureSSL(engine)
        engine.useClientMode = true

        return engine
    }

    override fun initChannel(channel: SocketChannel) {
        val pipeline = channel.pipeline()
        pipeline.addLast(SslHandler(getSSLEngine()))
        pipeline.addLast(ServerMessageHandler(observer, sslHandshakeComplete))
        pipeline.addLast(ClientMessageHandler())
    }
}

