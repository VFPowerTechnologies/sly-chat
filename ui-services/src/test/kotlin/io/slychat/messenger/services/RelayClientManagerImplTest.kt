package io.slychat.messenger.services

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import io.slychat.messenger.core.randomUserCredentials
import io.slychat.messenger.core.relay.AuthenticationFailure
import io.slychat.messenger.core.relay.RelayClient
import io.slychat.messenger.core.relay.RelayClientEvent
import io.slychat.messenger.core.relay.RelayClientState
import org.junit.Before
import org.junit.Test
import rx.schedulers.Schedulers
import rx.subjects.PublishSubject

class RelayClientManagerImplTest {
    val relayClientFactory: RelayClientFactory = mock()
    val relayClient: RelayClient = mock()

    val relayClientEvents: PublishSubject<RelayClientEvent> = PublishSubject.create()

    @Before
    fun before() {
        whenever(relayClientFactory.createClient(any())).thenReturn(relayClient)

        whenever(relayClient.state).thenReturn(RelayClientState.DISCONNECTED)

        whenever(relayClient.events).thenReturn(relayClientEvents)
    }

    @Test
    fun `it should reset itself to offline mode when an AuthenticationFailure occurs`() {
        val relayClientManager = RelayClientManagerImpl(
            Schedulers.immediate(),
            relayClientFactory
        )

        val userCredentials = randomUserCredentials()
        relayClientManager.connect(userCredentials)

        relayClientEvents.onNext(AuthenticationFailure())

        //throws an exception if relayClient != null
        relayClientManager.connect(userCredentials)
    }
}