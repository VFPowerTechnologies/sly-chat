package io.slychat.messenger.services.files

import io.slychat.messenger.core.files.RemoteFile
import io.slychat.messenger.core.persistence.Upload
import io.slychat.messenger.core.persistence.UploadPart
import nl.komponents.kovenant.Deferred
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.deferred
import java.util.*
import kotlin.test.fail

class MockTransferOperations : TransferOperations {
    var createDeferred = deferred<Unit, Exception>()
    var uploadDeferreds = HashMap<Int, Deferred<Unit, Exception>>()

    var autoResolveCreate = false

    private data class CreateArgs(val upload: Upload, val file: RemoteFile)
    private data class UploadArgs(val upload: Upload, val part: UploadPart, val file: RemoteFile)

    private var createArgs: CreateArgs? = null
    private var uploadArgs = HashMap<Int, UploadArgs>()

    override fun create(upload: Upload, file: RemoteFile): Promise<Unit, Exception> {
        if (autoResolveCreate)
            return Promise.of(Unit)

        createArgs = CreateArgs(upload, file)
        return createDeferred.promise
    }

    override fun uploadPart(upload: Upload, part: UploadPart, file: RemoteFile, progressCallback: (Long) -> Unit): Promise<Unit, Exception> {
        uploadArgs[part.n] = UploadArgs(upload, part, file)

        if (part.n in uploadDeferreds)
            throw RuntimeException("Attempted to upload part ${part.n} twice")

        val d = deferred<Unit, Exception>()
        uploadDeferreds[part.n] = d

        return d.promise
    }

    fun assertCreateNotCalled() {
        if (createArgs != null)
            fail("create() called with args=$createArgs")
    }

    fun assertCreateCalled(upload: Upload, file: RemoteFile) {
        val args = createArgs ?: fail("create() not called")

        val expected = CreateArgs(upload, file)
        if (args != expected)
            fail("create() called with differing args\nExpected:\n$expected\n\nActual:\n$args")
    }

    fun assertUploadPartNotCalled(n: Int) {
        val args = uploadArgs[n]
        if (args != null)
            fail("uploadPart() called with args=$args")
    }

    private fun failWithDiff(fnName: String, expected: Any, actual: Any) {
        fail("$fnName() called with differing args\nExpected:\n$expected\n\nActual:\n$actual")
    }

    fun assertUploadPartCalled(upload: Upload, part: UploadPart, file: RemoteFile) {
        val args = uploadArgs[part.n] ?: fail("uploadPart() not called for part ${part.n}")

        if (args.upload != upload)
            failWithDiff("uploadPart", upload, args.upload)
        if (args.part != part)
            failWithDiff("uploadPart", part, args.part)
        if (args.file != file)
            failWithDiff("uploadPart", file, args.file)
    }

    fun getUploadPartDeferred(n: Int): Deferred<Unit, Exception> {
        return uploadDeferreds[n] ?: fail("uploadPart() not called for part $n")
    }

    fun completeUploadPartOperation(n: Int) {
        getUploadPartDeferred(n).resolve(Unit)
    }
}