package io.slychat.messenger.services

import io.slychat.messenger.core.persistence.AccountInfo
import io.slychat.messenger.core.persistence.AccountInfoPersistenceManager
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.ui.successUi
import org.slf4j.LoggerFactory
import rx.Observable
import rx.subjects.BehaviorSubject

class AccountInfoManagerImpl(
    initialAccountInfo: AccountInfo,
    private val accountInfoPersistenceManager: AccountInfoPersistenceManager
) : AccountInfoManager {
    private val log = LoggerFactory.getLogger(javaClass)

    private val updatesSubject = BehaviorSubject.create<AccountInfo>(initialAccountInfo)

    override val accountInfo: Observable<AccountInfo>
        get() = updatesSubject

    override fun update(newAccountInfo: AccountInfo): Promise<Unit, Exception> {
        log.debug("Updating account info to: {}", newAccountInfo)

        return accountInfoPersistenceManager.store(newAccountInfo) successUi {
            log.debug("Account info updated")
            updatesSubject.onNext(newAccountInfo)
        } fail {
            log.error("Unable to update account info: {}", it.message, it)
        }
    }
}