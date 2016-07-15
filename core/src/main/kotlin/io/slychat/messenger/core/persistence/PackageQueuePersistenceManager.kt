package io.slychat.messenger.core.persistence

import io.slychat.messenger.core.UserId
import nl.komponents.kovenant.Promise

interface PackageQueuePersistenceManager {
    /** Stores a received message prior to decryption. */
    fun addToQueue(pkg: Package): Promise<Unit, Exception>

    fun addToQueue(packages: Collection<Package>): Promise<Unit, Exception>

    fun removeFromQueue(packageId: PackageId): Promise<Unit, Exception>

    fun removeFromQueue(packageIds: Collection<PackageId>): Promise<Unit, Exception>

    fun removeFromQueue(userId: UserId, messageIds: Collection<String>): Promise<Unit, Exception>

    fun removeFromQueue(userId: UserId): Promise<Unit, Exception>

    fun removeFromQueue(users: Set<UserId>): Promise<Unit, Exception>

    fun getQueuedPackages(userId: UserId): Promise<List<Package>, Exception>

    fun getQueuedPackages(users: Collection<UserId>): Promise<List<Package>, Exception>

    fun getQueuedPackages(): Promise<List<Package>, Exception>
}