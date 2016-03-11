package com.vfpowertech.keytap.services

import com.vfpowertech.keytap.core.crypto.HashDeserializers
import com.vfpowertech.keytap.core.crypto.KeyVault
import com.vfpowertech.keytap.core.crypto.hashPasswordWithParams
import com.vfpowertech.keytap.core.crypto.hexify
import com.vfpowertech.keytap.core.http.api.authentication.AuthenticationAsyncClient
import com.vfpowertech.keytap.core.http.api.authentication.AuthenticationRequest
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.bind
import nl.komponents.kovenant.functional.map

/** API for various remote authentication functionality. */
class AuthenticationService(serverUrl: String) {
    private val loginClient = AuthenticationAsyncClient(serverUrl)

    fun refreshAuthToken(username: String, remotePasswordHash: ByteArray): Promise<AuthTokenRefreshResult, Exception> {
        return loginClient.getParams(username) map { resp ->
            if (resp.errorMessage != null)
                throw AuthApiResponseException(resp.errorMessage)

            //TODO make sure hash params still match
            resp.params!!.csrfToken
        } bind { csrfToken ->
            val request = AuthenticationRequest(username, remotePasswordHash.hexify(), csrfToken)
            loginClient.auth(request)
        } map { resp ->
            if (resp.errorMessage != null)
                throw AuthApiResponseException(resp.errorMessage)

            AuthTokenRefreshResult(resp.data!!.authToken, resp.data!!.keyRegenCount)
        }
    }

    fun auth(username: String, password: String): Promise<AuthResult, Exception> {
        return loginClient.getParams(username) bind { response ->
            if (response.errorMessage != null)
                throw AuthApiResponseException(response.errorMessage)

            val authParams = response.params!!

            val hashParams = HashDeserializers.deserialize(authParams.hashParams)
            val hash = hashPasswordWithParams(password, hashParams)

            val request = AuthenticationRequest(username, hash.hexify(), authParams.csrfToken)

            loginClient.auth(request) map { response ->
                if (response.errorMessage != null)
                    throw AuthApiResponseException(response.errorMessage)

                val data = response.data!!
                val keyVault = KeyVault.deserialize(data.keyVault, password)
                keyVault.remotePasswordHash = hash
                keyVault.remotePasswordHashParams = hashParams
                AuthResult(data.authToken, data.keyRegenCount, keyVault, data.accountInfo)
            }
        }
    }
}