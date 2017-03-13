package io.slychat.messenger.services.files

import io.slychat.messenger.core.UserCredentials
import io.slychat.messenger.core.crypto.KeyVault
import io.slychat.messenger.core.files.RemoteFile
import io.slychat.messenger.core.files.encryptUserMetadata
import io.slychat.messenger.core.files.getFilePathHash
import io.slychat.messenger.core.http.api.storage.MetadataUpdateRequest
import io.slychat.messenger.core.http.api.storage.StorageAsyncClient
import io.slychat.messenger.core.http.api.storage.UpdateRequest
import io.slychat.messenger.core.persistence.FileListPersistenceManager
import io.slychat.messenger.core.persistence.FileListUpdate
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.bind
import nl.komponents.kovenant.functional.map
import org.slf4j.LoggerFactory
import java.util.*

class StorageSyncJobImpl(
    private val userCredentials: UserCredentials,
    private val keyVault: KeyVault,
    private val fileListPersistenceManager: FileListPersistenceManager,
    private val storageClient: StorageAsyncClient
) : StorageSyncJob {
    private class PushResults(val remoteUpdatesPerformed: Int)
    private class PullResults(val updates: List<RemoteFile>, val newListVersion: Long)

    private val log = LoggerFactory.getLogger(javaClass)

    private fun pushUpdates(): Promise<PushResults, Exception> {
        log.info("Running push")

        return fileListPersistenceManager.getRemoteUpdates() bind { remoteUpdates ->
            if (remoteUpdates.isEmpty())
                Promise.of(PushResults(0))
            else {
                val delete = ArrayList<String>()
                val updateMetadata = HashMap<String, MetadataUpdateRequest>()

                remoteUpdates.forEach {
                    when (it) {
                        is FileListUpdate.Delete -> delete.add(it.fileId)

                        is FileListUpdate.MetadataUpdate -> {
                            updateMetadata[it.fileId] = MetadataUpdateRequest(
                                getFilePathHash(keyVault, it.userMetadata),
                                encryptUserMetadata(keyVault, it.userMetadata)
                            )
                        }
                    }
                }

                val updateCount = remoteUpdates.size

                val request = UpdateRequest(delete, updateMetadata)
                storageClient.update(userCredentials, request) bind {
                    fileListPersistenceManager.removeRemoteUpdates(remoteUpdates.map { it.fileId }) map { PushResults(updateCount) }
                }
            }
        }
    }

    private fun pullUpdates(): Promise<PullResults, Exception> {
        log.info("Running pull")

        return fileListPersistenceManager.getVersion() bind { currentVersion ->
            storageClient.getFileList(userCredentials, currentVersion) bind {
                val remoteFiles = it.files.map { it.toRemoteFile(keyVault) }
                val latestVersion = it.version

                fileListPersistenceManager.mergeUpdates(remoteFiles, latestVersion) map {
                    PullResults(remoteFiles, latestVersion)
                }
            }
        }
    }

    override fun run(): Promise<StorageSyncResult, Exception> {
        return pushUpdates() bind { pushResults ->
            pullUpdates() map { pullResults ->
                StorageSyncResult(pushResults.remoteUpdatesPerformed, pullResults.updates, pullResults.newListVersion)
            }
        }
    }
}