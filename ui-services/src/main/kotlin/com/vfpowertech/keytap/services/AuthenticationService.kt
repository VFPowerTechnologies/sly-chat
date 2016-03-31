package com.vfpowertech.keytap.services

import com.vfpowertech.keytap.core.crypto.HashDeserializers
import com.vfpowertech.keytap.core.crypto.KeyVault
import com.vfpowertech.keytap.core.crypto.hashPasswordWithParams
import com.vfpowertech.keytap.core.crypto.hexify
import com.vfpowertech.keytap.core.http.api.authentication.AuthenticationAsyncClient
import com.vfpowertech.keytap.core.http.api.authentication.AuthenticationRequest
import com.vfpowertech.keytap.core.kovenant.fallbackTo
import com.vfpowertech.keytap.core.persistence.json.JsonAccountInfoPersistenceManager
import com.vfpowertech.keytap.core.persistence.json.JsonKeyVaultPersistenceManager
import com.vfpowertech.keytap.core.persistence.json.JsonSessionDataPersistenceManager
import com.vfpowertech.keytap.services.ui.impl.asyncCheckPath
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.bind
import nl.komponents.kovenant.functional.map
import nl.komponents.kovenant.ui.successUi
import org.slf4j.LoggerFactory

/** API for various remote authentication functionality. */
class AuthenticationService(
    serverUrl: String,
    val userPathsGenerator: UserPathsGenerator
) {
    private val loginClient = AuthenticationAsyncClient(serverUrl)

    private val log = LoggerFactory.getLogger(javaClass)

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

    fun remoteAuth(username: String, password: String): Promise<AuthResult, Exception> {
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

    fun localAuth(emailOrPhoneNumber: String, password: String): Promise<AuthResult, Exception> {
        val paths = userPathsGenerator.getPaths(emailOrPhoneNumber)

        //XXX I need to figure out a better way to do this stuff
        return asyncCheckPath(paths.keyVaultPath) bind {
            val keyVaultPersistenceManager = JsonKeyVaultPersistenceManager(paths.keyVaultPath)

            keyVaultPersistenceManager.retrieve(password) bind { keyVault ->
                asyncCheckPath(paths.sessionDataPath) bind {
                    JsonSessionDataPersistenceManager(it, keyVault.localDataEncryptionKey, keyVault.localDataEncryptionParams).retrieve()
                } bind { sessionData ->
                    JsonAccountInfoPersistenceManager(paths.accountInfoPath).retrieve() map { accountInfo ->
                        if (accountInfo == null)
                            throw RuntimeException("No account-info.json available")

                        log.debug("Local authentication successful")
                        AuthResult(sessionData.authToken, 0, keyVault, accountInfo)
                    }
                }
            }
        }
    }

    /** Attempts to authentication using a local session first, then falls back to remote authentication. */
    fun auth(emailOrPhoneNumber: String, password: String): Promise<AuthResult, Exception> {
        return localAuth(emailOrPhoneNumber, password) fallbackTo { remoteAuth(emailOrPhoneNumber, password) }
    }
}