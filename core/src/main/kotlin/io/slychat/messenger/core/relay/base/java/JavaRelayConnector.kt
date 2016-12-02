package io.slychat.messenger.core.relay.base.java

import io.slychat.messenger.core.crypto.tls.SSLConfigurator
import io.slychat.messenger.core.relay.base.RelayConnectionEvent
import io.slychat.messenger.core.relay.base.RelayConnector
import rx.Observable
import java.net.InetSocketAddress

class JavaRelayConnector : RelayConnector {
    override fun connect(address: InetSocketAddress, sslConfigurator: SSLConfigurator): Observable<RelayConnectionEvent> {
        return Observable.create<RelayConnectionEvent>({ subscriber ->
            val connectionManager = ConnectionManager(
                RelaySocketConnector(address, sslConfigurator),
                subscriber,
                RelayClientMessageHandler(),
                RelayServerMessageHandler(),
                ReaderWriterFactoryImpl()
            )

            val t = Thread(connectionManager)
            t.isDaemon = true
            t.run()
        })
    }
}