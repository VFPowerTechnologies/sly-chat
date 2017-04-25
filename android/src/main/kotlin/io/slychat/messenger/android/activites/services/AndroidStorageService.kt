package io.slychat.messenger.android.activites.services

import io.slychat.messenger.core.files.RemoteFile
import io.slychat.messenger.services.files.RemoteFileEvent
import io.slychat.messenger.services.files.TransferEvent
import nl.komponents.kovenant.Promise

interface AndroidStorageService {

    fun addRemoteFileListener(listener: (RemoteFileEvent) -> Unit)

    fun addTransferListener(listener: (TransferEvent) -> Unit)

    fun getEntriesAt(from: Int, to: Int, dir: String): Promise<List<AndroidDirEntry>, Exception>

    fun uploadFile(localFilePath: String, remoteFileDirectory: String, remoteFileName: String, cache: Boolean): Promise<Unit, Exception>

    fun deleteFiles(fileIds: List<String>): Promise<Unit, Exception>

    fun getFile(fileId: String): Promise<RemoteFile?, Exception>

    fun downloadFile(fileId: String, dir: String): Promise<Unit, Exception>

    fun getTransfers(): List<AndroidTransferStatus>

    fun remove(ids: List<String>): Promise<Unit, Exception>

    fun retry(id: String): Promise<Unit, Exception>

    fun removeCompleted(): Promise<Unit, Exception>

    fun cancel(ids: List<String>): Unit

    fun clearListeners()
}