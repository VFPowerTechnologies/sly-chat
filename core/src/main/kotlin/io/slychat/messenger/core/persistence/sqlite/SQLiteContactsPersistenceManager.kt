package io.slychat.messenger.core.persistence.sqlite

import com.almworks.sqlite4java.SQLiteConnection
import com.almworks.sqlite4java.SQLiteConstants
import com.almworks.sqlite4java.SQLiteException
import com.almworks.sqlite4java.SQLiteStatement
import io.slychat.messenger.core.PlatformContact
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.persistence.*
import nl.komponents.kovenant.Promise
import java.util.*

/** A contact is made up of an entry in the contacts table and an associated conv_ table containing their message log. */
class SQLiteContactsPersistenceManager(private val sqlitePersistenceManager: SQLitePersistenceManager) : ContactsPersistenceManager {
    private fun contactInfoFromRow(stmt: SQLiteStatement) =
        ContactInfo(
            UserId(stmt.columnLong(0)),
            stmt.columnString(1),
            stmt.columnString(2),
            stmt.columnInt(3) != 0,
            stmt.columnString(4),
            stmt.columnString(5)
        )

    private fun contactInfoToRow(contactInfo: ContactInfo, stmt: SQLiteStatement) {
        stmt.bind(1, contactInfo.id.long)
        stmt.bind(2, contactInfo.email)
        stmt.bind(3, contactInfo.name)
        stmt.bind(4, contactInfo.isPending.toInt())
        stmt.bind(5, contactInfo.phoneNumber)
        stmt.bind(6, contactInfo.publicKey)
    }

    override fun get(userId: UserId): Promise<ContactInfo?, Exception> = sqlitePersistenceManager.runQuery { connection ->
        connection.prepare("SELECT id, email, name, is_pending, phone_number, public_key FROM contacts WHERE id=?").use { stmt ->
            stmt.bind(1, userId.long)
            if (!stmt.step())
                null
            else
                contactInfoFromRow(stmt)
        }
    }

