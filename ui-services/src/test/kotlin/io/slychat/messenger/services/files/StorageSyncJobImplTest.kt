package io.slychat.messenger.services.files

import com.nhaarman.mockito_kotlin.*
import io.slychat.messenger.core.*
import io.slychat.messenger.core.crypto.generateFileId
import io.slychat.messenger.core.crypto.generateKey
import io.slychat.messenger.core.crypto.generateNewKeyVault
import io.slychat.messenger.core.files.RemoteFile
import io.slychat.messenger.core.files.encryptFileMetadata
import io.slychat.messenger.core.files.encryptUserMetadata
import io.slychat.messenger.core.http.api.storage.FileInfo
import io.slychat.messenger.core.http.api.storage.FileListResponse
import io.slychat.messenger.core.http.api.storage.StorageAsyncClient
import io.slychat.messenger.core.http.api.storage.UpdateResponse
import io.slychat.messenger.core.persistence.FileListPersistenceManager
import io.slychat.messenger.core.persistence.FileListUpdate
import io.slychat.messenger.testutils.KovenantTestModeRule
import io.slychat.messenger.testutils.thenResolve
import io.slychat.messenger.testutils.thenResolveUnit
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test
import kotlin.test.assertEquals

class StorageSyncJobImplTest {
    companion object {
        @JvmField
        @ClassRule
        val kovenantTestMode = KovenantTestModeRule()

        private val keyVault = generateNewKeyVault("test")
    }

    private val fileListPersistenceManager: FileListPersistenceManager = mock()
    private val storageClient: StorageAsyncClient = mock()
    private val syncJob = StorageSyncJobImpl(UserCredentials(randomSlyAddress(), randomAuthToken()), keyVault, fileListPersistenceManager, storageClient)

    private val currentVersion = 0L
    private val newVersion = 1L

    private fun generateTestFileInfo(): Pair<RemoteFile, FileInfo> {
        val um = randomUserMetadata().copy(fileKey = generateKey(128))
        val file = randomRemoteFile(userMetadata = um)

        val info = FileInfo(
            file.id,
            file.shareKey,
            file.isDeleted,
            file.lastUpdateVersion,
            1,
            2,
            encryptUserMetadata(keyVault, file.userMetadata),
            encryptFileMetadata(file.userMetadata, file.fileMetadata!!),
            file.remoteFileSize
        )

        return file to info
    }

    @Before
    fun before() {
        whenever(fileListPersistenceManager.getVersion()).thenResolve(currentVersion)
        whenever(fileListPersistenceManager.mergeUpdates(any(), any())).thenResolveUnit()
        whenever(fileListPersistenceManager.getRemoteUpdates()).thenResolve(emptyList())
        whenever(fileListPersistenceManager.removeRemoteUpdates(any())).thenResolveUnit()
        whenever(storageClient.getFileList(any(), any())).thenResolve(FileListResponse(0, emptyList()))
        whenever(storageClient.update(any(), any())).thenResolve(UpdateResponse(newVersion))
    }

    @Test
    fun `it should return the number of remote updates on push`() {
        val remoteUpdates = listOf(
            FileListUpdate.Delete(generateFileId())
        )

        whenever(fileListPersistenceManager.getRemoteUpdates()).thenResolve(remoteUpdates)

        val result = syncJob.run().get()

        assertEquals(1, result.remoteUpdatesPerformed, "Invalid update count")
    }

    @Test
    fun `it should remove the pushed updates on success`() {
        val remoteUpdates = listOf(
            FileListUpdate.Delete(generateFileId())
        )

        whenever(fileListPersistenceManager.getRemoteUpdates()).thenResolve(remoteUpdates)

        syncJob.run().get()

        verify(fileListPersistenceManager).removeRemoteUpdates(remoteUpdates.map { it.fileId })
    }

    @Test
    fun `it should not push if no remotes are available`() {
        whenever(fileListPersistenceManager.getRemoteUpdates()).thenResolve(emptyList())

        syncJob.run().get()

        verify(storageClient, never()).update(any(), any())
    }

    @Test
    fun `it should update the local file list with the results from a pull`() {
        val (file, info) = generateTestFileInfo()

        whenever(storageClient.getFileList(any(), eq(currentVersion))).thenResolve(FileListResponse(
            newVersion,
            listOf(info)
        ))

        syncJob.run().get()

        verify(fileListPersistenceManager).mergeUpdates(listOf(file), newVersion)
    }

    @Test
    fun `it should return the file list updates and the new version in the job results`() {
        val (file, info) = generateTestFileInfo()

        whenever(storageClient.getFileList(any(), eq(currentVersion))).thenResolve(FileListResponse(
            newVersion,
            listOf(info)
        ))

        val result = syncJob.run().get()

        assertEquals(newVersion, result.newListVersion, "Invalid version")
        assertThat(result.updates).apply {
            describedAs("Invalid update list")
            containsOnly(file)
        }
    }
}