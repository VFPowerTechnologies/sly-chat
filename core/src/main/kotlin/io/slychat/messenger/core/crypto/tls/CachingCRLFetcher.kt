package io.slychat.messenger.core.crypto.tls

import io.slychat.messenger.core.currentTimestamp
import java.security.cert.X509CRL
import java.util.*
import java.util.concurrent.TimeUnit

/** Simple caching CRLFetcher. Wraps an existing fetcher and caches the results. Thread-safe. */
class CachingCRLFetcher(
    private val fetcher: CRLFetcher,
    private val expiresInMs: Long = TimeUnit.MINUTES.toMillis(30),
    private val initialCache: Map<String, X509CRL> = emptyMap()
) : CRLFetcher {
    private val cache = HashMap<String, X509CRL>(initialCache)
    private val expiresAt = HashMap<String, Long>()

    private fun getFromCache(url: String): X509CRL? {
        val cached = cache[url] ?: return null

        //should never occur
        val t = expiresAt[url] ?: return null

        return if (currentTimestamp() >= t)
            null
        else
            cached
    }

    private fun addToCache(url: String, crl: X509CRL) {
        cache[url] = crl

        val revocationDate = crl.nextUpdate.time
        val expiration = currentTimestamp() + expiresInMs

        expiresAt[url] = Math.min(revocationDate, expiration)
    }

    override fun get(url: String): X509CRL {
        synchronized(this) {
            val cached = getFromCache(url)
            if (cached != null)
                return cached

            val crl = fetcher.get(url)
            addToCache(url, crl)
            return crl
        }
    }

    internal fun isCached(url: String): Boolean = synchronized(this) {
        url in cache
    }

    internal fun getFromCacheRaw(url: String): X509CRL? = synchronized(this) {
        cache[url]
    }
}