package io.slychat.messenger.core.persistence

import io.slychat.messenger.core.files.RemoteFile
import io.slychat.messenger.core.files.UserMetadata
import nl.komponents.kovenant.Promise

interface FileListPersistenceManager {
    fun addFile(file: RemoteFile): Promise<Unit, Exception>

    //should we filter out upload-bound files here? Or at least provide an option to?
    fun getAllFiles(startingAt: Int, count: Int): Promise<List<RemoteFile>, Exception>

    //generates a remote update
    fun deleteFile(fileId: String): Promise<Unit, Exception>

    //generates a remote update
    fun updateMetadata(fileId: String, userMetadata: UserMetadata): Promise<Unit, Exception>

    fun getFileInfo(fileId: String): Promise<RemoteFile?, Exception>

    fun mergeUpdates(updates: List<RemoteFile>, latestVersion: Int): Promise<Unit, Exception>

    //TODO change to Long
    fun getVersion(): Promise<Int, Exception>

    fun getRemoteUpdates(): Promise<List<FileListUpdate>, Exception>
}