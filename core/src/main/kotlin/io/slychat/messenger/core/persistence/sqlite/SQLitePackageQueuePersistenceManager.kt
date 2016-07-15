package io.slychat.messenger.core.persistence.sqlite

import com.almworks.sqlite4java.SQLiteConnection
import com.almworks.sqlite4java.SQLiteStatement
import io.slychat.messenger.core.SlyAddress
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.persistence.Package
import io.slychat.messenger.core.persistence.PackageId
import io.slychat.messenger.core.persistence.PackageQueuePersistenceManager
import nl.komponents.kovenant.Promise

class SQLitePackageQueuePersistenceManager(
    private val sqlitePersistenceManager: SQLitePersistenceManager
) : PackageQueuePersistenceManager {
    override fun addToQueue(pkg: Package): Promise<Unit, Exception> = addToQueue(listOf(pkg))

    override fun addToQueue(packages: Collection<Package>): Promise<Unit, Exception> = sqlitePersistenceManager.runQuery { connection ->
        val sql = "INSERT INTO package_queue (user_id, device_id, message_id, timestamp, payload) VALUES (?, ?, ?, ?, ?)"
        connection.batchInsertWithinTransaction(sql, packages) { stmt, queuedMessage ->
            stmt.bind(1, queuedMessage.id.address.id.long)
            stmt.bind(2, queuedMessage.id.address.deviceId)
            stmt.bind(3, queuedMessage.id.messageId)
            stmt.bind(4, queuedMessage.timestamp)
            stmt.bind(5, queuedMessage.payload)
        }
    }

    private fun removeFromQueueNoTransaction(connection: SQLiteConnection, userId: UserId, messageIds: Collection<String>) {
        messageIds.forEach { messageId ->
            connection.prepare("DELETE FROM package_queue WHERE user_id=? AND message_id=?").use { stmt ->
                stmt.bind(1, userId.long)
                stmt.bind(2, messageId)
                stmt.step()
            }
        }
    }

    override fun removeFromQueue(packageId: PackageId): Promise<Unit, Exception> = removeFromQueue(packageId.address.id, listOf(packageId.messageId))

    override fun removeFromQueue(packageIds: Collection<PackageId>): Promise<Unit, Exception> = sqlitePersistenceManager.runQuery { connection ->
        //TODO optimize
        connection.withTransaction {
            packageIds.forEach { packageId ->
                removeFromQueueNoTransaction(connection, packageId.address.id, listOf(packageId.messageId))
            }
        }
    }

    override fun removeFromQueue(userId: UserId, messageIds: Collection<String>): Promise<Unit, Exception> = sqlitePersistenceManager.runQuery { connection ->
        connection.withTransaction {
            removeFromQueueNoTransaction(connection, userId, messageIds)
        }
    }

    override fun removeFromQueue(userId: UserId): Promise<Unit, Exception> = sqlitePersistenceManager.runQuery { connection ->
        connection.prepare("DELETE FROM package_queue WHERE user_id=?").use { stmt ->
            stmt.bind(1, userId.long)
            stmt.step()
        }

        Unit
    }

    override fun removeFromQueue(users: Set<UserId>): Promise<Unit, Exception> = sqlitePersistenceManager.runQuery { connection ->
        connection.withTransaction {
            connection.prepare("DELETE FROM package_queue WHERE user_id=?").use { stmt ->
                users.forEach {
                    stmt.bind(1, it.long)
                    stmt.step()
                    stmt.reset(true)
                }
            }
        }
    }

    override fun getQueuedPackages(userId: UserId): Promise<List<Package>, Exception> = sqlitePersistenceManager.runQuery { connection ->
        connection.prepare("SELECT user_id, device_id, message_id, timestamp, payload FROM package_queue WHERE user_id=?").use { stmt ->
            stmt.bind(1, userId.long)

            stmt.map { rowToPackage(stmt) }
        }
    }

    override fun getQueuedPackages(users: Collection<UserId>): Promise<List<Package>, Exception> = sqlitePersistenceManager.runQuery { connection ->
        val sql = "SELECT user_id, device_id, message_id, timestamp, payload FROM package_queue where user_id IN (${getPlaceholders(users.size)})"
        connection.withPrepared(sql) { stmt ->
            users.forEachIndexed { i, userId ->
                stmt.bind(i+1, userId.long)
            }
            stmt.map { rowToPackage(stmt) }
        }
    }

    override fun getQueuedPackages(): Promise<List<Package>, Exception> = sqlitePersistenceManager.runQuery { connection ->
        connection.prepare("SELECT user_id, device_id, message_id, timestamp, payload FROM package_queue").use { stmt ->
            stmt.map { rowToPackage(stmt) }
        }
    }

    private fun rowToPackage(stmt: SQLiteStatement): Package {
        val userId = UserId(stmt.columnLong(0))
        val address = SlyAddress(userId, stmt.columnInt(1))
        val id = PackageId(
            address,
            stmt.columnString(2)
        )
        val timestamp = stmt.columnLong(3)
        val message = stmt.columnString(4)

        return Package(id, timestamp, message)
    }

}