package io.slychat.messenger.core.relay.base.java

import io.slychat.messenger.core.crypto.tls.SSLConfigurator
import io.slychat.messenger.core.crypto.tls.SSLSocketSSLSettingsAdapter
import io.slychat.messenger.core.crypto.tls.verifyHostname
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.security.cert.CertificateException
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

//TODO can we use TLS12SocketFactory somehow?
private class RelaySocketFactory(
    private val sslConfigurator: SSLConfigurator
) : SSLSocketFactory() {
    private val sslContext = SSLContext.getInstance("TLSv1.2")

    private val factory = sslContext.socketFactory
    override fun getDefaultCipherSuites(): Array<out String> {
        return factory.defaultCipherSuites
    }

    override fun createSocket(p0: InetAddress, p1: Int, p2: InetAddress, p3: Int): Socket {
        return configure(factory.createSocket(p0, p1, p2, p3))
    }

    override fun createSocket(s: Socket, host: String, port: Int, autoClose: Boolean): Socket {
        val socket = factory.createSocket(s, host, port, autoClose) as SSLSocket

        return configure(socket)
    }

    override fun createSocket(host: String, port: Int): Socket {
        return configure(factory.createSocket(host, port))
    }

    override fun createSocket(host: String, port: Int, p2: InetAddress, p3: Int): Socket {
        return configure(factory.createSocket(host, port, p2, p3))
    }

    override fun createSocket(p0: InetAddress, p1: Int): Socket {
        return configure(factory.createSocket(p0, p1))
    }

    private fun configure(socket: Socket): Socket {
        socket as? SSLSocket ?: return socket

        sslConfigurator.configure(SSLSocketSSLSettingsAdapter(socket))

        return socket
    }

    override fun getSupportedCipherSuites(): Array<out String> {
        return factory.supportedCipherSuites
    }
}

internal class RelaySocketConnector(
    private val relayAddress: InetSocketAddress,
    private val sslConfigurator: SSLConfigurator
) : SocketConnector {
    private val factory = RelaySocketFactory(sslConfigurator)
    private var socket: Socket? = null

    override fun connect(): Pair<InputStream, OutputStream> {
        assert(socket == null) { "connect() called but socket isn't null" }

        val socket = factory.createSocket(relayAddress.hostName, relayAddress.port) as SSLSocket
        try {
            val hostname = (socket.remoteSocketAddress as InetSocketAddress).hostName

            val isHostVerified = if (sslConfigurator.disableHostnameVerification)
                true
            else {
                verifyHostname(hostname, socket.session)
            }

            //mimic HttpsURLConnection's behavior
            if (!isHostVerified)
                throw CertificateException("No name matching $hostname found")

            this.socket = socket
            return socket.inputStream to socket.outputStream
        }
        catch (t: Throwable) {
            socket.close()
            throw t
        }
    }

    override fun disconnect() {
        socket?.apply { close() }
        socket = null
    }
}