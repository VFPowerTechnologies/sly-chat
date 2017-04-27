package io.slychat.messenger.services.ui

import com.vfpowertech.jsbridge.processor.annotations.Exclude
import com.vfpowertech.jsbridge.processor.annotations.JSToJavaGenerate
import io.slychat.messenger.core.Quota
import nl.komponents.kovenant.Promise

@JSToJavaGenerate("StorageService")
interface UIStorageService {
    fun getFile(fileId: String): Promise<UIRemoteFile?, Exception>

    fun getFiles(startingAt: Int, count: Int): Promise<List<UIRemoteFile>, Exception>

    fun getFilesAt(startingAt: Int, count: Int, path: String): Promise<List<UIRemoteFile>, Exception>

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
    fun getEntriesAt(startingAt: Int, count: Int, path: String): Promise<List<UIDirEntry>, Exception>

    val transfers: List<UITransferStatus>

    fun addQuotaListener(listener: (Quota) -> Unit)

    fun addTransferListener(listener: (UITransferEvent) -> Unit)

    fun addSyncListener(listener: (UIFileSyncEvent) -> Unit)

    fun addFileListener(listener: (UIRemoteFileEvent) -> Unit)

    @Exclude
    fun clearListeners()
}