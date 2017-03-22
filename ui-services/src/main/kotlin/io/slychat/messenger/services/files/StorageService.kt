package io.slychat.messenger.services.files

import io.slychat.messenger.core.Quota
import io.slychat.messenger.core.files.RemoteFile
import io.slychat.messenger.core.persistence.DirEntry
import nl.komponents.kovenant.Promise
import rx.Observable

interface StorageService {
    val quota: Observable<Quota>

    val transferEvents: Observable<TransferEvent>

    val syncEvents: Observable<FileListSyncEvent>

    val fileEvents: Observable<RemoteFileEvent>

    val transfers: List<TransferStatus>

    fun init()

    fun shutdown()

    fun sync()

    fun getFile(fileId: String): Promise<RemoteFile?, Exception>

    fun getFiles(startingAt: Int, count: Int): Promise<List<RemoteFile>, Exception>

    fun getFilesAt(startingAt: Int, count: Int, path: String): Promise<List<RemoteFile>, Exception>

    fun deleteFiles(fileIds: List<String>): Promise<Unit, Exception>

    fun uploadFile(localFilePath: String, remoteFileDirectory: String, remoteFileName: String, cache: Boolean): Promise<Unit, Exception>

    fun downloadFile(fileId: String, localFilePath: String): Promise<Unit, Exception>

    fun remove(transferIds: List<String>): Promise<Unit, Exception>

    fun retry(transferId: String): Promise<Unit, Exception>

    fun cancel(transferIds: List<String>)

    fun removeCompleted(): Promise<Unit, Exception>

    /** Must not be called during a sync. */
    fun clearSyncError()

    /** Will list directories, then files. */
    fun getEntriesAt(startingAt: Int, count: Int, path: String): Promise<List<DirEntry>, Exception>
}