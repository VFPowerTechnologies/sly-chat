package io.slychat.messenger.core.crypto.tls

import java.security.KeyStore
import java.security.cert.X509Certificate
import javax.net.ssl.*

//XXX we only use this for sly stuff; so don't care about other usages
class SSLConfigurator(
    val trustedRoot: X509Certificate,
    val crlFetcher: CRLFetcher,
    val disableHostnameVerification: Boolean,
    val disableCRLVerification: Boolean,
    val disableCertificationVerification: Boolean
) {
    private fun buildTrustManagers(): Array<TrustManager> {
        return arrayOf(if (disableCertificationVerification)
            TrustAllTrustManager()
        else {
            val trustStore = KeyStore.getInstance("PKCS12")
            trustStore.load(null, null)
            trustStore.setCertificateEntry("1", trustedRoot)

            val tmf = TrustManagerFactory.getInstance("PKIX")
            tmf.init(trustStore)

            val tm = tmf.trustManagers.first() as X509TrustManager

            if (!disableCRLVerification)
                CRLValidatingTrustManager(tm, trustedRoot, crlFetcher)
            else
                tm
        })
    }

    fun configure(sslSettingsAdapter: SSLSettingsAdapter) {
        sslSettingsAdapter.setEnabledProtocols(arrayOf("TLSv1.2"))
        sslSettingsAdapter.setEnabledCipherSuites(arrayOf("TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384"))
    }

    fun configure(connection: HttpsURLConnection) {
        val sslContext = SSLContext.getInstance("TLSv1.2")

        sslContext.init(null, buildTrustManagers(), null)

        connection.sslSocketFactory = TLS12SocketFactory(sslContext, this)

        if (disableHostnameVerification) {
            connection.hostnameVerifier = HostnameVerifier { hostname, session ->
                true
            }
        }
    }

    fun createEngine(): SSLEngine {
        val sslContext = SSLContext.getInstance("TLSv1.2")

        val trustManagers = buildTrustManagers()

        sslContext.init(null, trustManagers, null)

        val engine = sslContext.createSSLEngine()
        configure(SSLEngineSSLSettingsAdapter(engine))

        return engine
    }
}