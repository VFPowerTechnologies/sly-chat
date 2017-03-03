package io.slychat.messenger.core.persistence.sqlite

import io.slychat.messenger.core.crypto.generateFileId
import io.slychat.messenger.core.files.RemoteFile
import io.slychat.messenger.core.persistence.DuplicateFilePathException
import io.slychat.messenger.core.persistence.FileListUpdate
import io.slychat.messenger.core.persistence.InvalidFileException
import io.slychat.messenger.core.randomRemoteFile
import io.slychat.messenger.core.randomUserMetadata
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import kotlin.test.*

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

    private fun insertFile(lastUpdateVersion: Long = 1): RemoteFile {
        val file = randomRemoteFile().copy(lastUpdateVersion = lastUpdateVersion)

        fileListPersistenceManager.addFile(file).get()

        return file
    }

    private fun getFile(id: String): RemoteFile {
        return assertNotNull(fileListPersistenceManager.getFile(id).get(), "File not found")
    }

    private fun assertRemoteUpdateExists(update: FileListUpdate, message: String) {
        assertThat(fileListPersistenceManager.getRemoteUpdates().get()).apply {
            describedAs(message)
            contains(update)
        }
    }

    private fun testGetFilesAt(path: String, includePending: Boolean): List<RemoteFile> {
        (0..1).forEach { insertFile() }

        return fileListPersistenceManager.getFilesAt(0, 100, includePending, path).get()
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

        val stored = getFile(file.id)
        assertEquals(file, stored, "Version in db differs from original")
    }

    @Test
    fun `addFile should throw if path already has a file`() {
        val file = insertFile()

        assertFailsWith(DuplicateFilePathException::class) {
            fileListPersistenceManager.addFile(file).get()
        }
    }

    @Test
    fun `addFile should be case insensitive when checking path for dups`() {
        val file = insertFile()

        val um = file.userMetadata
        val modified = file.copy(
            userMetadata = file.userMetadata.copy(
                directory = um.directory.toUpperCase(), fileName = um.fileName.toUpperCase()
            )
        )

        assertFailsWith(DuplicateFilePathException::class) {
            fileListPersistenceManager.addFile(modified).get()
        }
    }

    @Test
    fun `getAllFiles should omit pending files when told to`() {
        val existingFile = insertFile()

        insertFile(0)

        assertThat(fileListPersistenceManager.getAllFiles(0, 1000, false).get()).apply {
            describedAs("Should not contain pending files")
            containsOnly(existingFile)
        }
    }

    @Test
    fun `getAllFiles should include pending files when told to`() {
        val existingFile = insertFile()

        val pendingFile = insertFile(0)

        assertThat(fileListPersistenceManager.getAllFiles(0, 1000, true).get()).apply {
            describedAs("Should contain added files")
            containsOnly(existingFile, pendingFile)
        }
    }

    @Test
    fun `getAllFiles should include only given range of files`() {
        val files = listOf(
            insertFile(),
            insertFile()
        ).sortedBy { it.id }

        for (i in 0..1) {
            assertThat(fileListPersistenceManager.getAllFiles(i, 1, false).get()).apply {
                describedAs("Should contain only the range [$i, ${i + 1})")
                containsOnly(files[i])
            }
        }
    }

    @Test
    fun `getFilesAt should only return files in the specified directory`() {
        val d = "/path"
        val expected = randomRemoteFile(userMetadata = randomUserMetadata(directory = d)).copy(lastUpdateVersion = 1)

        fileListPersistenceManager.addFile(expected).get()

        assertThat(testGetFilesAt(d, false)).apply {
            describedAs("Should return only files at the given path")
            containsOnly(expected)
        }
    }

    @Test
    fun `getAllFiles should include only the given range of files`() {
        val d = "/path"

        val files = listOf(
            randomRemoteFile(userMetadata = randomUserMetadata(directory = d)).copy(lastUpdateVersion = 1),
            randomRemoteFile(userMetadata = randomUserMetadata(directory = d)).copy(lastUpdateVersion = 1)
        ).sortedBy { it.id }

        files.forEach { fileListPersistenceManager.addFile(it).get() }
        (0..1).forEach { insertFile() }

        for (i in 0..1) {
            assertThat(fileListPersistenceManager.getFilesAt(i, 1, false, d).get()).apply {
                describedAs("Should contain only the range [$i, ${i + 1})")
                containsOnly(files[i])
            }
        }
    }

    @Test
    fun `getFilesAt should return pending files if asked`() {
        val d = "/path"
        val expected = randomRemoteFile(userMetadata = randomUserMetadata(directory = d)).copy(lastUpdateVersion = 0)

        fileListPersistenceManager.addFile(expected).get()

        assertThat(testGetFilesAt(d, true)).apply {
            describedAs("Should return only pending files at the given path")
            containsOnly(expected)
        }
    }

    @Test
    fun `getFilesAt should ignore directory case`() {
        val d = "/path"
        val expected = randomRemoteFile(userMetadata = randomUserMetadata(directory = d)).copy(lastUpdateVersion = 1)

        fileListPersistenceManager.addFile(expected).get()

        assertThat(testGetFilesAt(d.toUpperCase(), false)).apply {
            describedAs("Should return only files at the given path")
            containsOnly(expected)
        }
    }

    @Test
    fun `mergeUpdates should update files`() {
        val file = insertFile()

        val updated = file.copy(lastUpdateVersion = 2)

        fileListPersistenceManager.mergeUpdates(listOf(updated), 2).get()

        assertEquals(updated, fileListPersistenceManager.getFile(updated.id).get(), "File not updated")
    }

    @Test
    fun `mergeUpdates should add new files`() {
        val file = randomRemoteFile()

        fileListPersistenceManager.mergeUpdates(listOf(file), 2).get()

        assertEquals(file, fileListPersistenceManager.getFile(file.id).get(), "File not added")
    }

    @Test
    fun `mergeUpdates should remove deleted files`() {
        val file = insertFile()

        fileListPersistenceManager.mergeUpdates(listOf(file.copy(isDeleted = true)), 2).get()

        assertNull(fileListPersistenceManager.getFile(file.id).get(), "File not deleted from table")
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
    fun `deleteFiles should mark file as deleted`() {
        val file = insertFile()

        fileListPersistenceManager.deleteFiles(listOf(file.id)).get()

        val remoteFile = getFile(file.id)

        assertTrue(remoteFile.isDeleted, "File not marked as deleted")
    }

    @Test
    fun `deleteFiles should throw if file doesn't exist`() {
        assertFailsWith(InvalidFileException::class) {
            fileListPersistenceManager.deleteFiles(listOf(generateFileId())).get()
        }
    }

    @Test
    fun `deleteFiles should create a delete remote update`() {
        val file = insertFile()

        fileListPersistenceManager.deleteFiles(listOf(file.id)).get()

        assertRemoteUpdateExists(FileListUpdate.Delete(file.id), "Should add a delete remote update")
    }

    @Test
    fun `deletefiles should override metadata update`() {
        val file = insertFile()

        val userMetadata = file.userMetadata.moveTo("/newDir", "newName")

        fileListPersistenceManager.updateMetadata(file.id, userMetadata).get()

        fileListPersistenceManager.deleteFiles(listOf(file.id)).get()

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

        fileListPersistenceManager.deleteFiles(listOf(file.id)).get()

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