package com.vfpowertech.keytap.services.ui.impl

import com.vfpowertech.keytap.core.PlatformInfo
import com.vfpowertech.keytap.core.UserId
import com.vfpowertech.keytap.core.http.api.accountUpdate.*
import com.vfpowertech.keytap.core.persistence.AccountInfo
import com.vfpowertech.keytap.core.persistence.json.JsonAccountInfoPersistenceManager
import com.vfpowertech.keytap.services.KeyTapApplication
import com.vfpowertech.keytap.services.UserPathsGenerator
import com.vfpowertech.keytap.services.ui.UIAccountModificationService
import com.vfpowertech.keytap.services.ui.UIAccountUpdateResult
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.bind
import nl.komponents.kovenant.functional.map

class UIAccountModificationServiceImpl(
    private val app: KeyTapApplication,
    serverUrl: String,
    platformInfo: PlatformInfo
) : UIAccountModificationService {
    private val accountUpdateClient = AccountUpdateAsyncClient(serverUrl)
    private val userPathsGenerator = UserPathsGenerator(platformInfo)

    override fun updateName(name: String): Promise<UIAccountUpdateResult, Exception> {
        val authToken = app.userComponent?.userLoginData?.authToken ?: return Promise.ofFail(RuntimeException("Not logged in"))
        val username = app.userComponent?.userLoginData?.username ?: return Promise.ofFail(RuntimeException("Not logged in"))

        val paths = userPathsGenerator.getPaths(username)

        return accountUpdateClient.updateName(UpdateNameRequest(authToken, name)) bind { response ->
            if(response.isSuccess === true && response.accountInfo !== null) {
                val newAccountInfo = AccountInfo(UserId(response.accountInfo.id), response.accountInfo.name, response.accountInfo.username, response.accountInfo.phoneNumber)

                JsonAccountInfoPersistenceManager(paths.accountInfoPath).store(newAccountInfo) map { result ->
                    UIAccountUpdateResult(newAccountInfo, response.isSuccess, response.errorMessage)
                }
            }
            else {
                Promise.ofSuccess(UIAccountUpdateResult(null, response.isSuccess, response.errorMessage))
            }
        }
    }

    override fun requestPhoneUpdate(phoneNumber: String): Promise<UIAccountUpdateResult, Exception> {
        val authToken = app.userComponent?.userLoginData?.authToken ?: return Promise.ofFail(RuntimeException("Not logged in"))

        return accountUpdateClient.requestPhoneUpdate(RequestPhoneUpdateRequest(authToken, phoneNumber)) map { response ->
                UIAccountUpdateResult(null, response.isSuccess, response.errorMessage)
        }
    }

    override fun confirmPhoneNumber(smsCode: String): Promise<UIAccountUpdateResult, Exception> {
        val authToken = app.userComponent?.userLoginData?.authToken ?: return Promise.ofFail(RuntimeException("Not logged in"))
        val username = app.userComponent?.userLoginData?.username ?: return Promise.ofFail(RuntimeException("Not logged in"))

        val paths = userPathsGenerator.getPaths(username)

        return accountUpdateClient.confirmPhoneNumber(ConfirmPhoneNumberRequest(authToken, smsCode)) bind { response ->
            if(response.isSuccess === true && response.accountInfo !== null) {
                val newAccountInfo = AccountInfo(UserId(response.accountInfo.id), response.accountInfo.name, response.accountInfo.username, response.accountInfo.phoneNumber)

                JsonAccountInfoPersistenceManager(paths.accountInfoPath).store(newAccountInfo) map { result ->
                    UIAccountUpdateResult(newAccountInfo, response.isSuccess, response.errorMessage)
                }
            }
            else {
                Promise.ofSuccess(UIAccountUpdateResult(null, response.isSuccess, response.errorMessage))
            }
        }
    }
}