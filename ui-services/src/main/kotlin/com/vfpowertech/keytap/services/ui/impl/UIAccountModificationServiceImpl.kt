package com.vfpowertech.keytap.services.ui.impl

import com.vfpowertech.keytap.core.UserId
import com.vfpowertech.keytap.core.http.api.accountUpdate.*
import com.vfpowertech.keytap.core.persistence.AccountInfo
import com.vfpowertech.keytap.services.KeyTapApplication
import com.vfpowertech.keytap.services.di.UserComponent
import com.vfpowertech.keytap.services.ui.UIAccountModificationService
import com.vfpowertech.keytap.services.ui.UIAccountUpdateResult
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.bind
import nl.komponents.kovenant.functional.map

class UIAccountModificationServiceImpl(
    private val app: KeyTapApplication,
    serverUrl: String
) : UIAccountModificationService {
    private val accountUpdateClient = AccountUpdateAsyncClient(serverUrl)

    private fun getUserComponentOrThrow(): UserComponent {
        return app.userComponent ?: throw IllegalStateException("Not logged in")
    }

    override fun updateName(name: String): Promise<UIAccountUpdateResult, Exception> {
        val userComponent = getUserComponentOrThrow()

        return userComponent.authTokenManager.bind { authToken ->
            userComponent.accountInfoPersistenceManager.retrieve() bind { oldAccountInfo ->
                if (oldAccountInfo == null)
                    throw RuntimeException("Missing account info")

                accountUpdateClient.updateName(UpdateNameRequest(authToken.string, name)) bind { response ->
                    if (response.isSuccess === true && response.accountInfo !== null) {
                        val newAccountInfo = AccountInfo(
                            UserId(response.accountInfo.id),
                            response.accountInfo.name,
                            response.accountInfo.username,
                            response.accountInfo.phoneNumber,
                            oldAccountInfo.deviceId
                        )

                        userComponent.accountInfoPersistenceManager.store(newAccountInfo) map {
                            UIAccountUpdateResult(newAccountInfo, response.isSuccess, response.errorMessage)
                        }
                    } else {
                        Promise.ofSuccess(UIAccountUpdateResult(null, response.isSuccess, response.errorMessage))
                    }
                }
            }
        }
    }

    override fun requestPhoneUpdate(phoneNumber: String): Promise<UIAccountUpdateResult, Exception> {
        val userComponent = getUserComponentOrThrow()

        return userComponent.authTokenManager.bind { authToken ->
            accountUpdateClient.requestPhoneUpdate(RequestPhoneUpdateRequest(authToken.string, phoneNumber)) map { response ->
                UIAccountUpdateResult(null, response.isSuccess, response.errorMessage)
            }
        }
    }

    override fun confirmPhoneNumber(smsCode: String): Promise<UIAccountUpdateResult, Exception> {
        val userComponent = getUserComponentOrThrow()

        val accountInfoPersistenceManager = userComponent.accountInfoPersistenceManager

        return userComponent.authTokenManager.bind { authToken ->
            accountUpdateClient.confirmPhoneNumber(ConfirmPhoneNumberRequest(authToken.string, smsCode)) bind { response ->
                if (response.isSuccess === true && response.accountInfo !== null) {
                    //FIXME
                    val newAccountInfo = AccountInfo(
                        UserId(response.accountInfo.id),
                        response.accountInfo.name,
                        response.accountInfo.username,
                        response.accountInfo.phoneNumber,
                        0
                    )

                    accountInfoPersistenceManager.store(newAccountInfo) map {
                        UIAccountUpdateResult(newAccountInfo, response.isSuccess, response.errorMessage)
                    }
                } else {
                    Promise.ofSuccess(UIAccountUpdateResult(null, response.isSuccess, response.errorMessage))
                }
            }
        }
    }

    override fun updateEmail(email: String): Promise<UIAccountUpdateResult, Exception> {
        val userComponent = getUserComponentOrThrow()

        val accountInfoPersistenceManager = userComponent.accountInfoPersistenceManager

        return userComponent.authTokenManager.bind { authToken ->
            accountInfoPersistenceManager.retrieve() bind { oldAccountInfo ->
                if (oldAccountInfo == null)
                    throw RuntimeException("Missing account info")

                accountUpdateClient.updateEmail(UpdateEmailRequest(authToken.string, email)) bind { response ->
                    if (response.isSuccess === true && response.accountInfo !== null) {
                        val newAccountInfo = AccountInfo(
                            UserId(response.accountInfo.id),
                            response.accountInfo.name,
                            response.accountInfo.username,
                            response.accountInfo.phoneNumber,
                            oldAccountInfo.deviceId
                        )

                        accountInfoPersistenceManager.store(newAccountInfo) map {
                            UIAccountUpdateResult(newAccountInfo, response.isSuccess, response.errorMessage)
                        }
                    } else {
                        Promise.ofSuccess(UIAccountUpdateResult(null, response.isSuccess, response.errorMessage))
                    }
                }
            }
        }
    }
}