    override fun getAll(): Promise<List<ContactInfo>, Exception> = sqlitePersistenceManager.runQuery { connection ->
        connection.prepare("SELECT id, email, name, is_pending, phone_number, public_key FROM contacts").use { stmt ->
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

    override fun getAllConversations(): Promise<List<Conversation>, Exception> = sqlitePersistenceManager.runQuery { connection ->
        val sql = """
SELECT
    id, email, name, is_pending, phone_number, public_key,
    unread_count, last_message, last_timestamp
FROM
    contacts
JOIN
    conversation_info
ON
    contacts.id=conversation_info.contact_id
        """

        connection.prepare(sql).use { stmt ->
            stmt.map { stmt ->
                val contact = contactInfoFromRow(stmt)
                val lastTimestamp = if (!stmt.columnNull(8)) stmt.columnLong(8) else null
                val info = ConversationInfo(contact.id, stmt.columnInt(6), stmt.columnString(7), lastTimestamp)
                Conversation(contact, info)
            }
        }
    }

    private fun queryConversationInfo(connection: SQLiteConnection, userId: UserId): ConversationInfo {
        return connection.prepare("SELECT unread_count, last_message, last_timestamp FROM conversation_info WHERE contact_id=?").use { stmt ->
            stmt.bind(1, userId.long)
            if (!stmt.step())
                throw InvalidConversationException(userId)

            val unreadCount = stmt.columnInt(0)
            val lastMessage = stmt.columnString(1)
            val lastTimestamp = if (!stmt.columnNull(2)) stmt.columnLong(2) else null
            ConversationInfo(userId, unreadCount, lastMessage, lastTimestamp)
        }
    }


    override fun getConversationInfo(userId: UserId): Promise<ConversationInfo, Exception> = sqlitePersistenceManager.runQuery { connection ->
        try {
            queryConversationInfo(connection, userId)
        }
        catch (e: SQLiteException) {
            if (isInvalidTableException(e))
                throw InvalidConversationException(userId)
            else
                throw e
        }
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
        connection.prepare("SELECT id, email, name, is_pending, phone_number, public_key FROM contacts WHERE $fieldName LIKE ? ESCAPE '!'").use { stmt ->
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

    //never call when not inside a transition
    private fun removeContactNoTransaction(connection: SQLiteConnection, userId: UserId) {
        connection.prepare("DELETE FROM conversation_info WHERE contact_id=?").use { stmt ->
            stmt.bind(1, userId.long)
            stmt.step()
        }

        connection.prepare("DELETE FROM contacts WHERE id=?").use { stmt ->
            stmt.bind(1, userId.long)

            stmt.step()
            if (connection.changes <= 0)
                throw InvalidContactException(userId)
        }

        ConversationTable.delete(connection, userId)
    }

    //never call when not inside a transition
    //is here for bulk addition within a single transaction when syncing up the contacts list
    private fun addContactNoTransaction(connection: SQLiteConnection, contactInfo: ContactInfo) {
        try {
            connection.prepare("INSERT INTO contacts (id, email, name, is_pending, phone_number, public_key) VALUES (?, ?, ?, ?, ?, ?)").use { stmt ->
                contactInfoToRow(contactInfo, stmt)
                stmt.step()
            }

            connection.prepare("INSERT INTO conversation_info (contact_id, unread_count, last_message) VALUES (?, 0, NULL)").use { stmt ->
                stmt.bind(1, contactInfo.id.long)
                stmt.step()
            }

            ConversationTable.create(connection, contactInfo.id)
        }
        catch (e: SQLiteException) {
            if (e.baseErrorCode == SQLiteConstants.SQLITE_CONSTRAINT)
                throw DuplicateContactException(contactInfo.email)

            throw e
        }
    }

    private fun addContact(connection: SQLiteConnection, contactInfo: ContactInfo) {
        connection.withTransaction { addContactNoTransaction(connection, contactInfo) }
    }

    override fun add(contactInfo: ContactInfo): Promise<Unit, Exception> = sqlitePersistenceManager.runQuery { connection ->
        addContact(connection, contactInfo)
    }

    override fun addAll(contacts: List<ContactInfo>): Promise<Unit, Exception> = sqlitePersistenceManager.runQuery { connection ->
        contacts.forEach { addContact(connection, it) }
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

    override fun remove(contactInfo: ContactInfo): Promise<Unit, Exception> = sqlitePersistenceManager.runQuery { connection ->
        connection.withTransaction { removeContactNoTransaction(connection, contactInfo.id) }
    }

    override fun getDiff(ids: List<UserId>): Promise<ContactListDiff, Exception> = sqlitePersistenceManager.runQuery { connection ->
        val remoteIds = ids.toSet()

        val localIds = connection.prepare("SELECT id FROM contacts").use { stmt ->
            val r = HashSet<UserId>()
            while (stmt.step()) {
                r.add(UserId(stmt.columnLong(0)))
            }
            r
        }

        val removedEmails = HashSet(localIds)
        removedEmails.removeAll(remoteIds)

        val addedEmails = HashSet(remoteIds)
        addedEmails.removeAll(localIds)

        ContactListDiff(addedEmails, removedEmails)
    }

    override fun applyDiff(newContacts: List<ContactInfo>, removedContacts: List<UserId>): Promise<Unit, Exception> = sqlitePersistenceManager.runQuery { connection ->
        connection.withTransaction {
            newContacts.forEach { addContactNoTransaction(connection, it) }
            removedContacts.forEach { removeContactNoTransaction(connection, it) }
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

    override fun getPending(): Promise<List<ContactInfo>, Exception> = sqlitePersistenceManager.runQuery { connection ->
        connection.withPrepared("SELECT id, email, name, is_pending, phone_number, public_key FROM contacts WHERE is_pending=1") { stmt ->
            stmt.map { contactInfoFromRow(stmt) }
        }
    }

    override fun markAccepted(users: Set<UserId>): Promise<Unit, Exception> = sqlitePersistenceManager.runQuery { connection ->
        connection.withTransaction {
            connection.withPrepared("UPDATE contacts SET is_pending=0 WHERE id=?") { stmt ->
                users.forEach {
                    stmt.bind(1, it.long)
                    stmt.step()
                    stmt.reset()
                }
            }
        }
    }

    override fun getUnadded(): Promise<Set<UserId>, Exception> = sqlitePersistenceManager.runQuery { connection ->
        val sql = "SELECT DISTINCT user_id FROM package_queue LEFT OUTER JOIN contacts ON user_id=contacts.id WHERE id IS null"
        connection.withPrepared(sql) { stmt ->
            stmt.mapToSet { UserId(stmt.columnLong(0)) }
        }
    }
}
