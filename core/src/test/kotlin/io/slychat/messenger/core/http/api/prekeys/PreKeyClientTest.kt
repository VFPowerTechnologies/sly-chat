package io.slychat.messenger.core.http.api.prekeys

import com.fasterxml.jackson.databind.ObjectMapper
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import io.slychat.messenger.core.*
import io.slychat.messenger.core.crypto.generateLastResortPreKey
import io.slychat.messenger.core.crypto.generateNewKeyVault
import io.slychat.messenger.core.crypto.generatePrekeys
import io.slychat.messenger.core.http.HttpClient
import io.slychat.messenger.core.http.HttpResponse
import io.slychat.messenger.core.http.api.ApiResult
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PreKeyClientTest {
    val objectMapper = ObjectMapper()
    val keyVaultPassword = "test"
    val keyVault = generateNewKeyVault(keyVaultPassword)
    val defaultRegistrationId = 12345
    val generatedPreKeys = generatePrekeys(keyVault.identityKeyPair, 1, 1, 10)
    val lastResortPreKey = generateLastResortPreKey()
    val userId = UserId(1)
    val deviceId = 0
    val registrationId = 0
    val userCredentials = UserCredentials(SlyAddress(UserId(1), 1), AuthToken("000"))

    @Test
    fun `store should return a successful PreKeyStorageResponse when receiving a 200 response`() {
        val request = preKeyStorageRequestFromGeneratedPreKeys(defaultRegistrationId, keyVault, generatedPreKeys, lastResortPreKey)
        val response = PreKeyStoreResponse(null)
        val apiResult = ApiResult(null, response)
        val httpResponse = HttpResponse(200, HashMap(), objectMapper.writeValueAsString(apiResult))
        val httpClient = mock<HttpClient>()

        whenever(httpClient.postJSON(any(), any(), any())).thenReturn(httpResponse)
        val client = PreKeyClient("localhost", httpClient)

        val got = client.store(userCredentials, request)

        assertEquals(response, got)
    }

    @Test
    fun `store should throw UnauthorizedException when receiving a 401 response`() {
        val request = preKeyStorageRequestFromGeneratedPreKeys(defaultRegistrationId, keyVault, generatedPreKeys, lastResortPreKey)
        val httpResponse = HttpResponse(401, HashMap(), "")
        val httpClient = mock<HttpClient>()

        whenever(httpClient.postJSON(any(), any(), any())).thenReturn(httpResponse)

        val client = PreKeyClient("localhost", httpClient)

        assertFailsWith(UnauthorizedException::class) { client.store(userCredentials, request) }
    }

    @Test
    fun `retrieve should return a PreKeyRetrieveResponse when receiving a 200 response`() {
        val publicKey = "pppp"
        val preKey = "bbbb"
        val signedPreKey = "cccc"

        val request = PreKeyRetrievalRequest(userId, listOf())
        val response = PreKeyRetrievalResponse(null, hashMapOf(deviceId to SerializedPreKeySet(registrationId, publicKey, signedPreKey, preKey)))
        val apiResult = ApiResult(null, response)
        val httpResponse = HttpResponse(200, HashMap(), objectMapper.writeValueAsString(apiResult))

        val httpClient = mock<HttpClient>()

        whenever(httpClient.get(any(), any())).thenReturn(httpResponse)

        val client = PreKeyClient("localhost", httpClient)

        val got = client.retrieve(userCredentials, request)

        assertEquals(response, got)
    }

}