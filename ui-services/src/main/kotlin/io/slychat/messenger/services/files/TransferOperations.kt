package io.slychat.messenger.services.files

import io.slychat.messenger.core.files.RemoteFile
import io.slychat.messenger.core.persistence.Upload
import io.slychat.messenger.core.persistence.UploadPart
import nl.komponents.kovenant.Promise

//factory used to simplify testing
interface TransferOperations {
    fun create(upload: Upload, file: RemoteFile): Promise<Unit, Exception>
    fun uploadPart(upload: Upload, part: UploadPart, file: RemoteFile, progressCallback: (Long) -> Unit): Promise<Unit, Exception>
}