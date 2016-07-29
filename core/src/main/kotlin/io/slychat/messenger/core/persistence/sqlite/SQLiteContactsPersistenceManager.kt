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

    private fun allowedMessageLevelToInt(allowedMessageLevel: AllowedMessageLevel): Int = when (allowedMessageLevel) {
        AllowedMessageLevel.BLOCKED -> 0
        AllowedMessageLevel.GROUP_ONLY -> 1
        AllowedMessageLevel.ALL -> 2
    }

    private fun intToAllowedMessageLevel(v: Int): AllowedMessageLevel = when (v) {
        0 -> AllowedMessageLevel.BLOCKED
        1 -> AllowedMessageLevel.GROUP_ONLY
        2 -> AllowedMessageLevel.ALL
        else -> throw IllegalArgumentException("Invalid integer value for AllowedMessageLevel: $v")
    }

    private fun contactInfoFromRow(stmt: SQLiteStatement) =
        ContactInfo(
            UserId(stmt.columnLong(0)),
            stmt.columnString(1),
            stmt.columnString(2),
            intToAllowedMessageLevel(stmt.columnInt(3)),
            stmt.columnString(4),
            stmt.columnString(5)
        )

    private fun contactInfoToRow(contactInfo: ContactInfo, stmt: SQLiteStatement) {
        stmt.bind(1, contactInfo.id.long)
        stmt.bind(2, contactInfo.email)
        stmt.bind(3, contactInfo.name)
        stmt.bind(4, allowedMessageLevelToInt(contactInfo.allowedMessageLevel))
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
        val allowedMessageLevel = allowedMessageLevelToInt(AllowedMessageLevel.BLOCKED)
        connection.withPrepared("SELECT id FROM contacts WHERE allowed_message_level=$allowedMessageLevel") { stmt ->
            stmt.mapToSet { UserId(stmt.columnLong(0)) }
        }
    }

    override fun filterBlocked(users: Collection<UserId>): Promise<Set<UserId>, Exception> = sqlitePersistenceManager.runQuery { connection ->
        val ids = users.map { it.long }.joinToString(",")
        val allowedMessageLevel = allowedMessageLevelToInt(AllowedMessageLevel.BLOCKED)
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
            stmt.bind(1, allowedMessageLevelToInt(AllowedMessageLevel.GROUP_ONLY))
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

        return if (currentInfo == null) {
            connection.prepare("INSERT INTO contacts (id, email, name, allowed_message_level, phone_number, public_key) VALUES (?, ?, ?, ?, ?, ?)").use { stmt ->
                contactInfoToRow(contactInfo, stmt)
                stmt.step()
            }

            if (contactInfo.allowedMessageLevel == AllowedMessageLevel.ALL)
                addConversationData(connection, userId)

            true
        }
        else {
            return if (currentInfo.allowedMessageLevel != contactInfo.allowedMessageLevel) {
                if (contactInfo.allowedMessageLevel == AllowedMessageLevel.ALL)
                    addConversationData(connection, userId)

                updateMessageLevel(connection, userId, contactInfo.allowedMessageLevel)
            }
            else
                false
        }
    }

    override fun add(contactInfo: ContactInfo): Promise<Boolean, Exception> = sqlitePersistenceManager.runQuery { connection ->
        connection.withTransaction {
            val added = addContactNoTransaction(connection, contactInfo)
            if (added) {
                val remoteUpdates = listOf(RemoteContactUpdate(contactInfo.id, contactInfo.allowedMessageLevel))
                addRemoteUpdateNoTransaction(connection, remoteUpdates)
            }

            added
        }
    }

    private fun createRemoteUpdates(connection: SQLiteConnection, contactInfo: Collection<ContactInfo>) {
        val remoteUpdates = contactInfo.map { RemoteContactUpdate(it.id, it.allowedMessageLevel) }
        addRemoteUpdateNoTransaction(connection, remoteUpdates)
    }

    override fun add(contacts: Collection<ContactInfo>): Promise<Set<ContactInfo>, Exception> = sqlitePersistenceManager.runQuery { connection ->
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
                val remoteUpdates = listOf(RemoteContactUpdate(userId, AllowedMessageLevel.GROUP_ONLY))
                addRemoteUpdateNoTransaction(connection, remoteUpdates)
            }

            wasRemoved
        }
    }

    override fun applyDiff(newContacts: Collection<ContactInfo>, updated: Collection<AddressBookUpdate.Contact>): Promise<Unit, Exception> = sqlitePersistenceManager.runQuery { connection ->
        connection.withTransaction {
            newContacts.forEach { addContactNoTransaction(connection, it) }
            createRemoteUpdates(connection, newContacts)
            updated.forEach {
                updateContactMessageLevel(connection, it.userId, it.allowedMessageLevel)
            }
        }
    }

    override fun findMissing(platformContacts: List<PlatformContact>): Promise<List<PlatformContact>, Exception> = sqlitePersistenceManager.runQuery { connection ->
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

    private fun addRemoteUpdateNoTransaction(connection: SQLiteConnection, remoteUpdates: Collection<RemoteContactUpdate>) {
        connection.batchInsert("INSERT OR REPLACE INTO remote_contact_updates (contact_id, allowed_message_level) VALUES (?, ?)", remoteUpdates) { stmt, item ->
            stmt.bind(1, item.userId.long)
            stmt.bind(2, allowedMessageLevelToInt(item.allowedMessageLevel))
        }
    }

    override fun getRemoteUpdates(): Promise<List<AddressBookUpdate.Contact>, Exception> = sqlitePersistenceManager.runQuery { connection ->
        connection.withPrepared("SELECT contact_id, allowed_message_level FROM remote_contact_updates") { stmt ->
            stmt.map {
                val userId = UserId(stmt.columnLong(0))
                val type = intToAllowedMessageLevel(stmt.columnInt(1))
                AddressBookUpdate.Contact(userId, type)
            }
        }
    }

    override fun removeRemoteUpdates(remoteUpdates: Collection<AddressBookUpdate.Contact>): Promise<Unit, Exception> = sqlitePersistenceManager.runQuery { connection ->
        connection.withTransaction {
            connection.withPrepared("DELETE FROM remote_contact_updates WHERE contact_id=?") { stmt ->
                remoteUpdates.forEach { item ->
                    stmt.bind(1, item.userId.long)
                    stmt.step()
                    stmt.reset()
                }
            }
        }
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

        val remoteUpdates = listOf(RemoteContactUpdate(userId, newMessageLevel))
        addRemoteUpdateNoTransaction(connection, remoteUpdates)
    }

    private fun updateMessageLevel(connection: SQLiteConnection, user: UserId, newMessageLevel: AllowedMessageLevel): Boolean {
        connection.withPrepared("UPDATE contacts SET allowed_message_level=? WHERE id=?") { stmt ->
            stmt.bind(1, allowedMessageLevelToInt(newMessageLevel))
            stmt.bind(2, user.long)
            stmt.step()
        }

        return connection.changes > 0
    }
}
