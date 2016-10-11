package io.slychat.messenger.services

import io.slychat.messenger.core.persistence.AccountLocalInfo
import nl.komponents.kovenant.Promise

interface AccountLocalInfoManager {
    fun update(accountLocalInfo: AccountLocalInfo): Promise<Unit, Exception>
}