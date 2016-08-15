package io.slychat.messenger.services

import io.slychat.messenger.core.crypto.*
import io.slychat.messenger.core.http.api.authentication.AuthenticationAsyncClient
import io.slychat.messenger.core.http.api.authentication.AuthenticationRequest
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.bind
import nl.komponents.kovenant.functional.map
import nl.komponents.kovenant.task
import org.slf4j.LoggerFactory

/** API for initial authentication functionality. */
class AuthenticationService(
    private val authenticationClient: AuthenticationAsyncClient,
    private val localAccountDirectory: LocalAccountDirectory
) {
    companion object {
        /** Outcome of a local authentication attempt. */
        private sealed class LocalAuthOutcome {
            class Successful(val result: AuthResult) : LocalAuthOutcome()
            class NoLocalData : LocalAuthOutcome()
            class Failure(val deviceId: Int) : LocalAuthOutcome()
        }
    }

    private val log = LoggerFactory.getLogger(javaClass)

    /** Attempts to authenticate to the remote server. */
    private fun remoteAuth(emailOrPhoneNumber: String, password: String, registrationId: Int, deviceId: Int): Promise<AuthResult, Exception> {
        return authenticationClient.getParams(emailOrPhoneNumber) bind { paramsResponse ->
            if (paramsResponse.errorMessage != null)
                throw AuthApiResponseException(paramsResponse.errorMessage)

            val authParams = paramsResponse.params!!

            val hashParams = HashDeserializers.deserialize(authParams.hashParams)
            val hash = hashPasswordWithParams(password, hashParams)

            val request = AuthenticationRequest(emailOrPhoneNumber, hash.hexify(), authParams.csrfToken, registrationId, deviceId)

            authenticationClient.auth(request) map { response ->
                if (response.errorMessage != null)
                    throw AuthApiResponseException(response.errorMessage)

                val data = response.data!!
                val keyVault = KeyVault.deserialize(data.keyVault, password)
                AuthResult(data.authToken, keyVault, data.accountInfo)
            }
        }
    }

    /** Attempts to authenticate using local data. */
    private fun localAuth(emailOrPhoneNumber: String, password: String): LocalAuthOutcome {
        val accountInfo = localAccountDirectory.findAccountFor(emailOrPhoneNumber) ?: return LocalAuthOutcome.NoLocalData()

        //if this doesn't exist it'll throw and we'll just try remote auth
        val keyVaultPersistenceManager = localAccountDirectory.getKeyVaultPersistenceManager(accountInfo.id)

        val keyVault = try {
            keyVaultPersistenceManager.retrieveSync(password)
        }
        catch (e: KeyVaultDecryptionFailedException) {
            return LocalAuthOutcome.Failure(accountInfo.deviceId)
        }

        if (keyVault == null)
            return LocalAuthOutcome.NoLocalData()

        //this isn't important; just use a null token in the auth result if this isn't present, and then fetch one remotely by refreshing
        val sessionDataPersistenceManager = localAccountDirectory.getSessionDataPersistenceManager(
            accountInfo.id,
            keyVault.localDataEncryptionKey,
            keyVault.localDataEncryptionParams
        )
        val sessionData = sessionDataPersistenceManager.retrieveSync()
        val authToken = sessionData?.authToken

        return LocalAuthOutcome.Successful(AuthResult(authToken, keyVault, accountInfo))
    }

    /** Attempts to authentication using a local session first, then falls back to remote authentication. */
    fun auth(emailOrPhoneNumber: String, password: String, registrationId: Int): Promise<AuthResult, Exception> {
        return task { localAuth(emailOrPhoneNumber, password) } bind { localAuthResult ->
            when (localAuthResult) {
                is LocalAuthOutcome.NoLocalData -> {
                    log.debug("No local data found")
                    remoteAuth(emailOrPhoneNumber, password, registrationId, 0)
                }

                is LocalAuthOutcome.Successful -> {
                    log.debug("Local auth successful")
                    Promise.ofSuccess(localAuthResult.result)
                }

                //can occur if user changed their account password but no longer remember the old password
                is LocalAuthOutcome.Failure -> {
                    log.debug("Local auth failure")
                    remoteAuth(emailOrPhoneNumber, password, registrationId, localAuthResult.deviceId)
                }
            }
        }
    }
}