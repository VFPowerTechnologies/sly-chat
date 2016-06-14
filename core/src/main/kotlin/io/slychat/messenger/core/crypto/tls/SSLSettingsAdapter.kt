package io.slychat.messenger.core.crypto.tls

import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLSocket

interface SSLSettingsAdapter {
    fun setEnabledCipherSuites(cipherSuites: Array<String>)
    fun setEnabledProtocols(protocols: Array<String>)
}

class SSLSocketSSLSettingsAdapter(private val sslSocket: SSLSocket) : SSLSettingsAdapter {
    override fun setEnabledCipherSuites(cipherSuites: Array<String>) {
        sslSocket.enabledCipherSuites = cipherSuites
    }

    override fun setEnabledProtocols(protocols: Array<String>) {
        sslSocket.enabledProtocols = protocols
    }
}

class SSLEngineSSLSettingsAdapter(private val sslEngine: SSLEngine) : SSLSettingsAdapter {
    override fun setEnabledCipherSuites(cipherSuites: Array<String>) {
        sslEngine.enabledCipherSuites = cipherSuites
    }

    override fun setEnabledProtocols(protocols: Array<String>) {
        sslEngine.enabledProtocols = protocols
    }
}

//just done to centralize settings; since SSLSocket and SSLEngine don't have a common interface we just use an adapter,
//in the off chance we need to configure some other library/etc
fun configureSSL(sslSettingsAdapter: SSLSettingsAdapter) {
    sslSettingsAdapter.setEnabledProtocols(arrayOf("TLSv1.2"))
    sslSettingsAdapter.setEnabledCipherSuites(arrayOf("TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384"))
}

fun configureSSL(sslSocket: SSLSocket) = configureSSL(SSLSocketSSLSettingsAdapter(sslSocket))
fun configureSSL(sslEngine: SSLEngine) = configureSSL(SSLEngineSSLSettingsAdapter(sslEngine))
