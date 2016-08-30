package io.slychat.messenger.core.relay

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import io.slychat.messenger.core.BuildConfig
import io.slychat.messenger.core.crypto.randomMessageId
import io.slychat.messenger.core.crypto.tls.SSLConfigurator
import io.slychat.messenger.core.currentTimestamp
import io.slychat.messenger.core.emptyByteArray
import io.slychat.messenger.core.randomUserCredentials
import io.slychat.messenger.core.relay.base.*
import io.slychat.messenger.testutils.testSubscriber
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import rx.schedulers.Schedulers
import rx.subjects.PublishSubject
import java.io.ByteArrayInputStream
import java.net.InetSocketAddress
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

class RelayClientImplTest {
    companion object {
        val caCert = CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(BuildConfig.caCert)) as X509Certificate

        //not actually used
        val dummySslConfigurator = SSLConfigurator(
            caCert,
            mock(),
            false,
            false,
            false
        )
    }

    fun createPongMessage(): RelayMessage {
        val header = Header(
            1,
            0,
            "",
            "",
            "",
            randomMessageId(),
            0,
            1,
            currentTimestamp(),
            CommandCode.SERVER_PONG
        )

        return RelayMessage(header, emptyByteArray())
    }

    fun createSuccessfulAuthMessage(): RelayMessage {
        val header = Header(
            1,
            0,
            "",
            "",
            "",
            "",
            0,
            1,
            currentTimestamp(),
            CommandCode.SERVER_REGISTER_SUCCESSFUL
        )

        return RelayMessage(header, emptyByteArray())
    }

    @Test
    fun `it should update client server clock diff time when sending and receiving keep alive messages`() {
        val relayConnector = mock<RelayConnector>()

        val relayEvents = PublishSubject.create<RelayConnectionEvent>()
        whenever(relayConnector.connect(any(), any())).thenReturn(relayEvents)

        val credentials = randomUserCredentials()
        val client = RelayClientImpl(
            relayConnector,
            Schedulers.immediate(),
            InetSocketAddress("127.0.0.1", 2153),
            credentials,
            dummySslConfigurator
        )

        val testSubscriber = client.clockDifference.testSubscriber()

        client.connect()

        val relayConnection = mock<RelayConnection>()
        relayEvents.onNext(RelayConnectionEstablished(relayConnection))

        relayEvents.onNext(createSuccessfulAuthMessage())

        client.sendPing()

        relayEvents.onNext(createPongMessage())

        assertThat(testSubscriber.onNextEvents).apply {
            `as`("Should emit a time difference event")
            hasSize(1)
        }
    }
}