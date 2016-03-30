package com.vfpowertech.keytap.services.ui.impl

import com.vfpowertech.keytap.core.crypto.HashDeserializers
import com.vfpowertech.keytap.core.crypto.hashPasswordWithParams
import com.vfpowertech.keytap.core.crypto.hexify
import com.vfpowertech.keytap.core.http.api.accountUpdate.AccountUpdateAsyncClient
import com.vfpowertech.keytap.core.http.api.accountUpdate.UpdatePhoneRequest
import com.vfpowertech.keytap.core.http.api.authentication.AuthenticationAsyncClient
import com.vfpowertech.keytap.services.AuthApiResponseException
import com.vfpowertech.keytap.services.ui.UIAccountModificationService
import com.vfpowertech.keytap.services.ui.UIUpdatePhoneInfo
import com.vfpowertech.keytap.services.ui.UIUpdatePhoneResult
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.bind
import nl.komponents.kovenant.functional.map

class UIAccountModificationServiceImpl(
        serverUrl: String
) : UIAccountModificationService {
    private val loginClient = AuthenticationAsyncClient(serverUrl)
    private val accountUpdateClient = AccountUpdateAsyncClient(serverUrl);

    override fun updatePhone(info: UIUpdatePhoneInfo): Promise<UIUpdatePhoneResult, Exception> {
        return loginClient.getParams(info.email) bind { response ->
            if (response.errorMessage != null)
                throw AuthApiResponseException(response.errorMessage)

            val authParams = response.params!!

            val hashParams = HashDeserializers.deserialize(authParams.hashParams)
            val hash = hashPasswordWithParams(info.password, hashParams)

            accountUpdateClient.updatePhone(UpdatePhoneRequest(info.email, hash.hexify(), info.phoneNumber)) map { response ->
                UIUpdatePhoneResult(response.isSuccess, response.errorMessage)
            }
        }
    }
}