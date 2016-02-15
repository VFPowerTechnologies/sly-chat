package com.vfpowertech.keytap.core.http.api.authentication

import com.fasterxml.jackson.databind.ObjectMapper
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import com.vfpowertech.keytap.core.crypto.getRandomBits
import com.vfpowertech.keytap.core.crypto.hashes.BCryptParams
import com.vfpowertech.keytap.core.http.HttpClient
import com.vfpowertech.keytap.core.http.HttpResponse
import com.vfpowertech.keytap.core.http.api.ApiResult
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals

class AuthenticationClientTest {
    val objectMapper = ObjectMapper()
    val username = "test-user"
    val csrfToken = "csrf"

    @Test
    fun `getParams should return a successful AuthenticationParamsResponse when receiving a 200 response`() {
        val httpClient = mock<HttpClient>()
        val hashParams = BCryptParams(getRandomBits(128), 12).serialize()
        val response = ApiResult(null, AuthenticationParamsResponse(null, AuthenticationParams(csrfToken, hashParams)))
        val httpResponse = HttpResponse(200, HashMap(), objectMapper.writeValueAsString(response))

        whenever(httpClient.get(any(), any())).thenReturn(httpResponse)

        val client = AuthenticationClient("localhost", httpClient)
        val got = client.getParams(username)

        assertEquals(response, got)
    }

    @Test
    fun `auth should return a successful AuthenticationResponse when receiving a 200 response`() {
        val httpClient = mock<HttpClient>()
        val response = ApiResult(null, AuthenticationResponse(null, "auth", null, 0))
        val httpResponse = HttpResponse(200, HashMap(), objectMapper.writeValueAsString(response))

        whenever(httpClient.postJSON(any(), any())).thenReturn(httpResponse)

        val request = AuthenticationRequest(username, "hash", csrfToken)

        val client = AuthenticationClient("localhost", httpClient)
        val got = client.auth(request)

        assertEquals(response, got)
    }
}