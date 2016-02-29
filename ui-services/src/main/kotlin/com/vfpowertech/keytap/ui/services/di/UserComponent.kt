package com.vfpowertech.keytap.ui.services.di

import com.vfpowertech.keytap.core.persistence.ContactsPersistenceManager
import com.vfpowertech.keytap.core.persistence.ConversationPersistenceManager
import com.vfpowertech.keytap.core.persistence.sqlite.SQLitePersistenceManager
import com.vfpowertech.keytap.core.relay.RelayClient
import com.vfpowertech.keytap.ui.services.RelayClientManager
import com.vfpowertech.keytap.ui.services.UserLoginData
import dagger.Subcomponent

/** Scoped to a user's login session. */
@UserScope
@Subcomponent(modules = arrayOf(UserModule::class, PersistenceUserModule::class))
interface UserComponent {
    val sqlitePersistenceManager: SQLitePersistenceManager

    val contactsPersistenceManager: ContactsPersistenceManager

    val conversationPersistenceManager: ConversationPersistenceManager

    val userLoginData: UserLoginData

    fun createRelayClient(): RelayClient

    val relayClientManager: RelayClientManager
}

