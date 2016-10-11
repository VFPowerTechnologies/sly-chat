package io.slychat.messenger.core.persistence.sqlite

import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.mapToSet
import io.slychat.messenger.core.persistence.sqlite.SQLiteContactsPersistenceManager
import io.slychat.messenger.core.randomContactInfo

interface ContactsPersistenceManagerTestUtils {
    val contactsPersistenceManager: SQLiteContactsPersistenceManager

    fun insertRandomContact(): UserId {
        val contactInfo = randomContactInfo()

        contactsPersistenceManager.add(contactInfo).get()

        return contactInfo.id
    }

    /** Randomly generates and creates proper contact entries for users. Required for foreign key constraints. */
    fun insertRandomContacts(n: Int = 2): Set<UserId> {
        return (1..n).mapToSet { insertRandomContact() }
    }
}