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
