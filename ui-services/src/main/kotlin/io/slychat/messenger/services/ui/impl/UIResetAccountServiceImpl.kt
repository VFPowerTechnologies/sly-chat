package io.slychat.messenger.services.ui.impl

import io.slychat.messenger.services.ResetAccountService
import io.slychat.messenger.services.ui.UIRequestResetAccountResult
import io.slychat.messenger.services.ui.UIResetAccountService
import io.slychat.messenger.services.ui.UIResetAccountResult
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.map


class UIResetAccountServiceImpl(
    private val resetAccountService: ResetAccountService
) : UIResetAccountService {

    override fun resetAccount(username: String): Promise<UIRequestResetAccountResult, Exception> {
        return resetAccountService.resetAccount(username) map {
            UIRequestResetAccountResult(it.isSuccess, it.emailIsReleased, it.phoneNumberIsReleased, it.errorMessage)
        }
    }

    override fun submitEmailConfirmationCode(username: String, code: String): Promise<UIResetAccountResult, Exception> {
        return resetAccountService.submitEmailResetCode(username, code) map {
            UIResetAccountResult(it.isSuccess, it.errorMessage)
        }
    }

    override fun submitPhoneNumberConfirmationCode(username: String, code: String): Promise<UIResetAccountResult, Exception> {
        return resetAccountService.submitSmsResetCode(username, code) map {
            UIResetAccountResult(it.isSuccess, it.errorMessage)
        }
    }

}