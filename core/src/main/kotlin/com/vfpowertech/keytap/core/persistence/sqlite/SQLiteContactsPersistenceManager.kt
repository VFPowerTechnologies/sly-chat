package com.vfpowertech.keytap.core.persistence.sqlite

import com.almworks.sqlite4java.SQLiteConnection
import com.almworks.sqlite4java.SQLiteConstants
import com.almworks.sqlite4java.SQLiteException
import com.almworks.sqlite4java.SQLiteStatement
import com.vfpowertech.keytap.core.PlatformContact
import com.vfpowertech.keytap.core.persistence.*
import nl.komponents.kovenant.Promise
import java.util.*

/** A contact is made up of an entry in the contacts table and an associated conv_ table containing their message log. */
class SQLiteContactsPersistenceManager(private val sqlitePersistenceManager: SQLitePersistenceManager) : ContactsPersistenceManager {
    private fun contactInfoFromRow(stmt: SQLiteStatement) =
        ContactInfo(
            stmt.columnString(0),
            stmt.columnString(1),
            stmt.columnString(2),
            stmt.columnString(3)
        )

    private fun contactInfoToRow(contactInfo: ContactInfo, stmt: SQLiteStatement) {
        stmt.bind(1, contactInfo.email)
        stmt.bind(2, contactInfo.name)
        stmt.bind(3, contactInfo.phoneNumber)
        stmt.bind(4, contactInfo.publicKey)
    }

    override fun get(email: String): Promise<ContactInfo?, Exception> = sqlitePersistenceManager.runQuery { connection ->
        connection.prepare("SELECT email, name, phone_number, public_key FROM contacts WHERE email=?").use { stmt ->
            stmt.bind(1, email)
            if (!stmt.step())
                null
            else
                contactInfoFromRow(stmt)
        }
    }

    override fun getAll(): Promise<List<ContactInfo>, Exception> = sqlitePersistenceManager.runQuery { connection ->
        connection.prepare("SELECT email, name, phone_number, public_key FROM contacts").use { stmt ->
            val r = ArrayList<ContactInfo>()
            while (stmt.step()) {
                r.add(contactInfoFromRow(stmt))
            }
            r
        }
    }

    override fun getAllConversations(): Promise<List<Conversation>, Exception> = sqlitePersistenceManager.runQuery { connection ->
        val sql = """
SELECT
    email, name, phone_number, public_key,
    unread_count, last_message, last_timestamp
FROM
    contacts
JOIN
    conversation_info
ON
    contacts.email=conversation_info.contact_email
        """

        connection.prepare(sql).use { stmt ->
            stmt.map { stmt ->
                val contact = contactInfoFromRow(stmt)
                val lastTimestamp = if (!stmt.columnNull(6)) stmt.columnLong(6) else null
                val info = ConversationInfo(contact.email, stmt.columnInt(4), stmt.columnString(5), lastTimestamp)
                Conversation(contact, info)
            }
        }
    }

    private fun queryConversationInfo(connection: SQLiteConnection, contact: String): ConversationInfo {
        return connection.prepare("SELECT unread_count, last_message, last_timestamp FROM conversation_info WHERE contact_email=?").use { stmt ->
            stmt.bind(1, contact)
            if (!stmt.step())
                throw InvalidConversationException(contact)

            val unreadCount = stmt.columnInt(0)
            val lastMessage = stmt.columnString(1)
            val lastTimestamp = if (!stmt.columnNull(2)) stmt.columnLong(2) else null
            ConversationInfo(contact, unreadCount, lastMessage, lastTimestamp)
        }
    }


    override fun getConversationInfo(email: String): Promise<ConversationInfo, Exception> = sqlitePersistenceManager.runQuery { connection ->
        try {
            queryConversationInfo(connection, email)
        }
        catch (e: SQLiteException) {
            if (isInvalidTableException(e))
                throw InvalidConversationException(email)
            else
                throw e
        }
    }

    override fun markConversationAsRead(email: String): Promise<Unit, Exception> = sqlitePersistenceManager.runQuery { connection ->
        connection.prepare("UPDATE conversation_info SET unread_count=0 WHERE contact_email=?").use { stmt ->
            stmt.bind(1, email)
            stmt.step()
        }
        if (connection.changes <= 0)
            throw InvalidConversationException(email)

        Unit
    }


    private fun searchByLikeField(connection: SQLiteConnection, fieldName: String, searchValue: String): List<ContactInfo> =
        connection.prepare("SELECT email, name, phone_number, public_key FROM contacts WHERE $fieldName LIKE ? ESCAPE '!'").use { stmt ->
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
    private fun removeContactNoTransaction(connection: SQLiteConnection, email: String) {
        connection.prepare("DELETE FROM conversation_info WHERE contact_email=?").use { stmt ->
            stmt.bind(1, email)
            stmt.step()
        }

        connection.prepare("DELETE FROM contacts WHERE email=?").use { stmt ->
            stmt.bind(1, email)

            stmt.step()
            if (connection.changes <= 0)
                throw InvalidContactException(email)
        }

        ConversationTable.delete(connection, email)
    }

    //never call when not inside a transition
    //is here for bulk addition within a single transaction when syncing up the contacts list
    private fun addContactNoTransaction(connection: SQLiteConnection, contactInfo: ContactInfo) {
        try {
            connection.prepare("INSERT INTO contacts (email, name, phone_number, public_key) VALUES (?, ?, ?, ?)").use { stmt ->
                contactInfoToRow(contactInfo, stmt)
                stmt.step()
            }

            connection.prepare("INSERT INTO conversation_info (contact_email, unread_count, last_message) VALUES (?, 0, NULL)").use { stmt ->
                stmt.bind(1, contactInfo.email)
                stmt.step()
            }

            ConversationTable.create(connection, contactInfo.email)
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
                throw InvalidContactException(contactInfo.email)
        }
    }

    override fun remove(contactInfo: ContactInfo): Promise<Unit, Exception> = sqlitePersistenceManager.runQuery { connection ->
        connection.withTransaction { removeContactNoTransaction(connection, contactInfo.email) }
    }

    override fun getDiff(emails: List<String>): Promise<ContactListDiff, Exception> = sqlitePersistenceManager.runQuery { connection ->
        val remoteEmails = emails.toSet()

        val localEmails = connection.prepare("SELECT email FROM contacts").use { stmt ->
            val r = HashSet<String>()
            while (stmt.step()) {
                r.add(stmt.columnString(0))
            }
            r
        }

        val removedEmails = HashSet(localEmails)
        removedEmails.removeAll(remoteEmails)

        val addedEmails = HashSet(remoteEmails)
        addedEmails.removeAll(localEmails)

        ContactListDiff(addedEmails, removedEmails)
    }

    override fun applyDiff(newContacts: List<ContactInfo>, removedContacts: List<String>): Promise<Unit, Exception> = sqlitePersistenceManager.runQuery { connection ->
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
}
