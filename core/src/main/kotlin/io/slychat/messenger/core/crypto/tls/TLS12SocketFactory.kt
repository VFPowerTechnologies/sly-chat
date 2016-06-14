package io.slychat.messenger.core.crypto.tls

import java.net.InetAddress
import java.net.Socket
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

class TLS12SocketFactory(private val sslContext: SSLContext) : SSLSocketFactory() {
    private val internalFactory: SSLSocketFactory = sslContext.socketFactory

    private fun socketSetup(socket: Socket?): Socket? {
        if (socket == null || socket !is SSLSocket)
            return socket

        configureSSL(socket)

        return socket
    }

    override fun createSocket(p0: String, p1: Int): Socket? {
        return socketSetup(internalFactory.createSocket(p0, p1))
    }

    override fun createSocket(p0: String, p1: Int, p2: InetAddress, p3: Int): Socket? {
        return socketSetup(internalFactory.createSocket(p0, p1, p2, p3))
    }

    override fun createSocket(p0: InetAddress, p1: Int): Socket? {
        return socketSetup(internalFactory.createSocket(p0, p1))
    }

    override fun createSocket(p0: InetAddress, p1: Int, p2: InetAddress, p3: Int): Socket? {
        return socketSetup(internalFactory.createSocket(p0, p1, p2, p3))
    }

    override fun getDefaultCipherSuites(): Array<out String> {
        return internalFactory.defaultCipherSuites
    }

    override fun createSocket(p0: Socket, p1: String, p2: Int, p3: Boolean): Socket? {
        return socketSetup(internalFactory.createSocket(p0, p1, p2, p3))
    }

    override fun getSupportedCipherSuites(): Array<out String> {
        return internalFactory.supportedCipherSuites
    }
}