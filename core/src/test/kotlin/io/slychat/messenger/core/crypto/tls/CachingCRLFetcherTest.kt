package io.slychat.messenger.core.crypto.tls

import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import org.joda.time.DateTime
import org.junit.Before
import org.junit.Test
import java.security.cert.X509CRL
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CachingCRLFetcherTest {
    fun mockCRL(expiresAt: DateTime = future()): X509CRL {
        val crl = mock<X509CRL>()
        whenever(crl.nextUpdate).thenReturn(Date(expiresAt.millis))

        return crl
    }

    val timeInc: Long = 60 * 1000

    fun future(): DateTime = DateTime.now().plus(timeInc)
    fun past(): DateTime = DateTime.now().minus(timeInc)

    val mockUrl = "url"
    val mockUrl2 = "url2"

    lateinit var underlying: CRLFetcher

    @Before
    fun before() {
        underlying = mock<CRLFetcher>()
    }

    @Test
    fun `it should cache a new received CRL`() {
        val crlFetcher = CachingCRLFetcher(underlying)

        val crl = mockCRL()

        whenever(underlying.get(mockUrl)).thenReturn(crl)

        assertEquals(crl, crlFetcher.get(mockUrl), "Invalid CRL returned")

        assertTrue(crlFetcher.isCached(mockUrl), "Not cached")
    }

    @Test
    fun `it should use the values of the initial cache`() {
        val crl = mockCRL()

        val initial = mapOf(mockUrl to crl)

        val crlFetcher = CachingCRLFetcher(underlying, initialCache = initial)

        assertTrue(crlFetcher.isCached(mockUrl), "Not cached")
    }

    @Test
    fun `it should ignore an expired cached CRL`() {
        val crl = mockCRL(past())

        val expiresInMs = 1000L

        val initial = mapOf(mockUrl to crl)

        val underlying = mock<CRLFetcher>()
        val crlFetcher = CachingCRLFetcher(underlying, expiresInMs, initial)

        val newCRL = mockCRL()

        whenever(underlying.get(mockUrl)).thenReturn(newCRL)

        assertEquals(newCRL, crlFetcher.get(mockUrl), "Cache not updated")
    }

    @Test
    fun `it should cache values for multiple urls separately`() {
        val crl1 = mockCRL(past())
        val crl2 = mockCRL(future())

        whenever(underlying.get(mockUrl)).thenReturn(crl1)
        whenever(underlying.get(mockUrl2)).thenReturn(crl2)

        val crlFetcher = CachingCRLFetcher(underlying)

        crlFetcher.get(mockUrl)
        crlFetcher.get(mockUrl2)

        assertEquals(crl1, crlFetcher.getFromCacheRaw(mockUrl))
        assertEquals(crl2, crlFetcher.getFromCacheRaw(mockUrl2))
    }
}