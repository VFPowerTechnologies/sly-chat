package io.slychat.messenger.core.relay.base.java

import io.slychat.messenger.core.crypto.tls.SSLConfigurator
import io.slychat.messenger.core.crypto.tls.verifyHostname
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.security.cert.CertificateException
import javax.net.ssl.SSLSocket

internal class RelaySocketConnector(
    private val relayAddress: InetSocketAddress,
    private val sslConfigurator: SSLConfigurator
) : SocketConnector {
    private val log = LoggerFactory.getLogger(javaClass)

    private val factory = sslConfigurator.createSocketFactory()
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
        try {
            socket?.apply { close() }
        }
        catch (t: Throwable) {
            log.warn("An error occured while attempting to close the socket: {}", t.message, t)
        }

        socket = null
    }
}