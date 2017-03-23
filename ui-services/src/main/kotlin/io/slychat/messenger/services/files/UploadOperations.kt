package io.slychat.messenger.services.files

import io.slychat.messenger.core.files.RemoteFile
import io.slychat.messenger.core.http.api.upload.NewUploadResponse
import io.slychat.messenger.core.persistence.Upload
import io.slychat.messenger.core.persistence.UploadPart
import nl.komponents.kovenant.Promise
import rx.Observable
import java.util.concurrent.atomic.AtomicBoolean

//factory used to simplify testing
interface UploadOperations {
    fun create(upload: Upload, file: RemoteFile): Promise<NewUploadResponse, Exception>

    fun uploadPart(upload: Upload, part: UploadPart, file: RemoteFile, isCancelled: AtomicBoolean): Observable<Long>

    fun complete(upload: Upload): Promise<Unit, Exception>

    fun cache(upload: Upload, file: RemoteFile): Observable<Long>

    fun cancel(upload: Upload): Promise<Unit, Exception>
}