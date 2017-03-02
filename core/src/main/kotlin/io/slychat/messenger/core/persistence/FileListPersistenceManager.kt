package io.slychat.messenger.core.persistence

import io.slychat.messenger.core.files.RemoteFile
import nl.komponents.kovenant.Promise

interface FileListPersistenceManager {
    fun addFile(file: RemoteFile): Promise<Unit, Exception>

    fun getAllFiles(startingAt: Int, count: Int): Promise<List<RemoteFile>, Exception>

    fun updateFile(file: RemoteFile): Promise<Unit, Exception>

    fun getFileInfo(fileId: String): Promise<RemoteFile?, Exception>

    fun mergeUpdates(updates: List<RemoteFile>, latestVersion: Int): Promise<Unit, Exception>

    fun getVersion(): Promise<Int, Exception>
}