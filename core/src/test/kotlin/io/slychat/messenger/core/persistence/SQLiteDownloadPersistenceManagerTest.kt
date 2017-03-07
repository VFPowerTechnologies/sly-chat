package io.slychat.messenger.core.persistence

import io.slychat.messenger.core.crypto.generateDownloadId
import io.slychat.messenger.core.files.RemoteFile
import io.slychat.messenger.core.persistence.sqlite.FileUtils
import io.slychat.messenger.core.persistence.sqlite.SQLitePersistenceManager
import io.slychat.messenger.core.persistence.sqlite.SQLitePreKeyPersistenceManager
import io.slychat.messenger.core.persistence.sqlite.loadSQLiteLibraryFromResources
import io.slychat.messenger.core.randomDownload
import io.slychat.messenger.core.randomRemoteFile
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SQLiteDownloadPersistenceManagerTest {
    companion object {
        @JvmStatic
        @BeforeClass
        fun loadLibrary() {
            SQLitePreKeyPersistenceManager::class.java.loadSQLiteLibraryFromResources()
        }
    }

    private lateinit var persistenceManager: SQLitePersistenceManager
    private lateinit var downloadPersistenceManager: SQLiteDownloadPersistenceManager
    private val fileUtils = FileUtils()

    private fun insertFile(): RemoteFile {
        val file = randomRemoteFile()

        persistenceManager.syncRunQuery {
            fileUtils.insertFile(it, file)
        }

        return file
    }

    private fun insertDownloadFull(error: DownloadError? = null): DownloadInfo {
        val file = insertFile()
        val download = randomDownload(file.id, error = error)
        downloadPersistenceManager.add(download).get()

        return DownloadInfo(download, file)
    }

    private fun insertDownload(error: DownloadError? = null): Download {
        return insertDownloadFull(error).download
    }

    fun getDownload(downloadId: String): Download {
        return assertNotNull(downloadPersistenceManager.get(downloadId).get(), "Download not in db")
    }

    @Before
    fun before() {
        persistenceManager = SQLitePersistenceManager(null, null)
        persistenceManager.init()

        downloadPersistenceManager = SQLiteDownloadPersistenceManager(persistenceManager)
    }

    @After
    fun after() {
        persistenceManager.shutdown()
    }

    @Test
    fun `add should add a new download`() {
        val download = insertDownload()

        assertEquals(download, downloadPersistenceManager.get(download.id).get(), "Invalid db representation")
    }

    @Test
    fun `setState should update state`() {
        val download = insertDownload()

        downloadPersistenceManager.setState(download.id, DownloadState.COMPLETE).get()

        val updated = getDownload(download.id)

        assertEquals(DownloadState.COMPLETE, updated.state, "Completion status not updated")
    }

    @Test
    fun `setComplete should throw InvalidDownloadException if download doesn't exist`() {
        assertFailsWith(InvalidDownloadException::class) {
            downloadPersistenceManager.setState(generateDownloadId(), DownloadState.COMPLETE).get()
        }
    }

    @Test
    fun `setError should set a new error`() {
        val download = insertDownload()

        val error = DownloadError.NETWORK_ISSUE

        downloadPersistenceManager.setError(download.id, error).get()

        assertEquals(error, getDownload(download.id).error, "Error not updated")
    }

    @Test
    fun `setError should remove an error`() {
        val download = insertDownload(DownloadError.NETWORK_ISSUE)

        downloadPersistenceManager.setError(download.id, null).get()

        assertNull(getDownload(download.id).error, "Error not updated")
    }

    @Test
    fun `setError should throw InvalidDownloadException if download doesn't exist`() {
        assertFailsWith(InvalidDownloadException::class) {
            downloadPersistenceManager.setError(generateDownloadId(), DownloadError.NETWORK_ISSUE).get()
        }
    }

    @Test
    fun `getAll should return all stored downloads`() {
        val downloads = listOf(
            insertDownloadFull(),
            insertDownloadFull()
        )

        assertThat(downloadPersistenceManager.getAll().get()).apply {
            describedAs("Should return all downloads")
            containsAll(downloads)
        }
    }
}