package io.slychat.messenger.services

import io.slychat.messenger.core.http.api.accountreset.*
import nl.komponents.kovenant.Promise


class ResetAccountServiceImpl(
    private val resetAccountClient: ResetAccountAsyncClient
) : ResetAccountService {

    override fun resetAccount(username: String): Promise<RequestResetAccountResponse, Exception> {
        return resetAccountClient.resetAccount(ResetAccountRequest(username))
    }

    override fun submitSmsResetCode(username: String, code: String): Promise<ResetAccountResponse, Exception> {
        return resetAccountClient.submitSmsResetCode(ResetConfirmCodeRequest(username, code))
    }

    override fun submitEmailResetCode(username: String, code: String): Promise<ResetAccountResponse, Exception> {
        return resetAccountClient.submitEmailResetCode(ResetConfirmCodeRequest(username, code))
    }

}