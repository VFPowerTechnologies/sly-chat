package io.slychat.messenger.core.persistence.sqlite

import com.almworks.sqlite4java.SQLiteConnection
import com.almworks.sqlite4java.SQLiteStatement
import io.slychat.messenger.core.PlatformContact
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.crypto.hexify
import io.slychat.messenger.core.crypto.unhexify
import io.slychat.messenger.core.http.api.contacts.md5
import io.slychat.messenger.core.http.api.contacts.md5Fold
import io.slychat.messenger.core.persistence.*
import nl.komponents.kovenant.Promise
import org.slf4j.LoggerFactory
import java.util.*

internal fun contactInfoFromRow(stmt: SQLiteStatement) =
    ContactInfo(
        UserId(stmt.columnLong(0)),
        stmt.columnString(1),
        stmt.columnString(2),
        stmt.columnAllowedMessageLevel(3),
        stmt.columnString(4),
        stmt.columnString(5)
    )

/** A contact is made up of an entry in the contacts table and an associated conv_ table containing their message log. */
class SQLiteContactsPersistenceManager(private val sqlitePersistenceManager: SQLitePersistenceManager) : ContactsPersistenceManager {
    private val log = LoggerFactory.getLogger(javaClass)

    private fun contactInfoToRow(contactInfo: ContactInfo, stmt: SQLiteStatement) {
        stmt.bind(1, contactInfo.id.long)
        stmt.bind(2, contactInfo.email)
        stmt.bind(3, contactInfo.name)
        stmt.bind(4, contactInfo.allowedMessageLevel)
        stmt.bind(5, contactInfo.phoneNumber)
        stmt.bind(6, contactInfo.publicKey)
    }

    private fun queryContactInfo(connection: SQLiteConnection, userId: UserId): ContactInfo? {
        return connection.prepare("SELECT id, email, name, allowed_message_level, phone_number, public_key FROM contacts WHERE id=?").use { stmt ->
            stmt.bind(1, userId.long)
            if (!stmt.step())
                null
            else
                contactInfoFromRow(stmt)
        }
    }

    override fun get(userId: UserId): Promise<ContactInfo?, Exception> = sqlitePersistenceManager.runQuery { connection ->
        queryContactInfo(connection, userId)
    }

    override fun get(ids: Collection<UserId>): Promise<List<ContactInfo>, Exception> = sqlitePersistenceManager.runQuery { connection ->
        connection.withPrepared("SELECT id, email, name, allowed_message_level, phone_number, public_key FROM contacts WHERE id IN (${getPlaceholders(ids.size)})") { stmt ->
            ids.forEachIndexed { i, userId -> stmt.bind(i+1, userId) }
            stmt.map { contactInfoFromRow(stmt) }
        }
    }

    override fun getAll(): Promise<List<ContactInfo>, Exception> = sqlitePersistenceManager.runQuery { connection ->
        connection.prepare("SELECT id, email, name, allowed_message_level, phone_number, public_key FROM contacts").use { stmt ->
            val r = ArrayList<ContactInfo>()
            while (stmt.step()) {
                r.add(contactInfoFromRow(stmt))
            }
            r
        }
    }

    override fun exists(userId: UserId): Promise<Boolean, Exception> = sqlitePersistenceManager.runQuery { connection ->
        connection.prepare("SELECT 1 FROM contacts WHERE id=?").use { stmt ->
            stmt.bind(1, userId.long)
            stmt.step()
        }
    }

    override fun exists(users: Set<UserId>): Promise<Set<UserId>, Exception> = sqlitePersistenceManager.runQuery { connection ->
        val sql = "SELECT id FROM contacts WHERE id IN (${getPlaceholders(users.size)})"
        connection.prepare(sql).use { stmt ->
            users.forEachIndexed { i, userId ->
                stmt.bind(i+1, userId.long)
            }

            stmt.mapToSet { UserId(stmt.columnLong(0)) }
        }
    }

    override fun getBlockList(): Promise<Set<UserId>, Exception> = sqlitePersistenceManager.runQuery { connection ->
        connection.withPrepared("SELECT id FROM contacts WHERE allowed_message_level=?") { stmt ->
            stmt.bind(1, AllowedMessageLevel.BLOCKED)
            stmt.mapToSet { UserId(stmt.columnLong(0)) }
        }
    }

