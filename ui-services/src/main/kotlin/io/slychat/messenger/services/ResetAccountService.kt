package io.slychat.messenger.services

import io.slychat.messenger.core.http.api.accountreset.RequestResetAccountResponse
import io.slychat.messenger.core.http.api.accountreset.ResetAccountResponse
import nl.komponents.kovenant.Promise


interface ResetAccountService {

    fun resetAccount(username: String): Promise<RequestResetAccountResponse, Exception>

    fun submitSmsResetCode(username: String, code: String): Promise<ResetAccountResponse, Exception>

    fun submitEmailResetCode(username: String, code: String): Promise<ResetAccountResponse, Exception>

}