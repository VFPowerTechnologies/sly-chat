package io.slychat.messenger.core.http.api.accountreset

import io.slychat.messenger.core.http.HttpClientFactory
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.task

class ResetAccountAsyncClient(private val serverUrl: String, private val factory: HttpClientFactory) {
    private fun newClient() = ResetAccountClient(serverUrl, factory.create())

    fun resetAccount(request: ResetAccountRequest): Promise<RequestResetAccountResponse, Exception> = task {
        newClient().resetAccount(request)
    }

    fun submitEmailResetCode(request: ResetConfirmCodeRequest): Promise<ResetAccountResponse, Exception> = task {
        newClient().submitEmailResetCode(request)
    }

    fun submitSmsResetCode(request: ResetConfirmCodeRequest): Promise<ResetAccountResponse, Exception> = task {
        newClient().submitSmsResetCode(request)
    }

}