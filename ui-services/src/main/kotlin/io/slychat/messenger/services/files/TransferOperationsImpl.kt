package io.slychat.messenger.services.files

import io.slychat.messenger.core.crypto.KeyVault
import io.slychat.messenger.core.files.RemoteFile
import io.slychat.messenger.core.persistence.Upload
import io.slychat.messenger.core.persistence.UploadPart
import io.slychat.messenger.services.UploadClientFactory
import io.slychat.messenger.services.auth.AuthTokenManager
import nl.komponents.kovenant.Context
import nl.komponents.kovenant.Promise

class TransferOperationsImpl(
    private val authTokenManager: AuthTokenManager,
    private val uploadClientFactory: UploadClientFactory,
    private val keyVault: KeyVault,
    //TODO
    private val context: Context
) : TransferOperations {
    override fun create(upload: Upload, file: RemoteFile): Promise<Unit, Exception> {
        return authTokenManager.map {
            val op = CreateUploadOperation(it, upload, file, keyVault, uploadClientFactory.create())
            op.run()
        }
    }

    override fun uploadPart(upload: Upload, part: UploadPart, file: RemoteFile, progressCallback: (Long) -> Unit): Promise<Unit, Exception> {
        return authTokenManager.map {
            val op = UploadPartOperation(it, upload, part, file, uploadClientFactory.create(), progressCallback)

            op.run()
        }
    }
}