    override fun filterBlocked(users: Collection<UserId>): Promise<Set<UserId>, Exception> = sqlitePersistenceManager.runQuery { connection ->
        val ids = users.map { it.long }.joinToString(",")
        val blocked = connection.withPrepared("SELECT id FROM contacts WHERE allowed_message_level = ? AND id IN ($ids)") { stmt ->
            stmt.bind(1, AllowedMessageLevel.BLOCKED)
            stmt.mapToSet { UserId(stmt.columnLong(0)) }
        }

        val filtered = HashSet(users)
        filtered.removeAll(blocked)
        filtered
    }

    private fun removeConversationData(connection: SQLiteConnection, userId: UserId) {
        val id = ConversationId.User(userId)
        ConversationTable.delete(connection, id)
        deleteConversationInfo(connection, id)
        deleteExpiringMessagesForConversation(connection, id)
    }

    override fun block(userId: UserId): Promise<Unit, Exception> = sqlitePersistenceManager.runQuery { connection ->
        updateContactMessageLevel(connection, userId, AllowedMessageLevel.BLOCKED)
        Unit
    }

    override fun unblock(userId: UserId): Promise<Unit, Exception> = sqlitePersistenceManager.runQuery { connection ->
        updateContactMessageLevel(connection, userId, AllowedMessageLevel.GROUP_ONLY)
        Unit
    }

    override fun allowAll(userId: UserId): Promise<Unit, Exception> = sqlitePersistenceManager.runQuery { connection ->
        updateContactMessageLevel(connection, userId, AllowedMessageLevel.ALL)
        Unit
    }

    private fun searchByLikeField(connection: SQLiteConnection, fieldName: String, searchValue: String): List<ContactInfo> =
        connection.prepare("SELECT id, email, name, allowed_message_level, phone_number, public_key FROM contacts WHERE $fieldName LIKE ? ESCAPE '!'").use { stmt ->
            val escaped = escapeLikeString(searchValue, '!')
            stmt.bind(1, "%$escaped%")
            val r = ArrayList<ContactInfo>()
            while (stmt.step()) {
                r.add(contactInfoFromRow(stmt))
            }
            r
        }

    override fun searchByEmail(email: String): Promise<List<ContactInfo>, Exception> = sqlitePersistenceManager.runQuery {
        searchByLikeField(it, "email", email)
    }

    override fun searchByPhoneNumber(phoneNumber: String): Promise<List<ContactInfo>, Exception> = sqlitePersistenceManager.runQuery {
        searchByLikeField(it, "phone_number", phoneNumber)
    }

    override fun searchByName(name: String): Promise<List<ContactInfo>, Exception> = sqlitePersistenceManager.runQuery {
        searchByLikeField(it, "name", name)
    }

    private fun deleteConversationInfo(connection: SQLiteConnection, conversationId: ConversationId) {
        connection.withPrepared("DELETE FROM conversation_info WHERE conversation_id=?") { stmt ->
            stmt.bind(1, conversationId)
            stmt.step()
        }
    }

    private fun addConversationData(connection: SQLiteConnection, userId: UserId) {
        val id = ConversationId.User(userId)
        ConversationTable.create(connection, id)
        insertOrReplaceNewConversationInfo(connection, id)
    }

    private fun addContactNoTransaction(connection: SQLiteConnection, contactInfo: ContactInfo): Boolean {
        val userId = contactInfo.id
        val currentInfo = queryContactInfo(connection, userId)

        val newMessageLevel = contactInfo.allowedMessageLevel

        return if (currentInfo == null) {
            connection.prepare("INSERT INTO contacts (id, email, name, allowed_message_level, phone_number, public_key) VALUES (?, ?, ?, ?, ?, ?)").use { stmt ->
                contactInfoToRow(contactInfo, stmt)
                stmt.step()
            }

            if (newMessageLevel == AllowedMessageLevel.ALL)
                addConversationData(connection, userId)

            true
        }
        else {
            val currentMessageLevel = currentInfo.allowedMessageLevel

            return if (currentMessageLevel != newMessageLevel && newMessageLevel > currentMessageLevel) {
                if (newMessageLevel == AllowedMessageLevel.ALL)
                    addConversationData(connection, userId)

                updateMessageLevel(connection, userId, newMessageLevel)
            }
            else
                false
        }
    }

