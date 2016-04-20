package com.vfpowertech.keytap.core.http.api.accountUpdate

import com.vfpowertech.keytap.core.http.JavaHttpClient
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.task

class AccountUpdateAsyncClient(private val serverUrl: String) {
    private fun newClient() = AccountUpdateClient(serverUrl, JavaHttpClient())

    fun updateName(request: UpdateNameRequest): Promise<AccountUpdateResponse, Exception> = task {
        newClient().updateName(request)
    }

    fun requestPhoneUpdate(request: RequestPhoneUpdateRequest): Promise<AccountUpdateResponse, Exception> = task {
        newClient().requestPhoneUpdate(request)
    }

    fun confirmPhoneNumber(request: ConfirmPhoneNumberRequest): Promise<AccountUpdateResponse, Exception> = task {
        newClient().confirmPhoneNumber(request)
    }
}