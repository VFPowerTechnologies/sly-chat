package io.slychat.messenger.services.contacts

import io.slychat.messenger.core.http.api.contacts.AddressBookAsyncClient
import io.slychat.messenger.core.http.api.contacts.ContactAsyncClient
import io.slychat.messenger.core.persistence.AccountInfoPersistenceManager
import io.slychat.messenger.core.persistence.ContactsPersistenceManager
import io.slychat.messenger.core.persistence.GroupPersistenceManager
import io.slychat.messenger.services.PlatformContacts
import io.slychat.messenger.services.UserData
import io.slychat.messenger.services.auth.AuthTokenManager

class AddressBookSyncJobFactoryImpl(
    private val authTokenManager: AuthTokenManager,
    private val contactClient: ContactAsyncClient,
    private val addressBookClient: AddressBookAsyncClient,
    private val contactsPersistenceManager: ContactsPersistenceManager,
    private val groupPersistenceManager: GroupPersistenceManager,
    private val userLoginData: UserData,
    private val accountInfoPersistenceManager: AccountInfoPersistenceManager,
    private val platformContacts: PlatformContacts
) : AddressBookSyncJobFactory {
    override fun create(): AddressBookSyncJob {
        return AddressBookSyncJobImpl(
            authTokenManager,
            contactClient,
            addressBookClient,
            contactsPersistenceManager,
            groupPersistenceManager,
            userLoginData,
            accountInfoPersistenceManager,
            platformContacts
        )
    }
}