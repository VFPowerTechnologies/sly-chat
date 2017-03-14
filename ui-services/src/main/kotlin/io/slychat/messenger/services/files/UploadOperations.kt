package io.slychat.messenger.services.files

import io.slychat.messenger.core.files.RemoteFile
import io.slychat.messenger.core.persistence.Upload
import io.slychat.messenger.core.persistence.UploadPart
import nl.komponents.kovenant.Promise
import rx.Observable

//factory used to simplify testing
interface UploadOperations {
    fun create(upload: Upload, file: RemoteFile): Promise<Unit, Exception>

    fun uploadPart(upload: Upload, part: UploadPart, file: RemoteFile): Observable<Long>

    fun complete(upload: Upload): Promise<Unit, Exception>
}