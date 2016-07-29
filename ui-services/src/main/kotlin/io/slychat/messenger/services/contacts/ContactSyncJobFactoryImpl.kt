package io.slychat.messenger.services.contacts

import io.slychat.messenger.core.http.api.contacts.ContactAsyncClient
import io.slychat.messenger.core.http.api.contacts.AddressBookAsyncClient
import io.slychat.messenger.core.persistence.AccountInfoPersistenceManager
import io.slychat.messenger.core.persistence.ContactsPersistenceManager
import io.slychat.messenger.services.PlatformContacts
import io.slychat.messenger.services.UserData
import io.slychat.messenger.services.auth.AuthTokenManager

class ContactSyncJobFactoryImpl(
    private val authTokenManager: AuthTokenManager,
    private val contactClient: ContactAsyncClient,
    private val addressBookClient: AddressBookAsyncClient,
    private val contactsPersistenceManager: ContactsPersistenceManager,
    private val userLoginData: UserData,
    private val accountInfoPersistenceManager: AccountInfoPersistenceManager,
    private val platformContacts: PlatformContacts
) : ContactSyncJobFactory {
    override fun create(): ContactSyncJob {
        return ContactSyncJobImpl(
            authTokenManager,
            contactClient,
            addressBookClient,
            contactsPersistenceManager,
            userLoginData,
            accountInfoPersistenceManager,
            platformContacts
        )
    }
}