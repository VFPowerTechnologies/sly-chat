package com.vfpowertech.keytap.core.http.api.prekeys

import com.fasterxml.jackson.databind.ObjectMapper
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import com.vfpowertech.keytap.core.crypto.generateLastResortPreKey
import com.vfpowertech.keytap.core.crypto.generateNewKeyVault
import com.vfpowertech.keytap.core.crypto.generatePrekeys
import com.vfpowertech.keytap.core.http.HttpClient
import com.vfpowertech.keytap.core.http.HttpResponse
import com.vfpowertech.keytap.core.http.api.ApiResult
import com.vfpowertech.keytap.core.http.api.UnauthorizedException
import org.junit.Ignore
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PreKeyStorageClientTest {
    val objectMapper = ObjectMapper()
    val keyVaultPassword = "test"
    val keyVault = generateNewKeyVault(keyVaultPassword)
    val defaultRegistrationId = 12345
    val generatedPreKeys = generatePrekeys(keyVault.identityKeyPair, 1, 1, 10)
    val lastResortPreKey = generateLastResortPreKey()
    val authToken = "000"

    @Test
    fun `store should return a successful PreKeyStorageResponse when receiving a 200 response`() {
        val request = preKeyStorageRequestFromGeneratedPreKeys(authToken, defaultRegistrationId, keyVault, generatedPreKeys, lastResortPreKey)
        val response = PreKeyStoreResponse(null)
        val apiResult = ApiResult(null, response)
        val httpResponse = HttpResponse(200, HashMap(), objectMapper.writeValueAsString(apiResult))
        val httpClient = mock<HttpClient>()

        whenever(httpClient.postJSON(any(), any())).thenReturn(httpResponse)
        val client = PreKeyStorageClient("localhost", httpClient)

        val got = client.store(request)

        assertEquals(response, got)
    }

    @Ignore("TODO")
    @Test
    fun `store should throw ??? on 400 error`() {}

    @Test
    fun `store should throw UnauthorizedException when receiving a 401 response`() {
        val request = preKeyStorageRequestFromGeneratedPreKeys(authToken, defaultRegistrationId, keyVault, generatedPreKeys, lastResortPreKey)
        val httpResponse = HttpResponse(401, HashMap(), "")
        val httpClient = mock<HttpClient>()

        whenever(httpClient.postJSON(any(), any())).thenReturn(httpResponse)

        val client = PreKeyStorageClient("localhost", httpClient)

        assertFailsWith(UnauthorizedException::class) { client.store(request) }
    }
}