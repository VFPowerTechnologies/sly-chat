package io.slychat.messenger.services.messaging

import io.slychat.messenger.core.persistence.Package
import nl.komponents.kovenant.Promise

interface MessageReceiver {
    /** Promise completes once the packages have been written to disk. */
    fun processPackages(packages: List<Package>): Promise<Unit, Exception>

    fun shutdown()
    fun init()
}