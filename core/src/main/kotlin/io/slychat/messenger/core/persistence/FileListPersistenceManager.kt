package io.slychat.messenger.core.persistence

import io.slychat.messenger.core.files.RemoteFile
import io.slychat.messenger.core.files.UserMetadata
import nl.komponents.kovenant.Promise

interface FileListPersistenceManager {
    fun addFile(file: RemoteFile): Promise<Unit, Exception>

    fun getFiles(startingAt: Int, count: Int, includePending: Boolean, includeDeleted: Boolean): Promise<List<RemoteFile>, Exception>

    fun getFilesAt(startingAt: Int, count: Int, includePending: Boolean, includeDeleted: Boolean, path: String): Promise<List<RemoteFile>, Exception>

    fun getFileCount(): Promise<Int, Exception>

    //generates remote updates
    fun deleteFiles(fileIds: List<String>): Promise<List<RemoteFile>, Exception>

    //generates a remote update
    fun updateMetadata(fileId: String, userMetadata: UserMetadata): Promise<Unit, Exception>

    fun getFile(fileId: String): Promise<RemoteFile?, Exception>

    fun mergeUpdates(updates: List<RemoteFile>, latestVersion: Long): Promise<FileListMergeResults, Exception>

    fun getVersion(): Promise<Long, Exception>

    fun getRemoteUpdates(): Promise<List<FileListUpdate>, Exception>

    fun removeRemoteUpdates(fileIds: List<String>): Promise<Unit, Exception>

    //lists directories first
    fun getEntriesAt(startingAt: Int, count: Int, includePending: Boolean, path: String): Promise<List<DirEntry>, Exception>
}