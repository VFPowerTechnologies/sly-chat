package io.slychat.messenger.services.ui.impl

import io.slychat.messenger.core.Quota
import io.slychat.messenger.services.di.UserComponent
import io.slychat.messenger.services.files.*
import io.slychat.messenger.services.ui.*
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.map
import rx.Observable
import rx.subscriptions.CompositeSubscription

class UIStorageServiceImpl(
    userSessionAvailable: Observable<UserComponent?>
) : UIStorageService {
    private val subscriptions = CompositeSubscription()

    private var storageService: StorageService? = null

    private var quotaListeners = ArrayList<(Quota) -> Unit>()

    private var transferListeners = ArrayList<(UITransferEvent) -> Unit>()

    private var syncListeners = ArrayList<(UIFileSyncEvent) -> Unit>()

    private var fileListeners = ArrayList<(UIRemoteFileEvent) -> Unit>()

    private fun getServiceOrThrow(): StorageService {
        return storageService ?: error("Not logged in")
    }

    init {
        userSessionAvailable.subscribe { onUserSessionAvailabilityChanged(it) }
    }
    private fun onUserSessionAvailabilityChanged(userComponent: UserComponent?) {
        if (userComponent != null) {
            val storageService = userComponent.storageService

            this.storageService = storageService

            storageService.quota.subscribe { onQuotaUpdate(it) }
            storageService.transferEvents.subscribe { onTransferEvent(it) }
            storageService.syncEvents.subscribe { onSyncEvent(it) }
            storageService.fileEvents.subscribe { onRemoteFileEvent(it) }
        }
        else {
            subscriptions.clear()
            storageService = null
        }
    }

    private fun onRemoteFileEvent(event: RemoteFileEvent) {
        fileListeners.forEach { it(event.toUI()) }
    }

    private fun onSyncEvent(event: FileListSyncEvent) {
        syncListeners.forEach { it(event.toUI()) }
    }

    private fun onTransferEvent(event: TransferEvent) {
        transferListeners.forEach { it(event.toUI()) }
    }

    private fun onQuotaUpdate(quota: Quota) {
        quotaListeners.forEach { it(quota) }
    }

    override fun getFile(fileId: String): Promise<UIRemoteFile?, Exception> {
        return getServiceOrThrow().getFile(fileId).map { it?.toUI() }
    }

    override fun getFiles(startingAt: Int, count: Int): Promise<List<UIRemoteFile>, Exception> {
        return getServiceOrThrow().getFiles(startingAt, count).map { it.map { it.toUI() } }
    }

    override fun getFilesAt(startingAt: Int, count: Int, path: String): Promise<List<UIRemoteFile>, Exception> {
        return getServiceOrThrow().getFilesAt(startingAt, count, path).map { it.map { it.toUI() } }
    }

    override fun deleteFiles(fileIds: List<String>): Promise<Unit, Exception> {
        return getServiceOrThrow().deleteFiles(fileIds)
    }

    override fun uploadFile(localFilePath: String, remoteFileDirectory: String, remoteFileName: String, cache: Boolean): Promise<Unit, Exception> {
        return getServiceOrThrow().uploadFile(localFilePath, remoteFileDirectory, remoteFileName, cache)
    }

    override fun downloadFile(fileId: String, localFilePath: String): Promise<Unit, Exception> {
        return getServiceOrThrow().downloadFiles(listOf(DownloadRequest(fileId, localFilePath))).map { Unit }
    }

    override fun remove(transferIds: List<String>): Promise<Unit, Exception> {
        return getServiceOrThrow().remove(transferIds)
    }

    override fun retry(transferId: String): Promise<Unit, Exception> {
        return getServiceOrThrow().retry(transferId)
    }

    override fun cancel(transferIds: List<String>) {
        return getServiceOrThrow().cancel(transferIds)
    }

    override fun removeCompleted(): Promise<Unit, Exception> {
        return getServiceOrThrow().removeCompleted()
    }

    override fun clearSyncError() {
        return getServiceOrThrow().clearSyncError()
    }

    override fun getEntriesAt(startingAt: Int, count: Int, path: String): Promise<List<UIDirEntry>, Exception> {
        return getServiceOrThrow().getEntriesAt(startingAt, count, path).map { it.map { it.toUI() } }
    }

    override val transfers: List<UITransferStatus>
        get() = getServiceOrThrow().transfers.map { it.toUI() }

    override fun addQuotaListener(listener: (Quota) -> Unit) {
        quotaListeners.add(listener)
    }

    override fun addTransferListener(listener: (UITransferEvent) -> Unit) {
        transferListeners.add(listener)
    }

    override fun addSyncListener(listener: (UIFileSyncEvent) -> Unit) {
        syncListeners.add(listener)
    }

    override fun addFileListener(listener: (UIRemoteFileEvent) -> Unit) {
        fileListeners.add(listener)
    }

    override fun clearListeners() {
        quotaListeners.clear()
        transferListeners.clear()
        syncListeners.clear()
        fileListeners.clear()
    }
}