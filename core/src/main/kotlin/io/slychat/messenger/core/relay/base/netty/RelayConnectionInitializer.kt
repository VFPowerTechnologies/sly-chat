package io.slychat.messenger.core.relay.base.netty

import io.netty.channel.ChannelInitializer
import io.netty.channel.socket.SocketChannel
import io.netty.handler.ssl.SslHandler
import io.slychat.messenger.core.relay.base.RelayConnectionEvent
import io.slychat.messenger.core.tls.TrustAllTrustManager
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
        //FIXME
        sslContext.init(null, arrayOf(TrustAllTrustManager()), null)

        val engine = sslContext.createSSLEngine()
        engine.enabledProtocols = arrayOf("TLSv1.2")
        engine.enabledCipherSuites = arrayOf("TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384")
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

