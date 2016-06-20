@file:JvmName("TLSUtils")
package io.slychat.messenger.core.crypto.tls

import org.spongycastle.asn1.x500.X500Name
import org.spongycastle.asn1.x500.style.BCStyle
import org.spongycastle.asn1.x500.style.IETFUtils
import javax.net.ssl.SSLSession
import javax.security.auth.x500.X500Principal

/**
 * Verify that one of the given certificate's CNs match the hostname.
 *
 * @return True if a match exists, false if no match, the chain is empty, or the cert contains no CNs.
 */
fun verifyHostname(hostname: String, session: SSLSession): Boolean {
    val chain = session.peerCertificateChain
    if (chain.isEmpty())
        return false

    val cert = chain[0]

    //doing this avoids having to bring in the entire bouncy castle pki or jca api
    //subjectDN is a sun.security.x509.X500Name
    val principal = X500Principal(cert.subjectDN.name)

    val x500Name = X500Name.getInstance(principal.encoded)

    val cns = x500Name.getRDNs(BCStyle.CN)
    if (cns.isEmpty())
        return false

    val cnStrings = cns.map { IETFUtils.valueToString(it.first.value) }

    return cnStrings.contains(hostname)
}