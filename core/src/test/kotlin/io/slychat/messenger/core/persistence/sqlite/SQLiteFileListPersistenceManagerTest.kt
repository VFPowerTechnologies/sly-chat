package io.slychat.messenger.core.persistence.sqlite

import io.slychat.messenger.core.randomRemoteFile
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import kotlin.test.assertEquals

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

        Assertions.assertThat(fileListPersistenceManager.getAllFiles(0, 1000).get()).apply {
            describedAs("Should contain added files")
            containsOnly(file)
        }
    }

    @Test
    fun `updateFile should update the corresponding file`() {
        val file = randomRemoteFile()

        fileListPersistenceManager.addFile(file).get()

        val update = file.copy(isDeleted = true, fileMetadata = null)

        fileListPersistenceManager.updateFile(update).get()

        assertThat(fileListPersistenceManager.getFileInfo(update.id).get()).apply {
            describedAs("Message not updated")
            isEqualToComparingFieldByField(update)
        }
    }

    @Test
    fun `mergeUpdates should update files`() {
        val file = randomRemoteFile()

        fileListPersistenceManager.addFile(file).get()

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
        val latestVersion = 2

        fileListPersistenceManager.mergeUpdates(listOf(randomRemoteFile()), latestVersion).get()

        assertEquals(latestVersion, fileListPersistenceManager.getVersion().get(), "Version not updated")
    }
}