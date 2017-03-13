package io.slychat.messenger.services.files

import io.slychat.messenger.core.Quota
import io.slychat.messenger.core.files.RemoteFile
import nl.komponents.kovenant.Promise
import rx.Observable

interface StorageService {
    val quota: Observable<Quota>

    val updates: Observable<List<RemoteFile>>

    val transferEvents: Observable<TransferEvent>

    val syncEvents: Observable<FileListSyncEvent>

    val uploads: List<UploadStatus>

    val downloads: List<DownloadStatus>

    fun init()

    fun shutdown()

    fun sync()

    fun getFile(fileId: String): Promise<RemoteFile?, Exception>

    fun getFiles(startingAt: Int, count: Int): Promise<List<RemoteFile>, Exception>

    fun getFilesAt(startingAt: Int, count: Int, path: String): Promise<List<RemoteFile>, Exception>

    fun deleteFiles(fileIds: List<String>): Promise<Unit, Exception>

    fun uploadFile(localFilePath: String, remoteFileDirectory: String, remoteFileName: String): Promise<Unit, Exception>

    fun downloadFile(fileId: String, localFilePath: String): Promise<Unit, Exception>

    fun removeUploads(uploadIds: List<String>): Promise<Unit, Exception>

    fun removeDownload(downloadId: String): Promise<Unit, Exception>

    fun retryDownload(downloadId: String): Promise<Unit, Exception>

    fun retryUpload(uploadId: String): Promise<Unit, Exception>
}