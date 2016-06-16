package io.slychat.messenger.core.crypto.tls

import io.slychat.messenger.core.currentTimestamp
import java.net.HttpURLConnection
import java.net.URL
import java.security.cert.CertificateFactory
import java.security.cert.X509CRL
import java.util.*
import java.util.concurrent.TimeUnit

interface CRLFetcher {
   fun get(url: String): X509CRL
}

//TODO read/connection timeout
class JavaHttpCRLFetcher : CRLFetcher {
   private val certFactory = CertificateFactory.getInstance("X.509")

   override fun get(url: String): X509CRL {
      val connection = URL(url).openConnection() as HttpURLConnection

      connection.doInput = true
      connection.requestMethod = "GET"
      connection.useCaches = false

      return connection.inputStream.use {
         certFactory.generateCRL(it)
      } as X509CRL
   }
}

/** Simple caching CRLFetcher. Wraps an existing fetcher and caches the results. Thread-safe. */
class CachingCRLFetcher(
    private val fetcher: CRLFetcher,
    private val expiresInMs: Long = TimeUnit.MINUTES.toMillis(30)
) : CRLFetcher {
   private val cache = HashMap<String, X509CRL>()
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
}

