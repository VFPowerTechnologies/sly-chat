package io.slychat.messenger.services.files

import io.slychat.messenger.core.files.RemoteFile
import io.slychat.messenger.core.persistence.Download
import rx.Observable

interface DownloadOperations {
    fun download(download: Download, file: RemoteFile): Observable<Long>
}