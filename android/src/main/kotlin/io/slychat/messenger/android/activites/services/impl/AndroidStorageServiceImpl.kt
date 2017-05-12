package io.slychat.messenger.android.activites.services.impl

import android.support.v7.app.AppCompatActivity
import io.slychat.messenger.android.AndroidApp
import io.slychat.messenger.android.activites.services.*
import io.slychat.messenger.core.files.RemoteFile
import io.slychat.messenger.services.files.DownloadRequest
import io.slychat.messenger.services.files.RemoteFileEvent
import io.slychat.messenger.services.files.TransferEvent
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.map
import rx.Subscription

class AndroidStorageServiceImpl(activity: AppCompatActivity): AndroidStorageService {
    private val app = AndroidApp.get(activity)

    private val storageService = app.getUserComponent().storageService

    private lateinit var uiTransferListener: (TransferEvent) -> Unit
    private lateinit var uiRemotFileListener: (RemoteFileEvent) -> Unit
    private var remoteFileListener: Subscription? = null
    private var transferListener: Subscription? = null

    override fun addRemoteFileListener(listener: (RemoteFileEvent) -> Unit) {
        uiRemotFileListener = listener
        remoteFileListener?.unsubscribe()
        remoteFileListener = storageService.fileEvents.subscribe { event ->
            remoteFileEventUpdateUI(event)
        }
    }

    override fun addTransferListener(listener: (TransferEvent) -> Unit) {
        uiTransferListener = listener
        transferListener?.unsubscribe()
        transferListener = storageService.transferEvents.subscribe { event ->
            transferEventUpdateUI(event)
        }
    }

    override fun clearListeners() {
        remoteFileListener?.unsubscribe()
        transferListener?.unsubscribe()
    }

    override fun getEntriesAt(from: Int, to: Int, dir: String): Promise<List<AndroidDirEntry>, Exception> {
        return storageService.getEntriesAt(from, to, dir) map { dirEntries ->
            dirEntries.toAndroidDirEntry()
        }
    }

    override fun getFile(fileId: String): Promise<RemoteFile?, Exception> {
        return storageService.getFile(fileId)
    }

    override fun downloadFile(fileId: String, dir: String): Promise<Unit, Exception> {
        return storageService.downloadFiles(listOf(DownloadRequest(fileId, dir, true))) map { Unit }
    }

    override fun uploadFile(localFilePath: String, remoteFileDirectory: String, remoteFileName: String, cache: Boolean): Promise<Unit, Exception> {
        return storageService.uploadFile(localFilePath, remoteFileDirectory, remoteFileName, cache)
    }

    override fun deleteFiles(fileIds: List<String>): Promise<Unit, Exception> {
        return storageService.deleteFiles(fileIds)
    }

    override fun getTransfers(): List<AndroidTransferStatus> {
        return storageService.transfers.toAndroid()
    }

    override fun remove(ids: List<String>): Promise<Unit, Exception> {
        return storageService.remove(ids)
    }

    override fun retry(id: String): Promise<Unit, Exception> {
        return storageService.retry(id)
    }

    override fun removeCompleted(): Promise<Unit, Exception> {
        return storageService.removeCompleted()
    }

    override fun cancel(ids: List<String>): Unit {
        return storageService.cancel(ids)
    }

    private fun remoteFileEventUpdateUI(event: RemoteFileEvent) {
        uiRemotFileListener.invoke(event)
    }

    private fun transferEventUpdateUI(event: TransferEvent) {
        uiTransferListener.invoke(event)
    }
}