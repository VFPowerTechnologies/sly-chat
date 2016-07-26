package io.slychat.messenger.core.http.api.contacts

import io.slychat.messenger.core.crypto.generateNewKeyVault
import io.slychat.messenger.core.persistence.AllowedMessageLevel
import io.slychat.messenger.core.persistence.RemoteContactUpdate
import io.slychat.messenger.core.randomUserId
import org.junit.Test
import kotlin.test.assertEquals

class ContactsUtilsTest {
    companion object {
        val password = "test"
        val keyVault = generateNewKeyVault(password)
    }

    @Test
    fun `decryptRemoteContactEntries should be able to decrypt the output of encryptRemoteContactEntries`() {
        val updates = listOf(
            RemoteContactUpdate(randomUserId(), AllowedMessageLevel.ALL)
        )

        val encrypted = encryptRemoteContactEntries(keyVault, updates)

        val decrypted = decryptRemoteContactEntries(keyVault, encrypted)

        assertEquals(updates, decrypted, "Updates don't match")
    }
}