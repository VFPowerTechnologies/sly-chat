package io.slychat.messenger.core.persistence.sqlite

import io.slychat.messenger.core.crypto.generateFileId
import io.slychat.messenger.core.files.RemoteFile
import io.slychat.messenger.core.persistence.FileListUpdate
import io.slychat.messenger.core.persistence.InvalidFileException
import io.slychat.messenger.core.randomRemoteFile
import io.slychat.messenger.core.randomUserMetadata
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SQLiteFileListPersistenceManagerTest {
    companion object {
        @JvmStatic
        @BeforeClass
        fun loadLibrary() {
            SQLitePreKeyPersistenceManager::class.java.loadSQLiteLibraryFromResources()
        }
    }

    private lateinit var persistenceManager: SQLitePersistenceManager
    private lateinit var fileListPersistenceManager: SQLiteFileListPersistenceManager

    private fun insertFile(): RemoteFile {
        val file = randomRemoteFile()

        fileListPersistenceManager.addFile(file).get()

        return file
    }

    private fun getFile(id: String): RemoteFile {
        return assertNotNull(fileListPersistenceManager.getFileInfo(id).get(), "File not found")
    }

    private fun assertRemoteUpdateExists(update: FileListUpdate, message: String) {
        assertThat(fileListPersistenceManager.getRemoteUpdates().get()).apply {
            describedAs(message)
            contains(update)
        }
    }

    @Before
    fun before() {
        persistenceManager = SQLitePersistenceManager(null, null)
        persistenceManager.init()

        fileListPersistenceManager = SQLiteFileListPersistenceManager(persistenceManager)
    }

    @After
    fun after() {
        persistenceManager.shutdown()
    }

    @Test
    fun `getVersion should return the current version`() {
        assertEquals(0, fileListPersistenceManager.getVersion().get(), "Invalid version")
    }
    
    @Test
    fun `addFile should add a new file`() {
        val file = randomRemoteFile()

        fileListPersistenceManager.addFile(file).get()

        assertThat(fileListPersistenceManager.getAllFiles(0, 1000).get()).apply {
            describedAs("Should contain added files")
            containsOnly(file)
        }
    }

    @Test
    fun `mergeUpdates should update files`() {
        val file = insertFile()

        val updated = file.copy(lastUpdateVersion = 2)

        fileListPersistenceManager.mergeUpdates(listOf(updated), 2).get()

        assertEquals(updated, fileListPersistenceManager.getFileInfo(updated.id).get(), "File not updated")
    }

    @Test
    fun `mergeUpdates should add new files`() {
        val file = randomRemoteFile()

        fileListPersistenceManager.mergeUpdates(listOf(file), 2).get()

        assertEquals(file, fileListPersistenceManager.getFileInfo(file.id).get(), "File not added")
    }

    @Test
    fun `mergeUpdates should update list version`() {
        val latestVersion = 2L

        fileListPersistenceManager.mergeUpdates(listOf(randomRemoteFile()), latestVersion).get()

        assertEquals(latestVersion, fileListPersistenceManager.getVersion().get(), "Version not updated")
    }

    @Test
    fun `mergeUpdates should not generate remote updates`() {
        val latestVersion = 2L

        val file = insertFile()
        val updates = listOf(randomRemoteFile(), file.copy(userMetadata = file.userMetadata.rename("newName")))

        fileListPersistenceManager.mergeUpdates(updates, latestVersion).get()

        assertThat(fileListPersistenceManager.getRemoteUpdates().get()).apply {
            describedAs("Should not create remote updates")
            isEmpty()
        }
    }

    @Test
    fun `deleteFile should mark file as deleted`() {
        val file = insertFile()

        fileListPersistenceManager.deleteFile(file.id).get()

        val remoteFile = getFile(file.id)

        assertTrue(remoteFile.isDeleted, "File not marked as deleted")
    }

    @Test
    fun `deleteFile should throw if file doesn't exist`() {
        assertFailsWith(InvalidFileException::class) {
            fileListPersistenceManager.deleteFile(generateFileId()).get()
        }
    }

    @Test
    fun `deleteFile should create a delete remote update`() {
        val file = insertFile()

        fileListPersistenceManager.deleteFile(file.id).get()

        assertRemoteUpdateExists(FileListUpdate.Delete(file.id), "Should add a delete remote update")
    }

    @Test
    fun `deletefile should override metadata update`() {
        val file = insertFile()

        val userMetadata = file.userMetadata.moveTo("/newDir", "newName")

        fileListPersistenceManager.updateMetadata(file.id, userMetadata).get()

        fileListPersistenceManager.deleteFile(file.id).get()

        assertRemoteUpdateExists(FileListUpdate.Delete(file.id), "Should add a delete remote update")
    }

    @Test
    fun `updateMetadata should update the file metadata`() {
        val file = insertFile()

        val userMetadata = file.userMetadata.moveTo("/newDir", "newName")

        fileListPersistenceManager.updateMetadata(file.id, userMetadata).get()

        val updated = getFile(file.id)

        assertEquals(userMetadata, updated.userMetadata, "Metadata not updated")
    }

    @Test
    fun `updateMetadata should throw if file doesn't exist`() {
        assertFailsWith(InvalidFileException::class) {
            fileListPersistenceManager.updateMetadata(generateFileId(), randomUserMetadata()).get()
        }
    }

    @Test
    fun `updateMetadata should create a remote update`() {
        val file = insertFile()

        val userMetadata = file.userMetadata.moveTo("/newDir", "newName")

        fileListPersistenceManager.updateMetadata(file.id, userMetadata).get()

        assertRemoteUpdateExists(FileListUpdate.MetadataUpdate(file.id, userMetadata), "Should add an update metadata remote update")
    }

    //not that you'd normally do this...
    @Test
    fun `updateMetadata should override deletion update`() {
        val file = insertFile()

        fileListPersistenceManager.deleteFile(file.id).get()

        val userMetadata = file.userMetadata.moveTo("/newDir", "newName")

        fileListPersistenceManager.updateMetadata(file.id, userMetadata).get()

        assertRemoteUpdateExists(FileListUpdate.MetadataUpdate(file.id, userMetadata), "Should override existing delete update")
    }

    @Test
    fun `multiple updateMetadata calls should retain the last update`() {
        val file = insertFile()

        val userMetadata = file.userMetadata.moveTo("/newDir", "newName")

        fileListPersistenceManager.updateMetadata(file.id, file.userMetadata.moveTo("/oldDir", "oldName"))

        fileListPersistenceManager.updateMetadata(file.id, userMetadata).get()

        assertRemoteUpdateExists(FileListUpdate.MetadataUpdate(file.id, userMetadata), "Should override existing metadata update")
    }
}