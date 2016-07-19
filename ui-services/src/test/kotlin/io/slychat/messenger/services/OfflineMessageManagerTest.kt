package io.slychat.messenger.services

import com.nhaarman.mockito_kotlin.*
import io.slychat.messenger.core.SlyAddress
import io.slychat.messenger.core.currentTimestamp
import io.slychat.messenger.core.http.api.offline.OfflineMessagesAsyncClient
import io.slychat.messenger.core.http.api.offline.OfflineMessagesGetResponse
import io.slychat.messenger.core.http.api.offline.SerializedOfflineMessage
import io.slychat.messenger.core.persistence.Package
import io.slychat.messenger.core.randomUUID
import io.slychat.messenger.services.crypto.MockAuthTokenManager
import io.slychat.messenger.services.messaging.MessengerService
import io.slychat.messenger.testutils.KovenantTestModeRule
import io.slychat.messenger.testutils.TestException
import io.slychat.messenger.testutils.thenReturn
import nl.komponents.kovenant.deferred
import org.assertj.core.api.Assertions
import org.junit.ClassRule
import org.junit.Test
import rx.subjects.BehaviorSubject
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OfflineMessageManagerTest {
    companion object {
        @JvmField
        @ClassRule
        val kovenantTestMode = KovenantTestModeRule()
    }

    val networkAvailable: BehaviorSubject<Boolean> = BehaviorSubject.create()

    val offlineMessagesClient: OfflineMessagesAsyncClient = mock()
    val messengerService: MessengerService = mock()

    fun randomPayload(): String = randomUUID()

    fun randomSerializedOfflineMessages(): List<SerializedOfflineMessage> {
        return (0..1).map {
            SerializedOfflineMessage(
                SlyAddress(randomUserId(), 1),
                currentTimestamp(),
                randomPayload()
            )
        }
    }

    fun createManager(isOnline: Boolean = false): OfflineMessageManager {
        networkAvailable.onNext(isOnline)

        whenever(offlineMessagesClient.clear(any(), any())).thenReturn(Unit)
        whenever(messengerService.addOfflineMessages(any())).thenReturn(Unit)

        return OfflineMessageManager(
            networkAvailable,
            offlineMessagesClient,
            messengerService,
            MockAuthTokenManager()
        )
    }

    fun setGetResponse(serialized: List<SerializedOfflineMessage>): String {
        val range = "1:2"
        val response = OfflineMessagesGetResponse(range, serialized)
        whenever(offlineMessagesClient.get(any())).thenReturn(response)
        return range
    }

    fun assertPackages(serialized: List<SerializedOfflineMessage>, packages: List<Package>) {
        val original = packages.map { SerializedOfflineMessage(it.id.address, it.timestamp, it.payload) }

        Assertions.assertThat(serialized)
            .containsAll(original)
            .`as`("Package check")
    }

    @Test
    fun `it should queue a fetch when network is offline`() {
        val manager = createManager()

        manager.fetch()

        verify(offlineMessagesClient, never()).get(any())
    }

    @Test
    fun `it should do nothing if no offline messages are available`() {
        val manager = createManager(true)

        setGetResponse(emptyList())

        manager.fetch()

        verify(messengerService, never()).addOfflineMessages(any())
    }

    @Test
    fun `it should start a fetch if one was queued once the network becomes available`() {
        val manager = createManager()

        val serialized = randomSerializedOfflineMessages()

        setGetResponse(serialized)

        manager.fetch()

        networkAvailable.onNext(true)

        verify(messengerService).addOfflineMessages(capture {
            assertPackages(serialized, it)
        })
    }

    @Test
    fun `it should not do anything if another request is already active`() {
        val manager = createManager(true)

        val d = deferred<OfflineMessagesGetResponse, Exception>()

        whenever(offlineMessagesClient.get(any())).thenAnswer {
            d.promise
        }

        manager.fetch()
        manager.fetch()

        d.resolve(OfflineMessagesGetResponse("1:2", randomSerializedOfflineMessages()))

        verify(messengerService, times(1)).addOfflineMessages(any())
    }

    @Test
    fun `it should do nothing if no request is queued once the network becomes available`() {
        val manager = createManager()

        networkAvailable.onNext(true)

        verify(offlineMessagesClient, never()).get(any())
    }

    @Test
    fun `it should unregister from networkAvailable on shutdown`() {
        var unsubscribed  = false

        val manager = OfflineMessageManager(
            networkAvailable.doOnUnsubscribe { unsubscribed = true },
            offlineMessagesClient,
            messengerService,
            MockAuthTokenManager()
        )

        manager.shutdown()

        assertTrue(unsubscribed, "Didn't unsubscribe")
    }

    @Test
    fun `it should call clear if messenger service store succeeds`() {
        val manager = createManager(true)

        val range = setGetResponse(randomSerializedOfflineMessages())

        manager.fetch()

        verify(offlineMessagesClient).clear(any(), capture {
            assertEquals(range, it.range, "Range doesn't match")
        })
    }

    @Test
    fun `it should not call clear if messenger service store fails`() {
        val manager = createManager(true)

        whenever(messengerService.addOfflineMessages(any())).thenReturn(TestException())

        setGetResponse(randomSerializedOfflineMessages())

        manager.fetch()

        verify(offlineMessagesClient, never()).clear(any(), any())
    }
}