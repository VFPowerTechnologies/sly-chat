package io.slychat.messenger.core.http.api.authentication

import com.fasterxml.jackson.databind.ObjectMapper
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import io.slychat.messenger.core.AuthToken
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.crypto.defaultRemotePasswordHashParams
import io.slychat.messenger.core.crypto.generateNewKeyVault
import io.slychat.messenger.core.http.HttpClient
import io.slychat.messenger.core.http.HttpResponse
import io.slychat.messenger.core.http.api.ApiResult
import io.slychat.messenger.core.persistence.AccountInfo
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals

class AuthenticationClientTest {
    val objectMapper = ObjectMapper()
    val username = "test-user"
    val csrfToken = "csrf"
    val registrationId = 1
    val deviceId = 1
    val accountInfo = AccountInfo(UserId(1), "name", username, "0", deviceId)

    @Test
    fun `getParams should return a successful AuthenticationParamsResponse when receiving a 200 response`() {
        val httpClient = mock<HttpClient>()
        val hashParams = defaultRemotePasswordHashParams()
        val response = AuthenticationParamsResponse(null, AuthenticationParams(csrfToken, hashParams))
        val apiResult = ApiResult(null, response)
        val httpResponse = HttpResponse(200, HashMap(), objectMapper.writeValueAsString(apiResult))

        whenever(httpClient.get(any(), any())).thenReturn(httpResponse)

        val client = AuthenticationClient("localhost", httpClient)
        val got = client.getParams(username)

        assertEquals(response, got)
    }

    @Test
    fun `auth should return a successful AuthenticationResponse when receiving a 200 response`() {
        val password = "test"
        val serializedKeyVault = generateNewKeyVault(password).serialize()

        val httpClient = mock<HttpClient>()
        val response = AuthenticationResponse(null, AuthenticationData(AuthToken("auth"), serializedKeyVault, accountInfo, emptyList()))
        val apiResult = ApiResult(null, response)
        val httpResponse = HttpResponse(200, HashMap(), objectMapper.writeValueAsString(apiResult))

        whenever(httpClient.postJSON(any(), any(), any())).thenReturn(httpResponse)

        val request = AuthenticationRequest(username, "hash", csrfToken, registrationId, deviceId)

        val client = AuthenticationClient("localhost", httpClient)
        val got = client.auth(request)

        assertEquals(response, got)
    }
}