package io.slychat.messenger.services.crypto

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.crypto.generateKeyPair
import io.slychat.messenger.core.crypto.identityKeyFingerprint
import io.slychat.messenger.core.persistence.AllowedMessageLevel
import io.slychat.messenger.core.persistence.ContactsPersistenceManager
import io.slychat.messenger.core.persistence.PreKeyPersistenceManager
import io.slychat.messenger.core.persistence.SignalSessionPersistenceManager
import io.slychat.messenger.core.randomContactInfo
import io.slychat.messenger.core.randomRegistrationId
import io.slychat.messenger.core.randomSignalAddress
import io.slychat.messenger.testutils.thenReturn
import io.slychat.messenger.testutils.thenReturnNull
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SQLiteSignalProtocolStoreTest {
    val identityKeyPair = generateKeyPair()
    val registrationId = randomRegistrationId()

    val signalSessionPersistenceManager: SignalSessionPersistenceManager = mock()
    val contactsPersistenceManager: ContactsPersistenceManager = mock()
    val preKeyPersistenceManager: PreKeyPersistenceManager = mock()

    val signalStore = SQLiteSignalProtocolStore(
        identityKeyPair,
        registrationId,
        signalSessionPersistenceManager,
        preKeyPersistenceManager,
        contactsPersistenceManager
    )

    @Test
    fun `isTrustedIdentity should return false for users not in the address book`() {
        val address = randomSignalAddress()

        whenever(contactsPersistenceManager.get(any<UserId>())).thenReturnNull()

        assertFalse(signalStore.isTrustedIdentity(address.name, identityKeyPair.publicKey))
    }

    @Test
    fun `isTrustedIdentity should return false if key does not match address book key`() {
        val address = randomSignalAddress()

        val contactInfo = randomContactInfo(AllowedMessageLevel.ALL)

        whenever(contactsPersistenceManager.get(any<UserId>())).thenReturn(contactInfo)

        assertFalse(signalStore.isTrustedIdentity(address.name, identityKeyPair.publicKey))
    }

    @Test
    fun `isTrustedIdentity should return true if key does match address book key`() {
        val address = randomSignalAddress()

        val contactInfo = randomContactInfo(AllowedMessageLevel.ALL).copy(
            publicKey = identityKeyFingerprint(identityKeyPair.publicKey)
        )

        whenever(contactsPersistenceManager.get(any<UserId>())).thenReturn(contactInfo)

        assertTrue(signalStore.isTrustedIdentity(address.name, identityKeyPair.publicKey))
    }
}