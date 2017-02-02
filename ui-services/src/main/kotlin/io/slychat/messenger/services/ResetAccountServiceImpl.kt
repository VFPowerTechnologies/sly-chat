package io.slychat.messenger.services

import io.slychat.messenger.core.http.api.accountreset.*
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.map


class ResetAccountServiceImpl(
    private val resetAccountClient: ResetAccountAsyncClient,
    private val localAccountDirectory: LocalAccountDirectory
) : ResetAccountService {

    override fun resetAccount(username: String): Promise<RequestResetAccountResponse, Exception> {
        return resetAccountClient.resetAccount(ResetAccountRequest(username))
    }

    override fun submitSmsResetCode(username: String, code: String): Promise<ResetAccountResponse, Exception> {
        return resetAccountClient.submitSmsResetCode(ResetConfirmCodeRequest(username, code)) map { result ->
            if (result.isSuccess)
                localAccountDirectory.findAccountFor(username)?.apply { localAccountDirectory.deleteAccountData(this.id) }

            result
        }
    }

    override fun submitEmailResetCode(username: String, code: String): Promise<ResetAccountResponse, Exception> {
        return resetAccountClient.submitEmailResetCode(ResetConfirmCodeRequest(username, code))map { result ->
            if (result.isSuccess)
                localAccountDirectory.findAccountFor(username)?.apply { localAccountDirectory.deleteAccountData(this.id) }

            result
        }
    }

}