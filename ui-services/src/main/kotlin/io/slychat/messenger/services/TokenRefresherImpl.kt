package io.slychat.messenger.services

import io.slychat.messenger.core.SlyAddress
import io.slychat.messenger.core.crypto.hexify
import io.slychat.messenger.core.http.api.authentication.AuthenticationAsyncClient
import io.slychat.messenger.core.http.api.authentication.AuthenticationRequest
import io.slychat.messenger.services.auth.AuthApiResponseException
import io.slychat.messenger.services.auth.AuthTokenRefreshResult
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.bind
import nl.komponents.kovenant.functional.map

class TokenRefresherImpl(
    private val userData: UserData,
    accountInfoManager: AccountInfoManager,
    private val registrationId: Int,
    private val loginClient: AuthenticationAsyncClient
) : TokenRefresher {
    private val address = userData.address
    private lateinit var email: String

    init {
        accountInfoManager.accountInfo.subscribe { email = it.email }
    }

    override fun refreshAuthToken(): Promise<AuthTokenRefreshResult, Exception> {
        val deviceId = address.deviceId

        val remotePasswordHash = userData.keyVault.remotePasswordHash

        return loginClient.getParams(email) map { resp ->
            if (resp.errorMessage != null)
                throw AuthApiResponseException(resp.errorMessage)

            //TODO make sure hash params still match
            resp.params!!.csrfToken
        } bind { csrfToken ->
            val request = AuthenticationRequest(email, remotePasswordHash.hexify(), csrfToken, registrationId, deviceId)
            loginClient.auth(request)
        } map { resp ->
            if (resp.errorMessage != null)
                throw AuthApiResponseException(resp.errorMessage)

            AuthTokenRefreshResult(resp.data!!.authToken)
        }
    }
}