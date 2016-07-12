package io.slychat.messenger.services

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import io.slychat.messenger.core.SlyAddress
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.currentTimestamp
import io.slychat.messenger.core.persistence.MessagePersistenceManager
import io.slychat.messenger.core.persistence.Package
import io.slychat.messenger.core.persistence.PackageId
import io.slychat.messenger.core.randomUUID
import io.slychat.messenger.services.crypto.MessageCipherService
import io.slychat.messenger.testutils.KovenantTestModeRule
import io.slychat.messenger.testutils.thenReturn
import org.junit.ClassRule
import org.junit.Test
import rx.schedulers.Schedulers
import rx.subjects.PublishSubject
import kotlin.test.assertFailsWith

class MessageReceiverImplTest {
    companion object {
        @JvmField
        @ClassRule
        val kovenantTestMode = KovenantTestModeRule()
    }

    class TestException : Exception("Test exc")

    val messageProcessService: MessageProcessorService = mock()
    val messagePersistenceManager: MessagePersistenceManager = mock()
    val messageCipherService: MessageCipherService = mock()

    val decryptionResults: PublishSubject<DecryptionResult> = PublishSubject.create()

    fun createReceiver(): MessageReceiverImpl {
        val scheduler = Schedulers.immediate()

        whenever(messageCipherService.decryptedMessages).thenReturn(decryptionResults)

        return MessageReceiverImpl(
            scheduler,
            messageProcessService,
            messagePersistenceManager,
            messageCipherService
        )
    }

    @Test
    fun `it should notifier the submitter once the packages have been successfully queued`() {
        val receiver = createReceiver()

        val id = PackageId(SlyAddress(UserId(1), 1), randomUUID())
        val packages = listOf(Package(id, currentTimestamp(), "payload"))

        whenever(messagePersistenceManager.addToQueue(any<Collection<Package>>())).thenReturn(Unit)

        receiver.processPackages(packages).get()
    }

    @Test
    fun `it should propagate failures when package queue fails`() {
        val receiver = createReceiver()

        val id = PackageId(SlyAddress(UserId(1), 1), randomUUID())
        val packages = listOf(Package(id, currentTimestamp(), "payload"))

        whenever(messagePersistenceManager.addToQueue(any<Collection<Package>>())).thenReturn(TestException())

        assertFailsWith(TestException::class) {
            receiver.processPackages(packages).get()
        }
    }
}