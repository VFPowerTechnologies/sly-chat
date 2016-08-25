package io.slychat.messenger.core.persistence.sqlite

import com.almworks.sqlite4java.SQLiteConnection
import com.almworks.sqlite4java.SQLiteStatement
import io.slychat.messenger.core.PlatformContact
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.persistence.*
import nl.komponents.kovenant.Promise
import org.slf4j.LoggerFactory
import java.util.*

/** A contact is made up of an entry in the contacts table and an associated conv_ table containing their message log. */
class SQLiteContactsPersistenceManager(private val sqlitePersistenceManager: SQLitePersistenceManager) : ContactsPersistenceManager {
    private val log = LoggerFactory.getLogger(javaClass)

    private fun AllowedMessageLevel.toInt(): Int = when (this) {
        AllowedMessageLevel.BLOCKED -> 0
        AllowedMessageLevel.GROUP_ONLY -> 1
        AllowedMessageLevel.ALL -> 2
    }

    private fun Int.toAllowedMessageLevel(): AllowedMessageLevel = when (this) {
        0 -> AllowedMessageLevel.BLOCKED
        1 -> AllowedMessageLevel.GROUP_ONLY
        2 -> AllowedMessageLevel.ALL
        else -> throw IllegalArgumentException("Invalid integer value for AllowedMessageLevel: $this")
    }

    private fun contactInfoFromRow(stmt: SQLiteStatement) =
        ContactInfo(
            UserId(stmt.columnLong(0)),
            stmt.columnString(1),
            stmt.columnString(2),
            stmt.columnInt(3).toAllowedMessageLevel(),
            stmt.columnString(4),
            stmt.columnString(5)
        )

    private fun contactInfoToRow(contactInfo: ContactInfo, stmt: SQLiteStatement) {
        stmt.bind(1, contactInfo.id.long)
        stmt.bind(2, contactInfo.email)
        stmt.bind(3, contactInfo.name)
        stmt.bind(4, contactInfo.allowedMessageLevel.toInt())
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
        val allowedMessageLevel = AllowedMessageLevel.BLOCKED.toInt()
        connection.withPrepared("SELECT id FROM contacts WHERE allowed_message_level=$allowedMessageLevel") { stmt ->
            stmt.mapToSet { UserId(stmt.columnLong(0)) }
        }
    }

    override fun filterBlocked(users: Collection<UserId>): Promise<Set<UserId>, Exception> = sqlitePersistenceManager.runQuery { connection ->
        val ids = users.map { it.long }.joinToString(",")
        val allowedMessageLevel = AllowedMessageLevel.BLOCKED.toInt()
        val blocked = connection.withPrepared("SELECT id FROM contacts WHERE allowed_message_level == $allowedMessageLevel AND id IN ($ids)") { stmt ->
            stmt.mapToSet { UserId(stmt.columnLong(0)) }
        }

        val filtered = HashSet(users)
        filtered.removeAll(blocked)
        filtered
    }

    private fun removeConversationData(connection: SQLiteConnection, userId: UserId) {
        ConversationTable.delete(connection, userId)
        deleteConversationInfo(connection, userId)
    }

    override fun block(userId: UserId): Promise<Unit, Exception> = sqlitePersistenceManager.runQuery { connection ->
        updateContactMessageLevel(connection, userId, AllowedMessageLevel.BLOCKED)
    }

    override fun unblock(userId: UserId): Promise<Unit, Exception> = sqlitePersistenceManager.runQuery { connection ->
        updateContactMessageLevel(connection, userId, AllowedMessageLevel.GROUP_ONLY)
        Unit
    }

    override fun allowAll(userId: UserId): Promise<Unit, Exception> = sqlitePersistenceManager.runQuery { connection ->
        updateContactMessageLevel(connection, userId, AllowedMessageLevel.ALL)
    }

    override fun getAllConversations(): Promise<List<Conversation>, Exception> = sqlitePersistenceManager.runQuery { connection ->
        val sql = """
SELECT
    id, email, name, allowed_message_level, phone_number, public_key,
    unread_count, last_message, last_timestamp
FROM
    contacts
JOIN
    conversation_info
ON
    contacts.id=conversation_info.contact_id
        """

        connection.withPrepared(sql) { stmt ->
            stmt.map { stmt ->
                val contact = contactInfoFromRow(stmt)
                val lastTimestamp = stmt.columnNullableLong(8)
                val info = ConversationInfo(contact.id, stmt.columnInt(6), stmt.columnString(7), lastTimestamp)
                Conversation(contact, info)
            }
        }
    }

