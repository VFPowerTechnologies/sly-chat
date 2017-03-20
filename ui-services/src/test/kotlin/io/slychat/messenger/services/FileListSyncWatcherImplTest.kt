package io.slychat.messenger.services

import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.never
import com.nhaarman.mockito_kotlin.verify
import io.slychat.messenger.core.persistence.FileListMergeResults
import io.slychat.messenger.core.randomDownload
import io.slychat.messenger.core.randomQuota
import io.slychat.messenger.core.randomUpload
import io.slychat.messenger.services.files.FileListSyncEvent
import io.slychat.messenger.services.files.FileListSyncResult
import io.slychat.messenger.services.files.TransferEvent
import io.slychat.messenger.services.files.TransferState
import io.slychat.messenger.services.messaging.MessengerService
import org.junit.Test
import rx.subjects.PublishSubject

class FileListSyncWatcherImplTest {
    private val syncEvents = PublishSubject.create<FileListSyncEvent>()
    private val transferEvents = PublishSubject.create<TransferEvent>()
    private val messengerService: MessengerService = mock()

    private fun newWatcher(): FileListSyncWatcherImpl {
        return FileListSyncWatcherImpl(syncEvents, transferEvents, messengerService)
    }

    private fun assertBroadcast() {
        verify(messengerService).broadcastFileListSync()
    }

    private fun assertNoBroadcast() {
        verify(messengerService, never()).broadcastFileListSync()
    }

    @Test
    fun `it should send a broadcast message when a result contains pushed updates`() {
        val watcher = newWatcher()

        syncEvents.onNext(FileListSyncEvent.Result(FileListSyncResult(1, FileListMergeResults.empty, 1, randomQuota())))

        assertBroadcast()
    }

    @Test
    fun `it should not send a broadcast message when a result doesn't contain pushed updates`() {
        val watcher = newWatcher()

        syncEvents.onNext(FileListSyncEvent.Result(FileListSyncResult(0, FileListMergeResults.empty, 1, randomQuota())))

        assertNoBroadcast()
    }

    @Test
    fun `it should ignore Begin events`() {
        val watcher = newWatcher()

        syncEvents.onNext(FileListSyncEvent.Begin())

        assertNoBroadcast()
    }

    @Test
    fun `it should ignore End events`() {
        val watcher = newWatcher()

        syncEvents.onNext(FileListSyncEvent.End(false))

        assertNoBroadcast()
    }

    @Test
    fun `it should send a broadcast message for upload complete events`() {
        val watcher = newWatcher()

        transferEvents.onNext(TransferEvent.StateChanged(randomUpload(), TransferState.COMPLETE))

        assertBroadcast()
    }

    @Test
    fun `it should ignore non-complete upload states`() {
        val watcher = newWatcher()

        transferEvents.onNext(TransferEvent.StateChanged(randomUpload(), TransferState.ACTIVE))

        assertNoBroadcast()
    }

    @Test
    fun `it should ignore download complete events`() {
        val watcher = newWatcher()

        transferEvents.onNext(TransferEvent.StateChanged(randomDownload(), TransferState.COMPLETE))

        assertNoBroadcast()
    }
}