package io.slychat.messenger.services.ui.impl

import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.http.api.accountupdate.*
import io.slychat.messenger.core.persistence.AccountInfo
import io.slychat.messenger.services.SlyApplication
import io.slychat.messenger.services.di.UserComponent
import io.slychat.messenger.services.ui.UIAccountModificationService
import io.slychat.messenger.services.ui.UIAccountUpdateResult
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.bind
import nl.komponents.kovenant.functional.map
import rx.Subscription

//TODO maybe just pass AccountInfo update events to the ui instead of returning response after change?
class UIAccountModificationServiceImpl(
    private val app: SlyApplication,
    private val accountUpdateClient: AccountUpdateAsyncClient
) : UIAccountModificationService {
    private var currentAccountInfo: AccountInfo? = null

    private var accountInfoSubscription: Subscription? = null

    init {
        app.userSessionAvailable.subscribe { userComponent ->
            if (userComponent != null) {
                accountInfoSubscription = userComponent.accountInfoManager.accountInfo.subscribe {
                    currentAccountInfo = it
                }
            }
            else {
                currentAccountInfo = null
                accountInfoSubscription?.unsubscribe()
                accountInfoSubscription = null
            }
        }
    }

    private fun getUserComponentOrThrow(): UserComponent {
        return app.userComponent ?: throw IllegalStateException("Not logged in")
    }

    private fun updateAccountInfo(userComponent: UserComponent, newAccountInfo: AccountInfo): Promise<Unit, Exception> {
        return userComponent.accountInfoManager.update(newAccountInfo)
    }

    override fun updateName(name: String): Promise<UIAccountUpdateResult, Exception> {
        val oldAccountInfo = currentAccountInfo ?: throw RuntimeException("Missing account info")

        val userComponent = getUserComponentOrThrow()

        return userComponent.authTokenManager.bind { userCredentials ->
            accountUpdateClient.updateName(userCredentials, UpdateNameRequest(name)) bind { response ->
                if (response.isSuccess === true && response.accountInfo !== null) {
                    val newAccountInfo = AccountInfo(
                        UserId(response.accountInfo.id),
                        response.accountInfo.name,
                        response.accountInfo.username,
                        response.accountInfo.phoneNumber,
                        oldAccountInfo.deviceId
                    )

                    updateAccountInfo(userComponent, newAccountInfo) map {
                        UIAccountUpdateResult(newAccountInfo, response.isSuccess, response.errorMessage)
                    }
                } else {
                    Promise.ofSuccess(UIAccountUpdateResult(null, response.isSuccess, response.errorMessage))
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

        val oldAccountInfo = currentAccountInfo ?: throw RuntimeException("Missing account info")

        return userComponent.authTokenManager.bind { userCredentials ->
            accountUpdateClient.confirmPhoneNumber(userCredentials, ConfirmPhoneNumberRequest(smsCode)) bind { response ->
                if (response.isSuccess === true && response.accountInfo !== null) {
                    val newAccountInfo = AccountInfo(
                        UserId(response.accountInfo.id),
                        response.accountInfo.name,
                        response.accountInfo.username,
                        response.accountInfo.phoneNumber,
                        oldAccountInfo.deviceId
                    )

                    updateAccountInfo(userComponent, newAccountInfo) map {
                        UIAccountUpdateResult(newAccountInfo, response.isSuccess, response.errorMessage)
                    }
                } else {
                    Promise.ofSuccess(UIAccountUpdateResult(null, response.isSuccess, response.errorMessage))
                }
            }
        }
    }

    override fun updateEmail(email: String): Promise<UIAccountUpdateResult, Exception> {
        val oldAccountInfo = currentAccountInfo ?: throw RuntimeException("Missing account info")

        val userComponent = getUserComponentOrThrow()

        return userComponent.authTokenManager.bind { userCredentials ->
            accountUpdateClient.updateEmail(userCredentials, UpdateEmailRequest(email)) bind { response ->
                if (response.isSuccess === true && response.accountInfo !== null) {
                    val newAccountInfo = AccountInfo(
                        UserId(response.accountInfo.id),
                        response.accountInfo.name,
                        response.accountInfo.username,
                        response.accountInfo.phoneNumber,
                        oldAccountInfo.deviceId
                    )

                    updateAccountInfo(userComponent, newAccountInfo) map {
                        UIAccountUpdateResult(newAccountInfo, response.isSuccess, response.errorMessage)
                    }
                } else {
                    Promise.ofSuccess(UIAccountUpdateResult(null, response.isSuccess, response.errorMessage))
                }
            }
        }
    }
}