    override fun add(contactInfo: ContactInfo): Promise<Boolean, Exception> = sqlitePersistenceManager.runQuery { connection ->
        connection.withTransaction {
            val added = addContactNoTransaction(connection, contactInfo)
            if (added) {
                val remoteUpdates = listOf(AddressBookUpdate.Contact(contactInfo.id, contactInfo.allowedMessageLevel))
                addRemoteUpdateNoTransaction(connection, remoteUpdates)
            }

            added
        }
    }

    override fun addSelf(selfInfo: ContactInfo): Promise<Unit, Exception> = sqlitePersistenceManager.runQuery { connection ->
        addContactNoTransaction(connection, selfInfo)
        
        Unit
    }

    private fun createRemoteUpdates(connection: SQLiteConnection, contactInfo: Collection<ContactInfo>) {
        val remoteUpdates = contactInfo.map { AddressBookUpdate.Contact(it.id, it.allowedMessageLevel) }
        addRemoteUpdateNoTransaction(connection, remoteUpdates)
    }

    override fun add(contacts: Collection<ContactInfo>): Promise<Set<ContactInfo>, Exception> {
        return if (contacts.isNotEmpty())
            sqlitePersistenceManager.runQuery { connection ->
                val newContacts = HashSet<ContactInfo>()

                connection.withTransaction {
                    contacts.forEach {
                        if (addContactNoTransaction(connection, it))
                            newContacts.add(it)
                    }

                    createRemoteUpdates(connection, newContacts)
                }

                newContacts
            }
        else
            Promise.ofSuccess(emptySet())
    }

    override fun update(contactInfo: ContactInfo): Promise<Unit, Exception> = sqlitePersistenceManager.runQuery { connection ->
        connection.prepare("UPDATE contacts SET name=?, phone_number=?, public_key=? WHERE email=?").use { stmt ->
            stmt.bind(1, contactInfo.name)
            stmt.bind(2, contactInfo.phoneNumber)
            stmt.bind(3, contactInfo.publicKey)
            stmt.bind(4, contactInfo.email)

            stmt.step()
            if (connection.changes <= 0)
                throw InvalidContactException(contactInfo.id)
        }
    }

    override fun remove(userId: UserId): Promise<Boolean, Exception> = sqlitePersistenceManager.runQuery { connection ->
        connection.withTransaction {
            val wasRemoved = updateContactMessageLevel(connection, userId, AllowedMessageLevel.GROUP_ONLY)
            if (wasRemoved) {
                val remoteUpdates = listOf(AddressBookUpdate.Contact(userId, AllowedMessageLevel.GROUP_ONLY))
                addRemoteUpdateNoTransaction(connection, remoteUpdates)
            }

            wasRemoved
        }
    }

    override fun applyDiff(newContacts: Collection<ContactInfo>, updated: Collection<AddressBookUpdate.Contact>): Promise<Unit, Exception> {
        return if (newContacts.isNotEmpty() || updated.isNotEmpty())
            sqlitePersistenceManager.runQuery { connection ->
                connection.withTransaction {
                    newContacts.forEach { addContactNoTransaction(connection, it) }
                    updated.forEach {
                        updateContactMessageLevel(connection, it.userId, it.allowedMessageLevel)
                    }
                }
            }
        else
            Promise.ofSuccess(Unit)
    }

    override fun findMissing(platformContacts: List<PlatformContact>): Promise<List<PlatformContact>, Exception> {
        return if (platformContacts.isNotEmpty())
            sqlitePersistenceManager.runQuery { connection ->
                val missing = ArrayList<PlatformContact>()

                for (contact in platformContacts) {
                    val emails = contact.emails
                    val phoneNumbers = contact.phoneNumbers

                    val selection = ArrayList<String>()

                    if (emails.isNotEmpty())
                        selection.add("email IN (${getPlaceholders(emails.size)})")

                    if (phoneNumbers.isNotEmpty())
                        selection.add("phone_number IN (${getPlaceholders(phoneNumbers.size)})")

                    if (selection.isEmpty())
                        continue

                    val sql = "SELECT 1 FROM contacts WHERE " + selection.joinToString(" OR ") + " LIMIT 1"

                    connection.prepare(sql).use { stmt ->
                        var i = 1

                        for (email in emails) {
                            stmt.bind(i, email)
                            i += 1
                        }

                        for (phoneNumber in phoneNumbers) {
                            stmt.bind(i, phoneNumber)
                            i += 1
                        }

                        if (!stmt.step())
                            missing.add(contact)
                    }
                }

                missing
            }
        else
            Promise.ofSuccess(emptyList())
    }

