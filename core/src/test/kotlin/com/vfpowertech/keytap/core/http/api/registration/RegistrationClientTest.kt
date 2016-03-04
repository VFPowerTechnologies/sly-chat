package com.vfpowertech.keytap.core.http.api.registration

import com.fasterxml.jackson.databind.ObjectMapper
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import com.vfpowertech.keytap.core.crypto.generateNewKeyVault
import com.vfpowertech.keytap.core.http.HttpClient
import com.vfpowertech.keytap.core.http.HttpResponse
import com.vfpowertech.keytap.core.http.api.ApiResult
import com.vfpowertech.keytap.core.http.api.InvalidResponseBodyException
import com.vfpowertech.keytap.core.http.api.ServerErrorException
import com.vfpowertech.keytap.core.http.api.UnexpectedResponseException
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RegistrationClientTest {
    val objectMapper = ObjectMapper()
    val keyVaultPassword = "test"
    val keyVault = generateNewKeyVault(keyVaultPassword)
    val registrationInfo = RegistrationInfo("a@a.com", "name", "000-000-0000")
    val request = registrationRequestFromKeyVault(registrationInfo, keyVault)

    @Test
    fun `register should return a successful RegisterResponse when receiving a 200 response`() {
        val httpClient = mock<HttpClient>()
        val registerResponse = RegisterResponse(null, null)
        val apiValue = ApiResult(null, registerResponse)
        val httpResponse = HttpResponse(200, mapOf(), objectMapper.writeValueAsString(apiValue))

        whenever(httpClient.postJSON(any(), any())).thenReturn(httpResponse)

        val client = RegistrationClient("localhost", httpClient)

        val got = client.register(request)

        assertEquals(registerResponse, got)
    }

    @Test
    fun `register should return a failed RegisterResponse when receiving a 400 response`() {
        val httpClient = mock<HttpClient>()
        val registerResponse = RegisterResponse(null, null)
        val apiValue = ApiResult(null, registerResponse)
        val httpResponse = HttpResponse(400, mapOf(), objectMapper.writeValueAsString(apiValue))

        whenever(httpClient.postJSON(any(), any())).thenReturn(httpResponse)

        val client = RegistrationClient("localhost", httpClient)

        val got = client.register(request)

        assertEquals(registerResponse, got)
    }

    @Test
    fun `register should throw ServerError when receiving a 500 response`() {
        val httpClient = mock<HttpClient>()
        val httpResponse = HttpResponse(500, HashMap(), """{"error": {"message": "error message"}}""")

        whenever(httpClient.postJSON(any(), any())).thenReturn(httpResponse)

        val client = RegistrationClient("localhost", httpClient)
        assertFailsWith(ServerErrorException::class) { client.register(request) }
    }

    @Test
    fun `register should throw UnexpectedResponseException when receiving any other response code`() {
        val httpClient = mock<HttpClient>()
        val httpResponse = HttpResponse(100, HashMap(), "")

        whenever(httpClient.postJSON(any(), any())).thenReturn(httpResponse)

        val client = RegistrationClient("localhost", httpClient)
        assertFailsWith(UnexpectedResponseException::class) { client.register(request) }
    }

    @Test
    fun `register should throw InvalidResponseBodyException when receiving an expected response code but with an invalid response body`() {
        val httpClient = mock<HttpClient>()
        val httpResponse = HttpResponse(200, HashMap(), "{\"status\":\"failed\"}")

        whenever(httpClient.postJSON(any(), any())).thenReturn(httpResponse)

        val client = RegistrationClient("localhost", httpClient)
        assertFailsWith(InvalidResponseBodyException::class) { client.register(request) }
    }
}