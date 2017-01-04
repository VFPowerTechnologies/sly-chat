package io.slychat.messenger.android.activites.services.impl

import android.support.v7.app.AppCompatActivity
import io.slychat.messenger.android.AndroidApp
import io.slychat.messenger.android.activites.services.AccountService
import io.slychat.messenger.core.persistence.AccountInfo
import io.slychat.messenger.services.ui.UIAccountUpdateResult
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.bind
import nl.komponents.kovenant.functional.map

class AccountServiceImpl(val activity: AppCompatActivity): AccountService {
    private val app = AndroidApp.get(activity)

    fun UIAccountUpdateResult.toAccountUpdateResultResult(): AccountUpdateResult = AccountUpdateResult(successful, errorMessage, accountInfo)

    private fun getAccountInfoOrThrow(): AccountInfo {
        val accountInfo = app.accountInfo
        if (accountInfo === null)
            throw Exception()

        return accountInfo
    }

    override fun getAccountInfo(): AccountInfo {
        return getAccountInfoOrThrow()
    }

    override fun checkPhoneNumberAvailability(phone: String): Promise<Boolean, Exception> {
        return app.appComponent.registrationService.checkPhoneNumberAvailability(phone)
    }

    override fun updatePhone(phone: String): Promise<AccountUpdateResult, Exception> {
        return app.appComponent.uiAccountModificationService.requestPhoneUpdate(phone) map { result ->
            result.toAccountUpdateResultResult()
        }
    }

    override fun verifyPhone(code: String): Promise<AccountUpdateResult, Exception> {
        return app.appComponent.uiAccountModificationService.confirmPhoneNumber(code) map { result ->
            result.toAccountUpdateResultResult()
        }
    }

    override fun updateAccountInfo(accountInfo: AccountInfo): Promise<Unit, Exception> {
        return app.getUserComponent().accountInfoManager.update(accountInfo) map {
            app.accountInfo = accountInfo
        }
    }

    override fun updateInfo(name: String?, email: String?): Promise<AccountUpdateResult, Exception> {
        return updateName(name) bind { nameResult ->
            updateEmail(email) map { emailResult ->
                if (nameResult.successful && emailResult.successful) {
                    AccountUpdateResult(true, null, emailResult.accountInfo)
                }
                else if (!nameResult.successful)
                    nameResult
                else
                    emailResult
            }
        }
    }

    private fun updateName(name: String?): Promise<AccountUpdateResult, Exception> {
        if (name == null)
            return Promise.ofSuccess(AccountUpdateResult(true, null, null))
        else
            return app.appComponent.uiAccountModificationService.updateName(name) map { result ->
                val info = result.accountInfo
                if (result.successful && info !== null) {
                    updateAccountInfo(info)
                }

                result.toAccountUpdateResultResult()
            }
    }

    private fun updateEmail(email: String?): Promise<AccountUpdateResult, Exception> {
        if (email == null)
            return Promise.ofSuccess(AccountUpdateResult(true, null, null))
        else
            return app.appComponent.uiAccountModificationService.updateEmail(email) map { result ->
                val info = result.accountInfo
                if (result.successful && info !== null) {
                    updateAccountInfo(info)
                }

                result.toAccountUpdateResultResult()
            }
    }
}

data class AccountUpdateResult(
    val successful: Boolean,
    val errorMessage: String?,
    val accountInfo: AccountInfo?
)