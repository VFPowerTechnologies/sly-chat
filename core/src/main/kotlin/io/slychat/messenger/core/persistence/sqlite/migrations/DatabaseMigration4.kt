package io.slychat.messenger.core.persistence.sqlite.migrations

import com.almworks.sqlite4java.SQLiteConnection
import io.slychat.messenger.core.SlyAddress
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.persistence.sqlite.DatabaseMigration
import io.slychat.messenger.core.persistence.sqlite.bind
import io.slychat.messenger.core.persistence.sqlite.withPrepared
import java.util.*

@Suppress("unused")
class DatabaseMigration4 : DatabaseMigration(4) {
    private fun isContactPresent(connection: SQLiteConnection, userId: UserId): Boolean {
        return connection.withPrepared("SELECT 1 FROM contacts WHERE id=?") { stmt ->
            stmt.bind(1, userId)
            stmt.step()
        }
    }

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

        //foreigns are disabled during migrations
        val sessionsKeep = sessions.filter { isContactPresent(connection, it.first.id) }

        if (sessionsKeep.isNotEmpty()) {
            connection.withPrepared("INSERT INTO signal_sessions (contact_id, device_id, session) VALUES (?, ?, ?)") { stmt ->
                for (p in sessionsKeep) {
                    val (slyAddress, session) = p
                    stmt.bind(1, slyAddress.id.long)
                    stmt.bind(2, slyAddress.deviceId)
                    stmt.bind(3, session)

                    stmt.step()
                    stmt.reset(true)
                }
            }
        }

        connection.exec("DROP TABLE signal_sessions_old")
    }
}
