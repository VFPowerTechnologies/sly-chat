package com.vfpowertech.keytap.core.http.api.prekeys

import com.fasterxml.jackson.databind.ObjectMapper
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import com.vfpowertech.keytap.core.http.HttpClient
import com.vfpowertech.keytap.core.http.HttpResponse
import com.vfpowertech.keytap.core.http.api.ApiResult
import com.vfpowertech.keytap.core.http.api.UnauthorizedException
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PreKeyRetrieveClientTest {
    val objectMapper = ObjectMapper()
    val authToken = "000"
    val username = "test-user"

    @Test
    fun `retrieve should return a PreKeyRetrieveResponse when receiving a 200 response`() {
        val identityKey = "aaaa"
        val preKey = "bbbb"
        val signedPreKey = "cccc"

        val request = PreKeyRetrieveRequest(authToken, username)
        val response = PreKeyRetrieveResponse(null, username, SerializedPreKeySet(identityKey, signedPreKey, preKey))
        val apiResult = ApiResult(null, response)
        val httpResponse = HttpResponse(200, HashMap(), objectMapper.writeValueAsString(apiResult))

        val httpClient = mock<HttpClient>()

        whenever(httpClient.get(any())).thenReturn(httpResponse)

        val client = PreKeyRetrieveClient("localhost", httpClient)

        val got = client.retrieve(request)

        assertEquals(response, got)
    }

    @Test
    fun `store should throw UnauthorizedException when receiving a 401 response`() {
        val request = PreKeyRetrieveRequest(authToken, username)
        val httpResponse = HttpResponse(401, HashMap(), "")
        val httpClient = mock<HttpClient>()

        whenever(httpClient.get(any())).thenReturn(httpResponse)

        val client = PreKeyRetrieveClient("localhost", httpClient)

        assertFailsWith(UnauthorizedException::class) { client.retrieve(request) }
    }
}