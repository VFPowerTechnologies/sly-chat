package io.slychat.messenger.services.contacts

import io.slychat.messenger.core.http.api.contacts.AddressBookAsyncClient
import io.slychat.messenger.core.http.api.contacts.ContactLookupAsyncClient
import io.slychat.messenger.core.persistence.AccountInfo
import io.slychat.messenger.core.persistence.ContactsPersistenceManager
import io.slychat.messenger.core.persistence.GroupPersistenceManager
import io.slychat.messenger.services.PlatformContacts
import io.slychat.messenger.services.UserData
import io.slychat.messenger.services.auth.AuthTokenManager
import io.slychat.messenger.services.getAccountRegionCode
import rx.Observable

class AddressBookSyncJobFactoryImpl(
    private val authTokenManager: AuthTokenManager,
    private val contactLookupClient: ContactLookupAsyncClient,
    private val addressBookClient: AddressBookAsyncClient,
    private val contactsPersistenceManager: ContactsPersistenceManager,
    private val groupPersistenceManager: GroupPersistenceManager,
    private val userLoginData: UserData,
    accountInfo: Observable<AccountInfo>,
    private val platformContacts: PlatformContacts,
    private val promiseTimerFactory: PromiseTimerFactory
) : AddressBookSyncJobFactory {
    private lateinit var accountRegionCode: String

    init {
        accountInfo.subscribe {
            accountRegionCode = getAccountRegionCode(it)
        }
    }

    override fun create(): AddressBookSyncJob {
        return AddressBookSyncJobImpl(
            authTokenManager,
            contactLookupClient,
            addressBookClient,
            contactsPersistenceManager,
            groupPersistenceManager,
            userLoginData,
            accountRegionCode,
            platformContacts,
            promiseTimerFactory
        )
    }
}