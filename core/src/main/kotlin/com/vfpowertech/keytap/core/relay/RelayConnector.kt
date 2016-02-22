package com.vfpowertech.keytap.core.relay

import rx.Observable
import java.net.InetSocketAddress

/** Factory for creating connections to a relay server. */
interface RelayConnector {
    /**
     * The returned observable functions as follows:
     *
     * Once a subscription is established, a connection to the remote server is attempted.
     * If this fails, onError is called.
     * If this succeeds, onNext is called with RelayConnectionEstablished
     *
     * When either the server is disconnected, or ServerConnection.disconnect is called, onNext will receive
     * a RelayConnectionLost message, followed by onComplete.
     */
    fun connect(address: InetSocketAddress): Observable<RelayConnectionEvent>
}