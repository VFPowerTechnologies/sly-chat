package io.slychat.messenger.services

import io.slychat.messenger.core.hexify
import io.slychat.messenger.core.http.api.authentication.AuthenticationAsyncClient
import io.slychat.messenger.core.http.api.authentication.AuthenticationRequest
import io.slychat.messenger.core.persistence.AccountInfo
import io.slychat.messenger.services.auth.AuthApiResponseException
import io.slychat.messenger.services.auth.AuthTokenRefreshResult
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.bind
import nl.komponents.kovenant.functional.map
import rx.Observable

class TokenRefresherImpl(
    private val userData: UserData,
    accountInfo: Observable<AccountInfo>,
    private val registrationId: Int,
    private val loginClient: AuthenticationAsyncClient
) : TokenRefresher {
    private val address = userData.address
    private lateinit var email: String

    init {
        accountInfo.subscribe { email = it.email }
    }

    override fun refreshAuthToken(): Promise<AuthTokenRefreshResult, Exception> {
        val deviceId = address.deviceId

        val remotePasswordHash = userData.remotePasswordHash

        return loginClient.getParams(email) map { resp ->
            val errorMessage = resp.errorMessage
            if (errorMessage != null)
                throw AuthApiResponseException(errorMessage)

            //TODO make sure hash params still match
            resp.params!!.csrfToken
        } bind { csrfToken ->
            val request = AuthenticationRequest(email, remotePasswordHash.hexify(), csrfToken, registrationId, deviceId)
            loginClient.auth(request)
        } map { resp ->
            val errorMessage = resp.errorMessage
            if (errorMessage != null)
                throw AuthApiResponseException(errorMessage)

            AuthTokenRefreshResult(resp.authData!!.authToken)
        }
    }
}