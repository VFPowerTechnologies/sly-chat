package io.slychat.messenger.core.crypto.tls

import java.security.cert.X509CRL

interface CRLFetcher {
    fun get(url: String): X509CRL
}
