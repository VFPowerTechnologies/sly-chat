package io.slychat.messenger.core.persistence.sqlite

import io.slychat.messenger.core.crypto.generateDownloadId
import io.slychat.messenger.core.crypto.generateFileId
import io.slychat.messenger.core.persistence.AttachmentCacheRequest
import io.slychat.messenger.testutils.desc
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test

class SQLiteAttachmentCachePersistenceManagerTest {
    companion object {
        @JvmStatic
        @BeforeClass
        fun loadLibrary() {
            SQLiteAttachmentCachePersistenceManagerTest::class.java.loadSQLiteLibraryFromResources()
        }
    }

    private lateinit var persistenceManager: SQLitePersistenceManager
    private lateinit var attachmentCachePersistenceManager: SQLiteAttachmentCachePersistenceManager

    private fun randomAttachmentCacheRequest(fileId: String? = null, downloadId: String? = null): AttachmentCacheRequest {
        return AttachmentCacheRequest(fileId ?: generateFileId(), downloadId)
    }

    private fun addRequest(request: AttachmentCacheRequest) {
        attachmentCachePersistenceManager.addRequests(listOf(request)).get()
    }

    @Before
    fun before() {
        persistenceManager = SQLitePersistenceManager(null, null)
        persistenceManager.init()

        attachmentCachePersistenceManager = SQLiteAttachmentCachePersistenceManager(persistenceManager)
    }

    @After
    fun after() {
        persistenceManager.shutdown()
    }

    @Test
    fun `addRequest should add the given request`() {
        val requests = listOf(
            AttachmentCacheRequest(generateFileId(), null),
            AttachmentCacheRequest(generateFileId(), generateDownloadId())
        )

        attachmentCachePersistenceManager.addRequests(requests).get()

        assertThat(attachmentCachePersistenceManager.getAllRequests().get()).desc("Should contain all added requests") {
            containsAll(requests)
        }
    }

    @Test
    fun `updateRequests should update the given request`() {
        val request = randomAttachmentCacheRequest()
        val request2 = randomAttachmentCacheRequest()

        addRequest(request)
        addRequest(request2)

        val updated = request.copy(downloadId = generateDownloadId())
        val updated2 = request2.copy(downloadId = generateDownloadId())

        attachmentCachePersistenceManager.updateRequests(listOf(updated, updated2)).get()

        assertThat(attachmentCachePersistenceManager.getAllRequests().get()).desc("Should update requests") {
            contains(updated, updated2)
        }
    }

    @Test
    fun `deleteRequests should delete the specified requests`() {
        val request = randomAttachmentCacheRequest()
        val request2 = randomAttachmentCacheRequest()

        addRequest(request)
        addRequest(request2)

        attachmentCachePersistenceManager.deleteRequests(listOf(request.fileId, request2.fileId)).get()

        assertThat(attachmentCachePersistenceManager.getAllRequests().get()).desc("Should not contain deleted requests") {
            doesNotContain(request, request2)
        }
    }
}