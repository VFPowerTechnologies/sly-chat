package io.slychat.messenger.services

import io.slychat.messenger.core.persistence.AccountParams
import nl.komponents.kovenant.Promise

interface AccountParamsManager {
    fun update(accountParams: AccountParams): Promise<Unit, Exception>
}