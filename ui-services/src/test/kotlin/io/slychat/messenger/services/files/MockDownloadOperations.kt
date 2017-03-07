package io.slychat.messenger.services.files

import io.slychat.messenger.core.files.RemoteFile
import io.slychat.messenger.core.persistence.Download
import nl.komponents.kovenant.Deferred
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.deferred
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.fail

class MockDownloadOperations : DownloadOperations {
    private data class DownloadArgs(val download: Download, val file: RemoteFile)

    private val downloadDeferreds = HashMap<String, Deferred<Unit, Exception>>()

    private var downloadArgs: DownloadArgs? = null

    override fun download(download: Download, file: RemoteFile, isCancelled: AtomicBoolean, progressCallback: (Long) -> Unit): Promise<Unit, Exception> {
        downloadArgs = DownloadArgs(download, file)

        val d = deferred<Unit, Exception>()
        downloadDeferreds[download.id] = d
        return d.promise
    }

    fun assertDownloadNotCalled() {
        if (downloadArgs != null)
            fail("download() called with $downloadArgs")
    }

    fun assertDownloadCalled(download: Download, file: RemoteFile) {
        val args = downloadArgs ?: fail("download() not called")

        val expected = DownloadArgs(download, file)
        if (args != expected)
            fail("download() called with differing args\nExpected:\n$expected\n\nActual:\n$args")
    }

    fun getDownloadDeferred(downloadId: String): Deferred<Unit, Exception> {
        return downloadDeferreds[downloadId] ?: fail("download() not called for $downloadId")
    }
}