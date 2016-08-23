package io.slychat.messenger.core.persistence.sqlite

import com.almworks.sqlite4java.SQLiteStatement
import io.slychat.messenger.core.persistence.SignalSessionPersistenceManager
import nl.komponents.kovenant.Promise
import org.whispersystems.libsignal.SignalProtocolAddress
import org.whispersystems.libsignal.state.SessionRecord

class SQLiteSignalSessionPersistenceManager(
    private val sqlitePersistenceManager: SQLitePersistenceManager
) : SignalSessionPersistenceManager {
    private fun bindSignalAddress(stmt: SQLiteStatement, address: SignalProtocolAddress, colN: Int = 1) {
        stmt.bind(colN, address.name.toLong())
        stmt.bind(colN+1, address.deviceId)
    }

    override fun containsSession(address: SignalProtocolAddress): Promise<Boolean, Exception> = sqlitePersistenceManager.runQuery { connection ->
        connection.prepare("SELECT 1 FROM signal_sessions WHERE contact_id=? AND device_id=?").use { stmt ->
            bindSignalAddress(stmt, address)
            stmt.step()
        }
    }

    override fun deleteSession(address: SignalProtocolAddress): Promise<Unit, Exception> = sqlitePersistenceManager.runQuery { connection ->
        connection.prepare("DELETE FROM signal_sessions WHERE contact_id=? AND device_id=?").use { stmt ->
            bindSignalAddress(stmt, address)
            stmt.step()
        }

        Unit
    }

    override fun getSubDeviceSessions(name: String): Promise<List<Int>, Exception> = sqlitePersistenceManager.runQuery { connection ->
        connection.withPrepared("SELECT device_id FROM signal_sessions WHERE contact_id=?") { stmt ->
            stmt.bind(1, name)

            stmt.map {
                stmt.columnInt(0)
            }
        }
    }

    override fun deleteAllSessions(name: String): Promise<Unit, Exception> = sqlitePersistenceManager.runQuery { connection ->
        connection.exec("DELETE FROM signal_sessions")

        Unit
    }

    //TODO version upgrade
    override fun loadSession(address: SignalProtocolAddress): Promise<SessionRecord?, Exception> = sqlitePersistenceManager.runQuery { connection ->
        connection.prepare("SELECT session FROM signal_sessions WHERE contact_id=? AND device_id=?").use { stmt ->
            bindSignalAddress(stmt, address)

            if (!stmt.step())
                SessionRecord()
            else {
                val serialized = stmt.columnBlob(0)
                SessionRecord(serialized)
            }
        }
    }

    override fun storeSession(address: SignalProtocolAddress, record: SessionRecord): Promise<Unit, Exception> = sqlitePersistenceManager.runQuery { connection ->
        connection.prepare("INSERT OR REPLACE INTO signal_sessions (contact_id, device_id, session) VALUES (?, ?, ?)").use { stmt ->
            bindSignalAddress(stmt, address)
            stmt.bind(3, record.serialize())
            stmt.step()
        }

        Unit
    }
}