package io.slychat.messenger.core.persistence.sqlite

import io.slychat.messenger.core.SlyAddress
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.currentTimestamp
import io.slychat.messenger.core.persistence.Package
import io.slychat.messenger.core.persistence.PackageId
import org.junit.After
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SQLitePackageQueuePersistenceManagerTest {
    companion object {
        @JvmStatic
        @BeforeClass
        fun loadLibrary() {
            SQLitePreKeyPersistenceManager::class.java.loadSQLiteLibraryFromResources()
        }
    }

    lateinit var persistenceManager: SQLitePersistenceManager
    lateinit var packageQueuePersistenceManager: SQLitePackageQueuePersistenceManager

    @Before
    fun before() {
        persistenceManager = SQLitePersistenceManager(null, null)
        persistenceManager.init()
        packageQueuePersistenceManager = SQLitePackageQueuePersistenceManager(persistenceManager)
    }

    @After
    fun after() {
        persistenceManager.shutdown()
    }

    private fun queuedPackageFromInt(address: SlyAddress, i: Int): Package {
        return Package(
            PackageId(address, "$i"),
            currentTimestamp() + (i * 10),
            "message $i"
        )
    }

    @Test
    fun `addToQueue should store the given packages`() {
        val address = SlyAddress(UserId(1), 1)
        val queuedMessages = (0..1).map { queuedPackageFromInt(address, it) }

        packageQueuePersistenceManager.addToQueue(queuedMessages).get()
        val got = packageQueuePersistenceManager.getQueuedPackages().get()

        val expected = queuedMessages.sortedBy { it.timestamp }
        val gotSorted = got.sortedBy { it.timestamp }

        assertEquals(queuedMessages.size, gotSorted.size, "Invalid number of messages")
        assertEquals(expected, gotSorted, "Invalid messages")
    }

    @Test
    fun `getQueuedPackages(UserId) should only return packages for the given user`() {
        val address1 = SlyAddress(UserId(1), 1)
        val address2 = SlyAddress(UserId(2), 1)
        val queuedPackages1 = (0..1).map { queuedPackageFromInt(address1, it) }
        val queuedPackages2 = (0..1).map { queuedPackageFromInt(address2, it) }

        packageQueuePersistenceManager.addToQueue(queuedPackages1).get()
        packageQueuePersistenceManager.addToQueue(queuedPackages2).get()

        val got = packageQueuePersistenceManager.getQueuedPackages(address1.id).get()

        assertEquals(queuedPackages1, got, "Packages don't match")
    }

    @Test
    fun `getQueuedPackages(Set) should only return packages for the given users`() {
        val addresses = (0..2).map { SlyAddress(UserId(it.toLong()), 1) }

        val queuedPackages = addresses.map { address ->
            (0..1).map { queuedPackageFromInt(address, it) }
        }

        packageQueuePersistenceManager.addToQueue(queuedPackages.flatten()).get()

        val interestedUsers = addresses.subList(0, 2).map { it.id }.toSet()

        val packages = packageQueuePersistenceManager.getQueuedPackages(interestedUsers).get()

        assertTrue(packages.all { it.id.address.id in interestedUsers }, "Invalid package list")
    }

    @Test
    fun `removeFromQueue should remove the given packages`() {
        val address = SlyAddress(UserId(1), 1)
        val queuedPackages = (0..3).map { queuedPackageFromInt(address, it) }

        val toRemove = queuedPackages.subList(0, 2)
        val toKeep = queuedPackages.subList(2, 4)

        packageQueuePersistenceManager.addToQueue(queuedPackages).get()
        packageQueuePersistenceManager.removeFromQueue(address.id, toRemove.map { it.id.messageId }).get()

        val remaining = packageQueuePersistenceManager.getQueuedPackages().get()

        assertEquals(toKeep.size, remaining.size, "Invalid number of messages")

        val remainingSorted = remaining.sortedBy { it.timestamp }
        val toKeepSorted = toKeep.sortedBy { it.timestamp }

        assertEquals(toKeepSorted, remainingSorted, "Invalid messages")
    }

    @Test
    fun `removeFromQueue(PackageId) should remove the given packages`() {
        val address = SlyAddress(UserId(1), 1)
        val queuedPackages = (0..3).map { queuedPackageFromInt(address, it) }

        val toRemove = queuedPackages.subList(0, 2)
        val toKeep = queuedPackages.subList(2, 4)

        packageQueuePersistenceManager.addToQueue(queuedPackages).get()
        packageQueuePersistenceManager.removeFromQueue(toRemove.map { it.id }).get()

        val remaining = packageQueuePersistenceManager.getQueuedPackages().get()

        assertEquals(toKeep.size, remaining.size, "Invalid number of messages")

        val remainingSorted = remaining.sortedBy { it.timestamp }
        val toKeepSorted = toKeep.sortedBy { it.timestamp }

        assertEquals(toKeepSorted, remainingSorted, "Invalid messages")
    }

    @Test
    fun `removeFromQueue(UserId) should remove all packages for a user`() {
        val address = SlyAddress(UserId(1), 1)
        val queuedPackages = (0..3).map { queuedPackageFromInt(address, it) }

        packageQueuePersistenceManager.addToQueue(queuedPackages).get()

        packageQueuePersistenceManager.removeFromQueue(address.id).get()

        val queued = packageQueuePersistenceManager.getQueuedPackages(address.id).get()

        assertTrue(queued.isEmpty(), "Packages not deleted")
    }

    @Test
    fun `removeFromQueue(Set) should remove all packages for the given users`() {
        val address1 = SlyAddress(UserId(1), 1)
        val address2 = SlyAddress(UserId(2), 1)
        val address3 = SlyAddress(UserId(3), 1)
        val queuedPackages1 = (0..1).map { queuedPackageFromInt(address1, it) }
        val queuedPackages2 = (0..1).map { queuedPackageFromInt(address2, it) }
        val queuedPackages3 = (0..1).map { queuedPackageFromInt(address3, it) }

        packageQueuePersistenceManager.addToQueue(queuedPackages1).get()
        packageQueuePersistenceManager.addToQueue(queuedPackages2).get()
        packageQueuePersistenceManager.addToQueue(queuedPackages3).get()

        packageQueuePersistenceManager.removeFromQueue(setOf(address1.id, address2.id)).get()

        val queued = packageQueuePersistenceManager.getQueuedPackages().get()

        assertEquals(queuedPackages3, queued, "Invalid package list")
    }
}