package io.slychat.messenger.core.crypto.tls

import org.spongycastle.asn1.ASN1InputStream
import org.spongycastle.asn1.ASN1OctetString
import org.spongycastle.asn1.DERIA5String
import org.spongycastle.asn1.x509.*
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.*
import javax.net.ssl.X509TrustManager
import javax.security.auth.x500.X500Principal

/**
 * Verifies every certificate in the chain.
 *
 * This is NOT a general-purpose CRL revocation checker, as there doesn't seem to be any way to get at the JVM's
 * default list of trusted certs in a platform independent way. Currently we only contact external services over HTTP
 * so this isn't an issue.
 *
 * Might need to actually use a custom cert checking function eventually if this becomes an issue, since we can't just
 * wrap a TrustManager and do proper CRL checking without access to a list of trusted root certs.
 *
 * @throws CertificateRevokedException If any of the certificates in the chain are revoked.
 */
internal fun validateChainCRLs(chain: Array<out X509Certificate>, trustedRoot: X509Certificate, crlFetcher: CRLFetcher) {
    if (chain.size < 2)
        return

    for (i in 0..chain.size-2) {
        val cert = chain[i]
        val issuerCert = chain[i+1]
        checkCertificateCRL(cert, issuerCert, crlFetcher)
    }

    val last = chain.last()
    if (last == trustedRoot)
        return

    checkCertificateCRL(last, trustedRoot, crlFetcher)
}

/**
 * Retrieves the list of URIs in the given's certificate's CRLDistributionPoints extension.
 *
 * @return An empty list if no extension exists, or if no URIs are listed in the extension data.
 */
internal fun getCertificateCRLDistributionURIs(cert: X509Certificate): Collection<String> {
    val der = cert.getExtensionValue(Extension.cRLDistributionPoints.id) ?: return emptyList()

    val octetString = ASN1InputStream(der).use {
        ASN1OctetString.getInstance(it.readObject())
    }

    val derObject = ASN1InputStream(octetString.octets).readObject()
    val crlDistPoints = CRLDistPoint.getInstance(derObject)

    val distributionURIs = mutableSetOf<String>()
    for (distPoint in crlDistPoints.distributionPoints) {
        val name = distPoint.distributionPoint
        if (name != null && name.type == DistributionPointName.FULL_NAME) {
            val generalNames = GeneralNames.getInstance(name.name).names

            val uris = generalNames.filter { it.tagNo == GeneralName.uniformResourceIdentifier }

            distributionURIs.addAll(
                uris.map { DERIA5String.getInstance(it.name).string }
            )
        }
    }

    return distributionURIs
}

//can't use CertificateRevokedException as it's android API 24+
//ditto with CRLReason
class CertificateRevokedException(
    val certificate: X509Certificate,
    val date: Date,
    val issuer: X500Principal
) : CertificateException("DN: <<${certificate.subjectDN}>> has been revoked")

/**
 * Checks all available distribution points for an expired certificate.
 *
 * @throws CertificateRevokedException If the certificate is listed as revoked in the returned CRL.
 * @throws SignatureException CRL signature doesn't match signer.
 * @throws CRLException Invalid crl data.
 */
internal fun checkCertificateCRL(cert: X509Certificate, issuerCert: X509Certificate, crlFetcher: CRLFetcher) {
    val distributionURIs = getCertificateCRLDistributionURIs(cert)

    if (distributionURIs.isEmpty())
        return

    //XXX not sure it's actually required to check all the points or not
    distributionURIs.forEach { url ->
        val crl = crlFetcher.get(url)

        crl.verify(issuerCert.publicKey)

        val entry = crl.getRevokedCertificate(cert) ?: return

        val date = entry.revocationDate
        //can be null
        val issuer = entry.certificateIssuer ?: X500Principal(cert.issuerDN.name)

        throw CertificateRevokedException(
            cert,
            date,
            issuer
        )
    }
}

/**
 * Augments an existing X509TrustManager with CRL revocation checking. Requires the list of trusted roots to
 * complete CRL checking.
 *
 * @note Currently only supports a single root cert, so not used with external services.
 */
class CRLValidatingTrustManager(
    private val underlying: X509TrustManager,
    private val trustedRoot: X509Certificate,
    private val crlFetcher: CRLFetcher
) : X509TrustManager {
    override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) {
        return underlying.checkClientTrusted(chain, authType)
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) {
        underlying.checkServerTrusted(chain, authType)

        validateChainCRLs(chain, trustedRoot, crlFetcher)
    }

    override fun getAcceptedIssuers(): Array<out X509Certificate> {
        return underlying.acceptedIssuers
    }
}
