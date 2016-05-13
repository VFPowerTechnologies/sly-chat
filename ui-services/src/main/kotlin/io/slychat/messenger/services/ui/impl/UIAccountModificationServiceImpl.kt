package io.slychat.messenger.services.ui.impl

import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.http.api.accountupdate.*
import io.slychat.messenger.core.persistence.AccountInfo
import io.slychat.messenger.core.UserCredentials
import io.slychat.messenger.services.SlyApplication
import io.slychat.messenger.services.di.UserComponent
import io.slychat.messenger.services.ui.UIAccountModificationService
import io.slychat.messenger.services.ui.UIAccountUpdateResult
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.bind
import nl.komponents.kovenant.functional.map

class UIAccountModificationServiceImpl(
    private val app: SlyApplication,
    serverUrl: String
) : UIAccountModificationService {
    private val accountUpdateClient = AccountUpdateAsyncClient(serverUrl)

    private fun getUserComponentOrThrow(): UserComponent {
        return app.userComponent ?: throw IllegalStateException("Not logged in")
    }

    override fun updateName(name: String): Promise<UIAccountUpdateResult, Exception> {
        val userComponent = getUserComponentOrThrow()

        return userComponent.authTokenManager.bind { userCredentials ->
            userComponent.accountInfoPersistenceManager.retrieve() bind { oldAccountInfo ->
                if (oldAccountInfo == null)
                    throw RuntimeException("Missing account info")

                accountUpdateClient.updateName(userCredentials, UpdateNameRequest(name)) bind { response ->
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

        return userComponent.authTokenManager.bind { userCredentials ->
            accountUpdateClient.requestPhoneUpdate(userCredentials, RequestPhoneUpdateRequest(phoneNumber)) map { response ->
                UIAccountUpdateResult(null, response.isSuccess, response.errorMessage)
            }
        }
    }

    override fun confirmPhoneNumber(smsCode: String): Promise<UIAccountUpdateResult, Exception> {
        val userComponent = getUserComponentOrThrow()

        val accountInfoPersistenceManager = userComponent.accountInfoPersistenceManager

        return userComponent.authTokenManager.bind { userCredentials ->
            accountUpdateClient.confirmPhoneNumber(userCredentials, ConfirmPhoneNumberRequest(smsCode)) bind { response ->
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

        return userComponent.authTokenManager.bind { userCredentials ->
            accountInfoPersistenceManager.retrieve() bind { oldAccountInfo ->
                if (oldAccountInfo == null)
                    throw RuntimeException("Missing account info")

                accountUpdateClient.updateEmail(userCredentials, UpdateEmailRequest(email)) bind { response ->
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