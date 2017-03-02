package io.slychat.messenger.services.files

import io.slychat.messenger.core.Quota
import io.slychat.messenger.core.files.RemoteFile
import nl.komponents.kovenant.Promise
import rx.Observable

interface StorageService {
    val quota: Observable<Quota>

    val updates: Observable<List<RemoteFile>>

    fun init()

    fun shutdown()

    fun sync()

    fun getFileList(startingAt: Int, count: Int): Promise<List<RemoteFile>, Exception>

    fun getFileListFor(path: String): Promise<List<RemoteFile>, Exception>

    fun deleteFile(): Promise<Unit, Exception>

    fun getFileListVersion(): Promise<Int, Exception>
}