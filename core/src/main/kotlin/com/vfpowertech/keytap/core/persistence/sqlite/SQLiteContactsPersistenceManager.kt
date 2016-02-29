package com.vfpowertech.keytap.core.persistence.sqlite

import com.almworks.sqlite4java.SQLiteConnection
import com.almworks.sqlite4java.SQLiteConstants
import com.almworks.sqlite4java.SQLiteException
import com.almworks.sqlite4java.SQLiteStatement
import com.vfpowertech.keytap.core.persistence.ContactInfo
import com.vfpowertech.keytap.core.persistence.ContactsPersistenceManager
import com.vfpowertech.keytap.core.persistence.Conversation
import com.vfpowertech.keytap.core.persistence.DuplicateContactException
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
        throw RuntimeException("")
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

    override fun add(contactInfo: ContactInfo): Promise<Unit, Exception> = sqlitePersistenceManager.runQuery { connection ->
        try {
            connection.withTransaction {
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
        }
        catch (e: SQLiteException) {
            if (e.baseErrorCode == SQLiteConstants.SQLITE_CONSTRAINT)
                throw DuplicateContactException(contactInfo.email)

            throw e
        }
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
        connection.withTransaction {
            connection.prepare("DELETE FROM conversation_info WHERE contact_email=?").use { stmt ->
                stmt.bind(1, contactInfo.email)
                stmt.step()
            }

            connection.prepare("DELETE FROM contacts WHERE email=?").use { stmt ->
                stmt.bind(1, contactInfo.email)

                stmt.step()
                if (connection.changes <= 0)
                    throw InvalidContactException(contactInfo.email)
            }

            ConversationTable.delete(connection, contactInfo.email)
        }
    }
}
