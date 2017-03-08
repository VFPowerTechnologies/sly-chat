package io.slychat.messenger.services.files

import io.slychat.messenger.core.files.RemoteFile
import io.slychat.messenger.core.persistence.Upload
import io.slychat.messenger.core.persistence.UploadPart
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.deferred
import rx.Observable
import rx.schedulers.TestScheduler
import rx.subjects.PublishSubject
import java.util.*
import kotlin.test.fail

class MockUploadOperations(private val scheduler: TestScheduler) : UploadOperations {
    var createDeferred = deferred<Unit, Exception>()
    var uploadSubjects = HashMap<Int, PublishSubject<Long>>()

    var autoResolveCreate = false

    private data class CreateArgs(val upload: Upload, val file: RemoteFile)
    private data class UploadArgs(val upload: Upload, val part: UploadPart, val file: RemoteFile)

    private var createArgs: CreateArgs? = null
    private var uploadArgs = HashMap<Pair<String, Int>, UploadArgs>()
    private val unsubscriptions = HashSet<Pair<String, Int>>()

    override fun create(upload: Upload, file: RemoteFile): Promise<Unit, Exception> {
        if (autoResolveCreate)
            return Promise.of(Unit)

        createArgs = CreateArgs(upload, file)
        return createDeferred.promise
    }

    override fun uploadPart(upload: Upload, part: UploadPart, file: RemoteFile): Observable<Long> {
        uploadArgs[upload.id to part.n] = UploadArgs(upload, part, file)

        if (part.n in uploadSubjects)
            throw RuntimeException("Attempted to upload part ${part.n} twice")

        val s = PublishSubject.create<Long>()
        uploadSubjects[part.n] = s

        return s.doOnUnsubscribe { unsubscriptions.add(upload.id to part.n) }
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

    fun assertUploadPartNotCalled() {
        if (uploadArgs.isNotEmpty())
            fail("uploadPart() called ${uploadArgs.size}")
    }

    fun assertUploadPartNotCalled(uploadId: String, n: Int) {
        val args = uploadArgs[uploadId to n]
        if (args != null)
            fail("uploadPart() called with args=$args")
    }

    private fun failWithDiff(fnName: String, expected: Any, actual: Any) {
        fail("$fnName() called with differing args\nExpected:\n$expected\n\nActual:\n$actual")
    }

    fun assertUploadPartCalled(upload: Upload, part: UploadPart, file: RemoteFile) {
        val args = uploadArgs[upload.id to part.n] ?: fail("uploadPart() not called for part ${part.n}")

        if (args.upload != upload)
            failWithDiff("uploadPart", upload, args.upload)
        if (args.part != part)
            failWithDiff("uploadPart", part, args.part)
        if (args.file != file)
            failWithDiff("uploadPart", file, args.file)
    }

    private fun getUploadPartSubject(n: Int): PublishSubject<Long> {
        return uploadSubjects[n] ?: fail("uploadPart() not called for part $n")
    }

    fun completeUploadPartOperation(n: Int) {
        getUploadPartSubject(n).onCompleted()
        scheduler.triggerActions()
    }

    fun sendUploadProgress(uploadId: String, partN: Int, transferedBytes: Long) {
        val s = getUploadPartSubject(partN)
        s.onNext(transferedBytes)
        scheduler.triggerActions()
    }

    fun assertUnsubscribed(uploadId: String, partN: Int) {
        if ((uploadId to partN) !in unsubscriptions)
            fail("$uploadId/$partN was not unsubscribed")
    }
}