package com.vfpowertech.keytap.services.di

import com.vfpowertech.keytap.core.persistence.ContactsPersistenceManager
import com.vfpowertech.keytap.core.persistence.MessagePersistenceManager
import com.vfpowertech.keytap.core.persistence.sqlite.SQLitePersistenceManager
import com.vfpowertech.keytap.core.relay.RelayClient
import com.vfpowertech.keytap.services.UserLoginData
import com.vfpowertech.keytap.services.RelayClientManager
import dagger.Subcomponent

/** Scoped to a user's login session. */
@UserScope
@Subcomponent(modules = arrayOf(UserModule::class, PersistenceUserModule::class))
interface UserComponent {
    val sqlitePersistenceManager: SQLitePersistenceManager

    val contactsPersistenceManager: ContactsPersistenceManager

    val messagePersistenceManager: MessagePersistenceManager

    val userLoginData: UserLoginData

    fun createRelayClient(): RelayClient

    val relayClientManager: RelayClientManager
}

