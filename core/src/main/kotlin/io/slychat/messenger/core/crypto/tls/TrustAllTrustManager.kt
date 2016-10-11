package io.slychat.messenger.core.crypto.tls

import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

class TrustAllTrustManager : X509TrustManager {
    override fun checkClientTrusted(chain: Array<out X509Certificate>, p1: String) {
        throw UnsupportedOperationException()
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>, p1: String) {
    }

    override fun getAcceptedIssuers(): Array<out X509Certificate> = emptyArray()
}