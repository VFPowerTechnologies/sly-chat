@file:JvmName("TLSUtils")
package io.slychat.messenger.core.crypto.tls

import io.slychat.messenger.core.BuildConfig
import java.io.ByteArrayInputStream
import java.security.KeyStore
import java.security.cert.CertificateFactory
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory

//just done to centralize settings; since SSLSocket and SSLEngine don't have a common interface we just use an adapter,
//in the off chance we need to configure some other library/etc
fun configureSSL(sslSettingsAdapter: SSLSettingsAdapter) {
    sslSettingsAdapter.setEnabledProtocols(arrayOf("TLSv1.2"))
    sslSettingsAdapter.setEnabledCipherSuites(arrayOf("TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384"))
}

fun configureSSL(sslSocket: SSLSocket) = configureSSL(SSLSocketSSLSettingsAdapter(sslSocket))
fun configureSSL(sslEngine: SSLEngine) = configureSSL(SSLEngineSSLSettingsAdapter(sslEngine))

fun getTrustManagers(): Array<TrustManager> {
    return if (BuildConfig.TLS_DISABLE_CERTIFICATE_VERIFICATION)
        arrayOf(TrustAllTrustManager())
    else {
        val trustStore = KeyStore.getInstance("PKCS12")
        trustStore.load(null, null)
        val cert = CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(BuildConfig.caCert))
        trustStore.setCertificateEntry("1", cert)

        val tmf = TrustManagerFactory.getInstance("PKIX")
        tmf.init(trustStore)

        tmf.trustManagers
    }
}
