package io.slychat.messenger.services

import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.never
import com.nhaarman.mockito_kotlin.verify
import io.slychat.messenger.core.persistence.FileListMergeResults
import io.slychat.messenger.core.randomQuota
import io.slychat.messenger.services.files.FileListSyncEvent
import io.slychat.messenger.services.files.StorageSyncResult
import io.slychat.messenger.services.messaging.MessengerService
import org.junit.Test
import rx.subjects.PublishSubject

class FileListSyncWatcherImplTest {
    private val syncEvents = PublishSubject.create<FileListSyncEvent>()
    private val messengerService: MessengerService = mock()

    private fun newWatcher(): FileListSyncWatcherImpl {
        return FileListSyncWatcherImpl(syncEvents, messengerService)
    }

    @Test
    fun `it should send a broadcast message when a result contains pushed updates`() {
        val watcher = newWatcher()

        syncEvents.onNext(FileListSyncEvent.Result(StorageSyncResult(1, FileListMergeResults.empty, 1, randomQuota())))

        verify(messengerService).broadcastFileListSync()
    }

    @Test
    fun `it should not send a broadcast message when a result doesn't contain pushed updates`() {
        val watcher = newWatcher()

        syncEvents.onNext(FileListSyncEvent.Result(StorageSyncResult(0, FileListMergeResults.empty, 1, randomQuota())))

        verify(messengerService, never()).broadcastFileListSync()
    }

    @Test
    fun `it should ignore Begin events`() {
        val watcher = newWatcher()

        syncEvents.onNext(FileListSyncEvent.Begin())

        verify(messengerService, never()).broadcastFileListSync()
    }

    @Test
    fun `it should ignore End events`() {
        val watcher = newWatcher()

        syncEvents.onNext(FileListSyncEvent.End(false))

        verify(messengerService, never()).broadcastFileListSync()
    }
}