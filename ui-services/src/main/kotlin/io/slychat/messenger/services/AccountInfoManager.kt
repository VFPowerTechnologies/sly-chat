package io.slychat.messenger.services

import io.slychat.messenger.core.persistence.AccountInfo
import nl.komponents.kovenant.Promise
import rx.Observable

/** Manages a logged in user's account info. */
interface AccountInfoManager {
    val accountInfo: Observable<AccountInfo>

    //TODO maybe just do this in the background? this normally shouldn't fail anyways, so just send update when called, then write and log errors?
    fun update(newAccountInfo: AccountInfo): Promise<Unit, Exception>
}

