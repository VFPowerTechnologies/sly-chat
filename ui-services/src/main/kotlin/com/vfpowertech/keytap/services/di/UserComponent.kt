package com.vfpowertech.keytap.services.di

import com.vfpowertech.keytap.core.persistence.*
import com.vfpowertech.keytap.core.persistence.sqlite.SQLitePersistenceManager
import com.vfpowertech.keytap.core.relay.RelayClient
import com.vfpowertech.keytap.services.*
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

    val messengerService: MessengerService

    val notifierService: NotifierService

    val userLoginData: UserLoginData

    val accountInfo: AccountInfo

    val userPaths: UserPaths

    fun createRelayClient(): RelayClient

    val relayClientManager: RelayClientManager
}

