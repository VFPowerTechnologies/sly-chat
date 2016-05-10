package com.vfpowertech.keytap.core.http.api.accountupdate

import com.vfpowertech.keytap.core.http.JavaHttpClient
import com.vfpowertech.keytap.core.UserCredentials
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.task

class AccountUpdateAsyncClient(private val serverUrl: String) {
    private fun newClient() = AccountUpdateClient(serverUrl, JavaHttpClient())

    fun updateName(userCredentials: UserCredentials, request: UpdateNameRequest): Promise<AccountUpdateResponse, Exception> = task {
        newClient().updateName(userCredentials, request)
    }

    fun requestPhoneUpdate(userCredentials: UserCredentials, request: RequestPhoneUpdateRequest): Promise<AccountUpdateResponse, Exception> = task {
        newClient().requestPhoneUpdate(userCredentials, request)
    }

    fun confirmPhoneNumber(userCredentials: UserCredentials, request: ConfirmPhoneNumberRequest): Promise<AccountUpdateResponse, Exception> = task {
        newClient().confirmPhoneNumber(userCredentials, request)
    }

    fun updateEmail(userCredentials: UserCredentials, request: UpdateEmailRequest): Promise<AccountUpdateResponse, Exception> = task {
        newClient().updateEmail(userCredentials, request)
    }
}