    private fun queryConversationInfo(connection: SQLiteConnection, userId: UserId): ConversationInfo? {
        return connection.prepare("SELECT unread_count, last_message, last_timestamp FROM conversation_info WHERE contact_id=?").use { stmt ->
            stmt.bind(1, userId.long)
            if (stmt.step()) {
                val unreadCount = stmt.columnInt(0)
                val lastMessage = stmt.columnString(1)
                val lastTimestamp = if (!stmt.columnNull(2)) stmt.columnLong(2) else null
                ConversationInfo(userId, unreadCount, lastMessage, lastTimestamp)
            }
            else
                null
        }
    }


    override fun getConversationInfo(userId: UserId): Promise<ConversationInfo?, Exception> = sqlitePersistenceManager.runQuery { connection ->
        queryConversationInfo(connection, userId)
    }

    override fun markConversationAsRead(userId: UserId): Promise<Unit, Exception> = sqlitePersistenceManager.runQuery { connection ->
        connection.prepare("UPDATE conversation_info SET unread_count=0 WHERE contact_id=?").use { stmt ->
            stmt.bind(1, userId.long)
            stmt.step()
        }

        if (connection.changes <= 0)
            throw InvalidConversationException(userId)

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

    private fun removeContactNoTransaction(connection: SQLiteConnection, userId: UserId): Boolean {
        connection.prepare("UPDATE contacts set allowed_message_level=? WHERE id=?").use { stmt ->
            stmt.bind(1, AllowedMessageLevel.GROUP_ONLY.toInt())
            stmt.bind(2, userId)

            stmt.step()
        }

        val wasRemoved = connection.changes > 0

        if (wasRemoved) {
            ConversationTable.delete(connection, userId)

            deleteConversationInfo(connection, userId)
        }

        return wasRemoved
    }

    private fun deleteConversationInfo(connection: SQLiteConnection, userId: UserId) {
        connection.withPrepared("DELETE FROM conversation_info WHERE contact_id=?") { stmt ->
            stmt.bind(1, userId)
            stmt.step()
        }
    }

    private fun insertConversationInfo(connection: SQLiteConnection, userId: UserId) {
        connection.withPrepared("INSERT INTO conversation_info (contact_id, unread_count, last_message) VALUES (?, 0, NULL)") { stmt ->
            stmt.bind(1, userId)
            stmt.step()
        }
    }

    private fun addConversationData(connection: SQLiteConnection, userId: UserId) {
        ConversationTable.create(connection, userId)
        insertConversationInfo(connection, userId)
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
            val wasRemoved = removeContactNoTransaction(connection, userId)
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
                val type = stmt.columnInt(1).toAllowedMessageLevel()
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
    private fun updateContactMessageLevel(connection: SQLiteConnection, userId: UserId, newMessageLevel: AllowedMessageLevel) {
        val currentInfo = queryContactInfo(connection, userId)
        if (currentInfo == null) {
            log.warn("Attempt to update message level for a non-existent user: {}", userId)
            return
        }

        if (currentInfo.allowedMessageLevel == newMessageLevel)
            return

        updateMessageLevel(connection, userId, newMessageLevel)

        if (currentInfo.allowedMessageLevel == AllowedMessageLevel.ALL)
            removeConversationData(connection, userId)
        else if (newMessageLevel == AllowedMessageLevel.ALL)
            addConversationData(connection, userId)

        val remoteUpdates = listOf(AddressBookUpdate.Contact(userId, newMessageLevel))
        addRemoteUpdateNoTransaction(connection, remoteUpdates)
    }

    private fun updateMessageLevel(connection: SQLiteConnection, user: UserId, newMessageLevel: AllowedMessageLevel): Boolean {
        connection.withPrepared("UPDATE contacts SET allowed_message_level=? WHERE id=?") { stmt ->
            stmt.bind(1, newMessageLevel.toInt())
            stmt.bind(2, user.long)
            stmt.step()
        }

        return connection.changes > 0
    }

    override fun getAddressBookVersion(): Promise<Int, Exception> = sqlitePersistenceManager.runQuery { connection ->
        connection.withPrepared("SELECT version FROM address_book_version") { stmt ->
            stmt.step()
            stmt.columnInt(0)
        }
    }

    override fun updateAddressBookVersion(version: Int): Promise<Unit, Exception> = sqlitePersistenceManager.runQuery { connection ->
        connection.withPrepared("UPDATE address_book_version SET version=?") { stmt ->
            stmt.bind(1, version)
            stmt.step()
        }

        Unit
    }
}
