package io.slychat.messenger.core.persistence.sqlite

import io.slychat.messenger.core.crypto.generateFileId
import io.slychat.messenger.core.files.RemoteFile
import io.slychat.messenger.core.persistence.DirEntry
import io.slychat.messenger.core.persistence.DuplicateFilePathException
import io.slychat.messenger.core.persistence.FileListUpdate
import io.slychat.messenger.core.persistence.InvalidFileException
import io.slychat.messenger.core.randomRemoteFile
import io.slychat.messenger.core.randomUserMetadata
import io.slychat.messenger.testutils.desc
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

    private fun insertFile(lastUpdateVersion: Long = 1, directory: String? = null): RemoteFile {
        val userMetadata = randomUserMetadata(directory = directory)
        val file = randomRemoteFile(userMetadata = userMetadata).copy(lastUpdateVersion = lastUpdateVersion)

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

    private fun assertDirIndexContains(paths: List<Pair<String, String>>, message: String? = null) {
        paths.forEach {
            val (parent, sub) = it
            assertThat(fileListPersistenceManager.getDirectoriesAt(parent).get()).desc(message ?: "Should contain subdirs") {
                containsOnly(sub)
            }
        }
    }

    private fun assertDirIndexNotContains(paths: List<Pair<String, String>>, message: String? = null) {
        require(paths.isNotEmpty())
        paths.forEach {
            val (parent, sub) = it
            assertThat(fileListPersistenceManager.getDirectoriesAt(parent).get()).desc(message ?: "Should not contain subdirs") {
                doesNotContain(sub)
            }
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
    fun `addFile should update directory index`() {
        val userMetadata = randomUserMetadata(directory = "/a/b")
        val file = randomRemoteFile(userMetadata = userMetadata)

        fileListPersistenceManager.addFile(file).get()

        assertDirIndexContains(listOf(
            "/" to "a",
            "/a" to "b"
        ))
    }

    @Test
    fun `getAllFiles should omit pending files when told to`() {
        val existingFile = insertFile()

        insertFile(0)

        assertThat(fileListPersistenceManager.getFiles(0, 1000, false).get()).apply {
            describedAs("Should not contain pending files")
            containsOnly(existingFile)
        }
    }

    @Test
    fun `getAllFiles should include pending files when told to`() {
        val existingFile = insertFile()

        val pendingFile = insertFile(0)

        assertThat(fileListPersistenceManager.getFiles(0, 1000, true).get()).apply {
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
            assertThat(fileListPersistenceManager.getFiles(i, 1, false).get()).apply {
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

        val result = fileListPersistenceManager.mergeUpdates(listOf(file), 2).get()

        assertEquals(file, fileListPersistenceManager.getFile(file.id).get(), "File not added")

        assertThat(result.added).desc("Should include the added file") { containsOnly(file) }

        assertThat(result.deleted).desc("Should be empty") { isEmpty() }

        assertThat(result.updated).desc("Should be empty") { isEmpty() }
    }

    @Test
    fun `mergeUpdates should update the directory index when adding new files`() {
        val userMetadata = randomUserMetadata(directory = "/a")
        val file = randomRemoteFile(userMetadata = userMetadata)

        fileListPersistenceManager.mergeUpdates(listOf(file), 2).get()

        assertDirIndexContains(listOf(
            "/" to "a"
        ))
    }

    @Test
    fun `mergeUpdates should remove deleted files`() {
        val file = insertFile()
        val updated = file.copy(isDeleted = true)

        val result = fileListPersistenceManager.mergeUpdates(listOf(updated), 2).get()

        assertNull(fileListPersistenceManager.getFile(file.id).get(), "File not deleted from table")

        assertThat(result.added).desc("Should be empty") { isEmpty() }

        assertThat(result.deleted).desc("Should include the deleted file") { containsOnly(updated) }

        assertThat(result.updated).desc("Should be empty") { isEmpty() }
    }

    @Test
    fun `mergeUpdates should update the directory index when a file is removed`() {
        val file = insertFile(directory = "/a")
        val updated = file.copy(isDeleted = true)

        fileListPersistenceManager.mergeUpdates(listOf(updated), 2).get()

        assertDirIndexNotContains(listOf(
            "/" to "a"
        ))
    }

    @Test
    fun `mergeUpdates should update existing files`() {
        val file = insertFile()
        val updated = file.copy(userMetadata = file.userMetadata.rename("test-file"))

        val result = fileListPersistenceManager.mergeUpdates(listOf(updated), 2).get()

        val fromDb = getFile(file.id)
        assertEquals(updated, fromDb, "File not updated in db")

        assertThat(result.added).desc("Should be empty") { isEmpty() }

        assertThat(result.deleted).desc("Should be empty") { isEmpty() }

        assertThat(result.updated).desc("Should contain the updated file") { containsOnly(updated) }
    }

    @Test
    fun `mergeUpdates should update the directory index when a file path is modified`() {
        val file = insertFile(directory = "/a")
        val updated = file.copy(userMetadata = file.userMetadata.moveTo("/b"))

        fileListPersistenceManager.mergeUpdates(listOf(updated), 2).get()

        assertDirIndexNotContains(listOf("/" to "a"), "Should not contain the old path")
        assertDirIndexContains(listOf("/" to "b"), "Should contain the new path")
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
    fun `remoteRemoteUpdates should remove the given updates`() {
        val file = insertFile()
        val file2 = insertFile()

        fileListPersistenceManager.deleteFiles(listOf(file.id, file2.id)).get()

        fileListPersistenceManager.removeRemoteUpdates(listOf(file.id)).get()

        assertThat(fileListPersistenceManager.getRemoteUpdates().get()).apply {
            describedAs("Should remove the given updates")
            containsOnly(FileListUpdate.Delete(file2.id))
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
    fun `deleteFiles should return the updated files`() {
        val file = insertFile()

        val deleted = fileListPersistenceManager.deleteFiles(listOf(file.id)).get()

        assertThat(deleted).desc("Should return updated files") {
            containsOnly(file.copy(isDeleted = true, fileMetadata = null))
        }
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
    fun `deleteFiles should override metadata update`() {
        val file = insertFile()

        val userMetadata = file.userMetadata.moveTo("/newDir", "newName")

        fileListPersistenceManager.updateMetadata(file.id, userMetadata).get()

        fileListPersistenceManager.deleteFiles(listOf(file.id)).get()

        assertRemoteUpdateExists(FileListUpdate.Delete(file.id), "Should add a delete remote update")
    }

    @Test
    fun `deleteFiles should update the directory index`() {
        val userMetadata = randomUserMetadata(directory = "/a/b")
        val file = randomRemoteFile(userMetadata = userMetadata)

        fileListPersistenceManager.addFile(file).get()
        fileListPersistenceManager.deleteFiles(listOf(file.id)).get()

        assertDirIndexNotContains(listOf(
            "/" to "a",
            "/a" to "b"
        ))
    }

    @Test
    fun `deleteFiles should not remove index entries if some files still use it`() {
        val userMetadata = randomUserMetadata(directory = "/a/b")
        val file = randomRemoteFile(userMetadata = userMetadata)
        val file2 = randomRemoteFile(userMetadata = userMetadata.copy(fileName = "2"))

        fileListPersistenceManager.addFile(file).get()
        fileListPersistenceManager.addFile(file2).get()
        fileListPersistenceManager.deleteFiles(listOf(file.id)).get()

        assertDirIndexContains(listOf(
            "/" to "a",
            "/a" to "b"
        ))
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

    @Test
    fun `getFileCount should return the number of files`() {
        insertFile()
        insertFile()

        assertEquals(2, fileListPersistenceManager.getFileCount().get(), "Invalid file list count")
    }

    @Test
    fun `getFileCount should return 0 when list is empty`() {
        assertEquals(0, fileListPersistenceManager.getFileCount().get(), "Invalid empty file list count")
    }

    @Test
    fun `getEntriesAt should return both files and directories`() {
        val file = insertFile(directory = "/")
        insertFile(directory = "/a")

        val expected = listOf(
            DirEntry.D("/a", "a"),
            DirEntry.F(file)
        )

        assertThat(fileListPersistenceManager.getEntriesAt(0, 100, "/").get()).desc("Should contain both files and directory entries") {
            containsAll(expected)
        }
    }

    @Test
    fun `getEntriesAt should handle startingAt being non-zero for joint file directory results`() {
        val file = insertFile(directory = "/")
        insertFile(directory = "/a")
        insertFile(directory = "/b")

        val expected = listOf(
            DirEntry.D("/b", "b"),
            DirEntry.F(file)
        )

        assertThat(fileListPersistenceManager.getEntriesAt(1, 2, "/").get()).desc("Should contain both files and directory entries") {
            containsAll(expected)
        }
    }

    @Test
    fun `getEntriesAt should not return files when directories max out the count`() {
        val file = insertFile(directory = "/")
        insertFile(directory = "/a")
        val file2 = insertFile(directory = "/")

        val dir = DirEntry.D("/a", "a")

        assertThat(fileListPersistenceManager.getEntriesAt(0, 1, "/").get()).desc("Should only contain the first item") {
            containsOnly(dir)
        }

        assertThat(fileListPersistenceManager.getEntriesAt(1, 1, "/").get()).desc("Should only contain the second item") {
            containsOnly(DirEntry.F(file))
        }

        assertThat(fileListPersistenceManager.getEntriesAt(2, 1, "/").get()).desc("Should only contain the third item") {
            containsOnly(DirEntry.F(file2))
        }
    }
}