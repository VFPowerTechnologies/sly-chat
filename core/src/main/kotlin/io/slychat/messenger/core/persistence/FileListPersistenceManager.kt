package io.slychat.messenger.core.persistence

import io.slychat.messenger.core.files.RemoteFile
import nl.komponents.kovenant.Promise

interface FileListPersistenceManager {
    fun getAllFiles(): Promise<List<RemoteFile>, Exception>

    fun mergeUpdates(updates: List<RemoteFile>, latestVersion: Int): Promise<Unit, Exception>
}