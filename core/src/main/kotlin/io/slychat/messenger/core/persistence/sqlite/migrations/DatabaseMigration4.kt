package io.slychat.messenger.core.persistence.sqlite.migrations

import com.almworks.sqlite4java.SQLiteConnection
import com.almworks.sqlite4java.SQLiteException
import io.slychat.messenger.core.SlyAddress
import io.slychat.messenger.core.persistence.sqlite.DatabaseMigration
import io.slychat.messenger.core.persistence.sqlite.withPrepared
import java.util.*

@Suppress("unused")
class DatabaseMigration4 : DatabaseMigration(4) {
    override fun apply(connection: SQLiteConnection) {
        super.apply(connection)

        val sessions = connection.withPrepared("SELECT address, session FROM signal_sessions_old") { stmt ->
            val sessions = ArrayList<Pair<SlyAddress, ByteArray>>()

            while (stmt.step()) {
                val address = stmt.columnString(0).replace(':', '.')
                val session = stmt.columnBlob(1)

                val slyAddress = SlyAddress.fromString(address) ?: continue

                sessions.add(slyAddress to session)
            }

            sessions
        }

        if (sessions.isNotEmpty()) {
            connection.withPrepared("INSERT INTO signal_sessions (contact_id, device_id, session) VALUES (?, ?, ?)") { stmt ->
                for (p in sessions) {
                    val (slyAddress, session) = p
                    stmt.bind(1, slyAddress.id.long)
                    stmt.bind(2, slyAddress.deviceId)
                    stmt.bind(3, session)

                    try {
                        stmt.step()
                    }
                    catch (e: SQLiteException) {
                        //session for a previously removed contact
                        if (e.message?.contains("FOREIGN KEY constraint failed") ?: false)
                            continue
                        else
                            throw e
                    }
                    finally {
                        //need to reset if we skip over an error, else we get an out of sequence error when we try
                        //and reuse the statement
                        stmt.reset(true)
                    }
                }
            }
        }

        connection.exec("DROP TABLE signal_sessions_old")
    }
}
