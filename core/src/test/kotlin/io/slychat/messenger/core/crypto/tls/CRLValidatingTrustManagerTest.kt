package io.slychat.messenger.core.crypto.tls

import org.junit.Test
import java.io.InputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509CRL
import java.security.cert.X509Certificate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CRLValidatingTrustManagerTest {
    val certFactory = CertificateFactory.getInstance("X.509")

    fun getPEMStream(name: String): InputStream =
        javaClass.getResourceAsStream("/pki/$name.pem")

    fun getCertificateChain(name: String): Collection<X509Certificate> {
        @Suppress("UNCHECKED_CAST")
        return getPEMStream("$name.cert").use {
            certFactory.generateCertificates(it)
        } as Collection<X509Certificate>
    }

    fun getSiteCertificateChain(name: String, intermediateName: String = "intermediate"): Collection<X509Certificate>
        = getCertificateChain("$intermediateName/$name.chained")

    fun getCRL(name: String): X509CRL {
        return getPEMStream("$name.crl").use {
            certFactory.generateCRL(it)
        } as X509CRL
    }

    fun getRootCACert(): X509Certificate {
        return getPEMStream("ca.cert").use {
            certFactory.generateCertificate(it)
        } as X509Certificate
    }

    fun getTestFetcher(intermediateName: String): CRLFetcher = object : CRLFetcher {
        override fun get(url: String): X509CRL {
            return when (url) {
                "http://crl.slychat.io/ca.crl.pem" -> getCRL("ca")
                "http://crl.slychat.io/intermediate.crl.pem" -> getCRL("$intermediateName/intermediate")
                else -> throw IllegalArgumentException("Invalid url: $url")
            }
        }
    }

    fun validateSiteChain(name: String, intermediateName: String = "intermediate") {
        val chain = getSiteCertificateChain(name, intermediateName)
        validateChainCRLs(chain.toTypedArray(), getRootCACert(), getTestFetcher(intermediateName))
    }

    @Test
    fun `getCertificateDistributionURIs should return all available URIs for the given cert`() {
        val cert = getSiteCertificateChain("api.slychat.io").first()

        val uris = getCertificateCRLDistributionURIs(cert)

        assertEquals(listOf("http://crl.slychat.io/intermediate.crl.pem"), uris.toList())
    }

    @Test
    fun `validateChainCRLs should not throw if the cert is not contained in any of the CRLS`() {
        validateSiteChain("api.slychat.io")
    }

    @Test
    fun `validateChainCRLs throw if the cert is contained in the intermediate CRL`() {
        assertFailsWith<CertificateRevokedException> {
            validateSiteChain("revoked.slychat.io")
        }
    }

    @Test
    fun `validateChainCRLs throw if the intermediate CA's cert is contained in the root CA CRL`() {
        assertFailsWith<CertificateRevokedException> {
            validateSiteChain("revoked-ca.slychat.io", "revoked-intermediate")
        }
    }
}