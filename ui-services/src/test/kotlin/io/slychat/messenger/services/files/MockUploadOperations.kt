package io.slychat.messenger.services.files

import io.slychat.messenger.core.files.RemoteFile
import io.slychat.messenger.core.http.api.upload.NewUploadResponse
import io.slychat.messenger.core.persistence.Upload
import io.slychat.messenger.core.persistence.UploadPart
import io.slychat.messenger.core.randomQuota
import nl.komponents.kovenant.Deferred
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.deferred
import rx.Observable
import rx.schedulers.TestScheduler
import rx.subjects.PublishSubject
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class MockUploadOperations(private val scheduler: TestScheduler) : UploadOperations {
    private var createDeferreds = HashMap<String, Deferred<NewUploadResponse, Exception>>()
    private var completeDeferred = deferred<Unit, Exception>()
    private var cancelDeferreds = HashMap<String, Deferred<Unit, Exception>>()
    private var uploadSubjects = HashMap<Int, PublishSubject<Long>>()
    private val cacheSubjects = HashMap<String, PublishSubject<Long>>()

    private val uploadPartCancellations = HashMap<Pair<String, Int>, AtomicBoolean>()
    private val cacheCancellations = HashMap<String, AtomicBoolean>()

    var autoResolveCreate = false

    private data class CreateArgs(val upload: Upload, val file: RemoteFile)
    private data class UploadArgs(val upload: Upload, val part: UploadPart, val file: RemoteFile)
    private data class CacheArgs(val upload: Upload, val file: RemoteFile)

    private var createArgs: CreateArgs? = null
    private var uploadArgs = HashMap<Pair<String, Int>, UploadArgs>()
    private var completeArgs: Upload? = null

    override fun create(upload: Upload, file: RemoteFile): Promise<NewUploadResponse, Exception> {
        if (autoResolveCreate)
            return Promise.of(NewUploadResponse(null, randomQuota()))

        val d = deferred<NewUploadResponse, Exception>()
        createDeferreds[upload.id] = d

        createArgs = CreateArgs(upload, file)
        return d.promise
    }

    override fun uploadPart(upload: Upload, part: UploadPart, file: RemoteFile, isCancelled: AtomicBoolean): Observable<Long> {
        uploadArgs[upload.id to part.n] = UploadArgs(upload, part, file)
        uploadPartCancellations[upload.id to part.n] = isCancelled

        if (part.n in uploadSubjects)
            throw RuntimeException("Attempted to upload part ${part.n} twice")

        val s = PublishSubject.create<Long>()
        uploadSubjects[part.n] = s

        return s
    }

    fun assertUploadPartCancelled(uploadId: String, partN: Int) {
        val isCancelled = uploadPartCancellations[uploadId to partN] ?: fail("uploadPart($uploadId, $partN) not called")

        assertTrue(isCancelled.get(), "Upload part not cancelled")
    }

    fun assertCacheCancelled(uploadId: String) {
        val isCancelled = cacheCancellations[uploadId] ?: fail("cache($uploadId) not called")

        assertTrue(isCancelled.get(), "Cache not cancelled")
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

    fun errorUploadPartOperation(n: Int, e: Exception) {
        getUploadPartSubject(n).onError(e)
        scheduler.triggerActions()
    }

    fun sendUploadProgress(uploadId: String, partN: Int, transferedBytes: Long) {
        val s = getUploadPartSubject(partN)
        s.onNext(transferedBytes)
        scheduler.triggerActions()
    }

    override fun complete(upload: Upload): Promise<Unit, Exception> {
        completeArgs = upload

        return completeDeferred.promise
    }

    fun assertCompleteNotCalled() {
        if (completeArgs != null)
            fail("complete($completeArgs) called")
    }

    fun assertCompleteCalled(upload: Upload) {
        val args = completeArgs ?: fail("complete() not called")

        assertEquals(upload, args, "complete() called with differing args")
    }

    fun completeCompleteUploadOperation() {
        completeDeferred.resolve(Unit)
    }

    fun rejectCompleteUploadOperation(e: Exception) {
        completeDeferred.reject(e)
    }

    override fun cache(upload: Upload, file: RemoteFile, isCancelled: AtomicBoolean): Observable<Long> {
        val s = PublishSubject.create<Long>()
        cacheSubjects[upload.id] = s
        cacheCancellations[upload.id] = isCancelled

        return s
    }

    fun completeCacheOperation(uploadId: String) {
        val s = cacheSubjects[uploadId] ?: fail("cache($uploadId) not called")

        s.onCompleted()
        scheduler.triggerActions()
    }

    fun errorCacheOperation(uploadId: String, e: Exception) {
        val s = cacheSubjects[uploadId] ?: fail("cache($uploadId) not called")

        s.onError(e)
        scheduler.triggerActions()
    }

    override fun cancel(upload: Upload): Promise<Unit, Exception> {
        val d = deferred<Unit, Exception>()
        cancelDeferreds[upload.id] = d
        return d.promise
    }

    fun assertCancelCalled(uploadId: String) {
        if (cancelDeferreds[uploadId] == null)
            fail("cancel($uploadId) not called")
    }

    fun resolveCancelOperation(uploadId: String) {
        val d = cancelDeferreds[uploadId] ?: fail("cancel($uploadId) not called")
        d.resolve(Unit)
    }

    fun rejectCancelOperation(uploadId: String, e: Exception) {
        val d = cancelDeferreds[uploadId] ?: fail("cancel($uploadId) not called")
        d.reject(e)
    }

    fun resolveCreateOperation(uploadId: String, resp: NewUploadResponse) {
        val d = createDeferreds[uploadId] ?: fail("create($uploadId) not called")
        d.resolve(resp)
    }

    fun rejectCreateOperation(uploadId: String, e: Exception) {
        val d = createDeferreds[uploadId] ?: fail("create($uploadId) not called")
        d.reject(e)
    }
}