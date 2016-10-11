package io.slychat.messenger.core.crypto.tls

import java.net.HttpURLConnection
import java.net.URL
import java.security.cert.CertificateFactory
import java.security.cert.X509CRL

class JavaHttpCRLFetcher(
    private val connectTimeoutMs: Int = 3000,
    private val readTimeoutMs: Int = 3000
) : CRLFetcher {
    private val certFactory = CertificateFactory.getInstance("X.509")

    override fun get(url: String): X509CRL {
        val connection = URL(url).openConnection() as HttpURLConnection

        connection.doInput = true
        connection.requestMethod = "GET"
        connection.useCaches = false

        connection.connectTimeout = connectTimeoutMs
        connection.readTimeout = readTimeoutMs

        return connection.inputStream.use {
            certFactory.generateCRL(it)
        } as X509CRL
    }
}