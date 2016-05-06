package com.vfpowertech.keytap.services.di

import com.vfpowertech.keytap.core.persistence.*
import com.vfpowertech.keytap.core.persistence.sqlite.SQLitePersistenceManager
import com.vfpowertech.keytap.services.*
import com.vfpowertech.keytap.services.auth.AuthTokenManager
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

    val messengerService: MessengerService

    val notifierService: NotifierService

    val userLoginData: UserLoginData

    val userPaths: UserPaths

    val relayClientManager: RelayClientManager

    val preKeyManager: PreKeyManager

    val offlineMessageManager: OfflineMessageManager

    val contactSyncManager: ContactSyncManager

    val authTokenManager: AuthTokenManager
}
