package com.vfpowertech.keytap.services

import com.vfpowertech.keytap.core.crypto.*
import com.vfpowertech.keytap.core.div
import com.vfpowertech.keytap.core.http.JavaHttpClient
import com.vfpowertech.keytap.core.http.api.authentication.AuthenticationAsyncClient
import com.vfpowertech.keytap.core.http.api.authentication.AuthenticationClient
import com.vfpowertech.keytap.core.http.api.authentication.AuthenticationRequest
import com.vfpowertech.keytap.core.persistence.AccountInfo
import com.vfpowertech.keytap.core.persistence.json.JsonAccountInfoPersistenceManager
import com.vfpowertech.keytap.core.persistence.json.JsonKeyVaultPersistenceManager
import com.vfpowertech.keytap.core.persistence.json.JsonSessionDataPersistenceManager
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.bind
import nl.komponents.kovenant.functional.map
import nl.komponents.kovenant.task
import org.slf4j.LoggerFactory
import java.io.FileNotFoundException

/** API for various remote authentication functionality. */
class AuthenticationService(
    private val serverUrl: String,
    private val userPathsGenerator: UserPathsGenerator
) {
    companion object {
        private sealed class LocalAuthOutcome {
            class Successful(val result: AuthResult) : LocalAuthOutcome()
            class NoLocalData : LocalAuthOutcome()
            class Failure(val deviceId: Int) : LocalAuthOutcome()
        }
    }

    private val log = LoggerFactory.getLogger(javaClass)

    fun refreshAuthToken(accountInfo: AccountInfo, registrationId: Int, remotePasswordHash: ByteArray): Promise<AuthTokenRefreshResult, Exception> {
        val username = accountInfo.email
        val deviceId = accountInfo.deviceId
        //FIXME
        val loginClient = AuthenticationAsyncClient(serverUrl)

        return loginClient.getParams(username) map { resp ->
            if (resp.errorMessage != null)
                throw AuthApiResponseException(resp.errorMessage)

            //TODO make sure hash params still match
            resp.params!!.csrfToken
        } bind { csrfToken ->
            val request = AuthenticationRequest(username, remotePasswordHash.hexify(), csrfToken, registrationId, deviceId)
            loginClient.auth(request)
        } map { resp ->
            if (resp.errorMessage != null)
                throw AuthApiResponseException(resp.errorMessage)

            AuthTokenRefreshResult(resp.data!!.authToken, resp.data!!.keyRegenCount)
        }
    }

    private fun findAccountFor(emailOrPhoneNumber: String): AccountInfo? {
        val accountsDir = userPathsGenerator.accountsDir

        if (!accountsDir.exists())
            return null

        for (accountDir in accountsDir.listFiles()) {
            if (!accountDir.isDirectory)
                continue

            //ignore non-numeric dirs
            try {
                 accountDir.name.toLong()
            }
            catch (e: NumberFormatException) {
                continue
            }

            val accountInfoFile = accountDir / UserPathsGenerator.ACCOUNT_INFO_FILENAME
            val accountInfo = JsonAccountInfoPersistenceManager(accountInfoFile).retrieveSync() ?: continue

            if (emailOrPhoneNumber == accountInfo.phoneNumber ||
                emailOrPhoneNumber == accountInfo.email)
                return accountInfo
        }

        return null
    }

    private fun remoteAuth(emailOrPhoneNumber: String, password: String, registrationId: Int, deviceId: Int): AuthResult {
        val loginClient = AuthenticationClient(serverUrl, JavaHttpClient())

        val paramsResponse = loginClient.getParams(emailOrPhoneNumber)

        if (paramsResponse.errorMessage != null)
            throw AuthApiResponseException(paramsResponse.errorMessage)

        val authParams = paramsResponse.params!!

        val hashParams = HashDeserializers.deserialize(authParams.hashParams)
        val hash = hashPasswordWithParams(password, hashParams)

        //TODO if deviceId == 0, gen prekeys
        val request = AuthenticationRequest(emailOrPhoneNumber, hash.hexify(), authParams.csrfToken, registrationId, deviceId)

        val response = loginClient.auth(request)
        if (response.errorMessage != null)
            throw AuthApiResponseException(response.errorMessage)

        val data = response.data!!
        val keyVault = KeyVault.deserialize(data.keyVault, password)
        return AuthResult(data.authToken, data.keyRegenCount, keyVault, data.accountInfo)
    }

    private fun localAuth(emailOrPhoneNumber: String, password: String): LocalAuthOutcome {
        val accountInfo = findAccountFor(emailOrPhoneNumber) ?: return LocalAuthOutcome.NoLocalData()

        val paths = userPathsGenerator.getPaths(accountInfo.id)

        //if this doesn't exist it'll throw and we'll just try remote auth
        val keyVaultPersistenceManager = JsonKeyVaultPersistenceManager(paths.keyVaultPath)

        val keyVault = try {
            keyVaultPersistenceManager.retrieveSync(password)
        }
        catch (e: KeyVaultDecryptionFailedException) {
           return LocalAuthOutcome.Failure(accountInfo.deviceId)
        }
        catch (e: FileNotFoundException) {
            return LocalAuthOutcome.NoLocalData()
        }

        //this isn't important; just use a null token in the auth result if this isn't present, and then fetch one remotely by refreshing
        val authToken = try {
            val sessionData = JsonSessionDataPersistenceManager(paths.sessionDataPath, keyVault.localDataEncryptionKey, keyVault.localDataEncryptionParams).retrieveSync()
            sessionData.authToken
        }
        catch (e: FileNotFoundException) {
            null
        }

        return LocalAuthOutcome.Successful(AuthResult(authToken, 0, keyVault, accountInfo))
    }

    private fun authSync(emailOrPhoneNumber: String, password: String, registrationId: Int): AuthResult {
        val localAuthResult = localAuth(emailOrPhoneNumber, password)
        return when (localAuthResult) {
            is LocalAuthOutcome.NoLocalData -> {
                log.debug("No local data found")
                remoteAuth(emailOrPhoneNumber, password, registrationId, 0)
            }

            is LocalAuthOutcome.Successful -> {
                log.debug("Local auth successful")
                localAuthResult.result
            }

            //can occur if user changed their account password but no longer remember the old password
            is LocalAuthOutcome.Failure -> {
                log.debug("Local auth failure")
                remoteAuth(emailOrPhoneNumber, password, registrationId, localAuthResult.deviceId)
            }
        }
    }

    /** Attempts to authentication using a local session first, then falls back to remote authentication. */
    fun auth(emailOrPhoneNumber: String, password: String, registrationId: Int): Promise<AuthResult, Exception> = task {
        authSync(emailOrPhoneNumber, password, registrationId)
    }
}