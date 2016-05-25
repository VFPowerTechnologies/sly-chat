package io.slychat.messenger.services.di

import io.slychat.messenger.core.persistence.*
import io.slychat.messenger.core.persistence.sqlite.SQLitePersistenceManager
import io.slychat.messenger.services.*
import io.slychat.messenger.services.auth.AuthTokenManager
import dagger.Subcomponent

/** Scoped to a user's login session. */
@UserScope
@Subcomponent(modules = arrayOf(UserModule::class, PersistenceUserModule::class))
interface UserComponent {
    val keyVaultPersistenceManager: KeyVaultPersistenceManager

    val sqlitePersistenceManager: SQLitePersistenceManager

    val contactsPersistenceManager: ContactsPersistenceManager

    val messagePersistenceManager: MessagePersistenceManager

    val sessionDataPersistenceManager : SessionDataPersistenceManager

    val accountInfoPersistenceManager: AccountInfoPersistenceManager

    val preKeyPersistenceManager: PreKeyPersistenceManager

    val contactsService: ContactsService

    val messengerService: MessengerService

    val notifierService: NotifierService

    val userLoginData: UserData

    val userPaths: UserPaths

    val relayClientManager: RelayClientManager

    val preKeyManager: PreKeyManager

    val offlineMessageManager: OfflineMessageManager

    val contactSyncManager: ContactSyncManager

    val authTokenManager: AuthTokenManager
}
