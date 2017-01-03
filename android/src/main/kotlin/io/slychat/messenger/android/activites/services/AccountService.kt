package io.slychat.messenger.android.activites.services

import io.slychat.messenger.android.activites.services.impl.AccountUpdateResult
import io.slychat.messenger.core.persistence.AccountInfo
import nl.komponents.kovenant.Promise

interface AccountService {

    fun getAccountInfo(): AccountInfo

    fun checkPhoneNumberAvailability(phone: String): Promise<Boolean, Exception>

    fun updatePhone(phone: String): Promise<AccountUpdateResult, Exception>

    fun verifyPhone(code: String): Promise<AccountUpdateResult, Exception>

    fun updateAccountInfo(accountInfo: AccountInfo): Promise<Unit, Exception>
}