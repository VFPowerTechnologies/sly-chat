package io.slychat.messenger.core.persistence

import nl.komponents.kovenant.Promise
import org.whispersystems.libsignal.SignalProtocolAddress
import org.whispersystems.libsignal.state.SessionRecord

/** Manages signal-protocol SessionRecord objects. */
interface SignalSessionPersistenceManager {
    fun containsSession(address: SignalProtocolAddress): Promise<Boolean, Exception>

    fun deleteSession(address: SignalProtocolAddress): Promise<Unit, Exception>

    /**
     * Returns all devices for the given name which have existing sessions.
     *
     * Note that unlike Signal, we don't have the concept of a master device, thus return all devices (ie: we include deviceId=1)
     */
    fun getSubDeviceSessions(name: String): Promise<List<Int>, Exception>

    fun deleteAllSessions(name: String): Promise<Unit, Exception>

    fun loadSession(address: SignalProtocolAddress): Promise<SessionRecord?, Exception>

    fun storeSession(address: SignalProtocolAddress, record: SessionRecord): Promise<Unit, Exception>
}