    private fun addRemoteUpdateNoTransaction(connection: SQLiteConnection, remoteUpdates: Collection<AddressBookUpdate.Contact>) {
        connection.batchInsert("INSERT OR REPLACE INTO remote_contact_updates (contact_id) VALUES (?)", remoteUpdates) { stmt, item ->
            stmt.bind(1, item.userId.long)
        }
    }

    override fun getRemoteUpdates(): Promise<List<AddressBookUpdate.Contact>, Exception> = sqlitePersistenceManager.runQuery { connection ->
        val sql = """
SELECT
    c.id,
    c.allowed_message_level
FROM
    remote_contact_updates AS r
JOIN
    contacts AS c
ON
    r.contact_id=c.id
"""
        connection.withPrepared(sql) { stmt ->
            stmt.map {
                val userId = UserId(stmt.columnLong(0))
                val type = stmt.columnAllowedMessageLevel(1)
                AddressBookUpdate.Contact(userId, type)
            }
        }
    }

    override fun removeRemoteUpdates(remoteUpdates: Collection<UserId>): Promise<Unit, Exception> {
        return if (remoteUpdates.isNotEmpty())
            sqlitePersistenceManager.runQuery { connection ->
                connection.withTransaction {
                    connection.withPrepared("DELETE FROM remote_contact_updates WHERE contact_id=?") { stmt ->
                        remoteUpdates.forEach { item ->
                            stmt.bind(1, item)
                            stmt.step()
                            stmt.reset()
                        }
                    }
                }
            }
        else
            Promise.ofSuccess(Unit)
    }

    /** Updates message level for an existing contact. Handles deletion of conversation data and creation of remote update. */
    private fun updateContactMessageLevel(connection: SQLiteConnection, userId: UserId, newMessageLevel: AllowedMessageLevel): Boolean {
        val currentInfo = queryContactInfo(connection, userId)
        if (currentInfo == null) {
            log.warn("Attempt to update message level for a non-existent user: {}", userId)
            return false
        }

        if (currentInfo.allowedMessageLevel == newMessageLevel)
            return false

        updateMessageLevel(connection, userId, newMessageLevel)

        if (currentInfo.allowedMessageLevel == AllowedMessageLevel.ALL)
            removeConversationData(connection, userId)
        else if (newMessageLevel == AllowedMessageLevel.ALL)
            addConversationData(connection, userId)

        val remoteUpdates = listOf(AddressBookUpdate.Contact(userId, newMessageLevel))
        addRemoteUpdateNoTransaction(connection, remoteUpdates)

        return true
    }

    private fun updateMessageLevel(connection: SQLiteConnection, user: UserId, newMessageLevel: AllowedMessageLevel): Boolean {
        connection.withPrepared("UPDATE contacts SET allowed_message_level=? WHERE id=?") { stmt ->
            stmt.bind(1, newMessageLevel)
            stmt.bind(2, user.long)
            stmt.step()
        }

        return connection.changes > 0
    }

    private fun calculateAddressBookHash(connection: SQLiteConnection): String {
        val sql = """
SELECT
    data_hash
FROM
    address_book_hashes
ORDER BY
    id_hash
"""
        return connection.withPrepared(sql) { stmt ->
            md5Fold {
                stmt.foreach {
                    it(stmt.columnBlob(0))
                }
            }
        }.hexify()
    }

    override fun addRemoteEntryHashes(remoteEntries: Collection<RemoteAddressBookEntry>): Promise<String, Exception> = sqlitePersistenceManager.runQuery { connection ->
        val sql = """
INSERT OR REPLACE INTO
    address_book_hashes
    (id_hash, data_hash)
VALUES
    (?, ?)
"""
        connection.batchInsertWithinTransaction(sql, remoteEntries) { stmt, entry ->
            stmt.bind(1, entry.hash.unhexify())
            stmt.bind(2, md5(entry.encryptedData))
        }

        calculateAddressBookHash(connection)
    }

    override fun getAddressBookHash(): Promise<String, Exception> = sqlitePersistenceManager.runQuery { connection ->
        calculateAddressBookHash(connection)
    }
}
