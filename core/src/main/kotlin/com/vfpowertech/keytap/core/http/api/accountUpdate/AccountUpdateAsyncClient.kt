package com.vfpowertech.keytap.core.http.api.accountUpdate

import com.vfpowertech.keytap.core.http.JavaHttpClient
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.task

class AccountUpdateAsyncClient(private val serverUrl: String) {
    private fun newClient() = AccountUpdateClient(serverUrl, JavaHttpClient())

    fun updatePhone(request: UpdatePhoneRequest): Promise<UpdatePhoneResponse, Exception> = task {
         newClient().updatePhone(request)
    }
}