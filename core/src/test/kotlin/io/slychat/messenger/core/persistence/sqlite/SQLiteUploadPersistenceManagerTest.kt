package io.slychat.messenger.core.persistence.sqlite

import io.slychat.messenger.core.crypto.generateFileId
import io.slychat.messenger.core.crypto.generateUploadId
import io.slychat.messenger.core.files.RemoteFile
import io.slychat.messenger.core.persistence.*
import io.slychat.messenger.core.randomRemoteFile
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import kotlin.test.*

class SQLiteUploadPersistenceManagerTest {
    companion object {
        @JvmStatic
        @BeforeClass
        fun loadLibrary() {
            SQLitePreKeyPersistenceManager::class.java.loadSQLiteLibraryFromResources()
        }
    }

    private lateinit var persistenceManager: SQLitePersistenceManager
    private lateinit var fileListPersistenceManager: SQLiteFileListPersistenceManager
    private lateinit var uploadPersistenceManager: SQLiteUploadPersistenceManager

    @Before
    fun before() {
        persistenceManager = SQLitePersistenceManager(null, null)
        persistenceManager.init()

        fileListPersistenceManager = SQLiteFileListPersistenceManager(persistenceManager)
        uploadPersistenceManager = SQLiteUploadPersistenceManager(persistenceManager)
    }

    @After
    fun after() {
        persistenceManager.shutdown()
    }

    private fun randomFilePath(): String {
        return "/path/file.ext"
    }

    private fun insertFile(): RemoteFile {
        val file = randomRemoteFile()
        fileListPersistenceManager.addFile(file).get()
        return file
    }

    private fun insertUpload(error: UploadError? = null): Upload {
        val upload = randomUpload(insertFile().id, error)
        uploadPersistenceManager.add(upload).get()
        return upload
    }

    private fun randomUpload(fileId: String, error: UploadError? = null): Upload {
        val parts = listOf(
            UploadPart(1, 10, false),
            UploadPart(2, 5, false)
        )

        return Upload(
            generateUploadId(),
            fileId,
            UploadState.PENDING,
            randomFilePath(),
            false,
            error,
            parts
        )
    }

    private fun getUpload(uploadId: String, message: String = "Upload not in db"): Upload {
        return assertNotNull(uploadPersistenceManager.get(uploadId).get(), message)
    }

    @Test
    fun `add should insert a new upload entry`() {
        val file = insertFile()

        val upload = randomUpload(file.id)

        uploadPersistenceManager.add(upload).get()

        val actual = getUpload(upload.id, "Upload not inserted in db")

        assertThat(actual).apply {
            describedAs("Should match the inserted upload")
            isEqualToComparingFieldByField(upload)
        }
    }

    @Test
    fun `add should throw InvalidFileException if no associated file exists`() {
        val upload = randomUpload(generateFileId())

        assertFailsWith(InvalidFileException::class) {
            uploadPersistenceManager.add(upload).get()
        }
    }

    @Test
    fun `setState should update the upload's state`() {
        val upload = insertUpload()

        val newState = UploadState.COMPLETE

        uploadPersistenceManager.setState(upload.id, newState).get()

        val actual = getUpload(upload.id)

        assertEquals(newState, actual.state, "State not updated")
    }

    @Test
    fun `setState should throw InvalidUploadException if upload doesn't exist`() {
        assertFailsWith(InvalidUploadException::class) {
            uploadPersistenceManager.setState(generateUploadId(), UploadState.COMPLETE).get()
        }
    }

    @Test
    fun `setError should set a new error`() {
        val upload = insertUpload()

        val error = UploadError.INSUFFICIENT_QUOTA

        uploadPersistenceManager.setError(upload.id, error).get()

        val actual = getUpload(upload.id)

        assertEquals(error, actual.error, "Error not updated")
    }

    @Test
    fun `setError should remove an error`() {
        val upload = insertUpload(UploadError.INSUFFICIENT_QUOTA)

        uploadPersistenceManager.setError(upload.id, null).get()

        val actual = getUpload(upload.id)

        assertNull(actual.error, "Error not updated")
    }

    @Test
    fun `setError should throw InvalidUploadException if upload doesn't exist`() {
        assertFailsWith(InvalidUploadException::class) {
            uploadPersistenceManager.setError(generateUploadId(), null).get()
        }
    }

    @Test
    fun `completePart should mark part as complete`() {
        val upload = insertUpload()
        val part = upload.parts.first()

        uploadPersistenceManager.completePart(upload.id, part.n).get()

        val actual = getUpload(upload.id)
        val actualPart = actual.parts.first()

        assertTrue(actualPart.isComplete, "Part not marked as completed")
    }

    @Test
    fun `completePart should throw InvalidUploadException if upload doesn't exist`() {
        assertFailsWith(InvalidUploadException::class) {
            uploadPersistenceManager.completePart(generateUploadId(), 1).get()
        }
    }

    @Test
    fun `completePart should throw InvalidUploadPartException if part doesn't exist`() {
        val upload = insertUpload()

        assertFailsWith(InvalidUploadPartException::class) {
            uploadPersistenceManager.completePart(upload.id, upload.parts.size + 1).get()
        }
    }

    @Test
    fun `getAll should return the empty list if no uploads are present`() {
        assertThat(uploadPersistenceManager.getAll().get()).apply {
            describedAs("Should return an empty list")
            isEmpty()
        }
    }

    @Test
    fun `getAll should return all uploads`() {
        val expected = listOf(
            insertUpload(null),
            insertUpload(UploadError.INSUFFICIENT_QUOTA)
        )

        val actual = uploadPersistenceManager.getAll().get()

        assertThat(actual).apply {
            describedAs("Should return saved uploads")
            containsOnlyElementsOf(expected)
        }
    }
}
