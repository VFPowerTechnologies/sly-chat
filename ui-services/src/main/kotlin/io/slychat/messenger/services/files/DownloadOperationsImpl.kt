package io.slychat.messenger.services.files

import io.slychat.messenger.core.crypto.KeyVault
import io.slychat.messenger.core.files.RemoteFile
import io.slychat.messenger.core.persistence.Download
import io.slychat.messenger.services.StorageClientFactory
import io.slychat.messenger.services.auth.AuthTokenManager
import nl.komponents.kovenant.Context
import nl.komponents.kovenant.Promise
import java.util.concurrent.atomic.AtomicBoolean

class DownloadOperationsImpl(
    private val authTokenManager: AuthTokenManager,
    private val storageClientFactory: StorageClientFactory,
    private val keyVault: KeyVault,
    //TODO
    private val context: Context
) : DownloadOperations {
    override fun download(download: Download, file: RemoteFile, isCancelled: AtomicBoolean, progressCallback: (Long) -> Unit): Promise<Unit, Exception> {
        return authTokenManager.map {
            val op = DownloadOperation(it, download, file, storageClientFactory.create(), isCancelled, progressCallback)
            op.run()
        }
    }
}