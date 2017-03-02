package io.slychat.messenger.core.http.api.contacts

import io.slychat.messenger.core.*
import io.slychat.messenger.core.crypto.generateNewKeyVault
import io.slychat.messenger.core.crypto.getRandomBits
import io.slychat.messenger.core.persistence.AddressBookUpdate
import io.slychat.messenger.core.persistence.AllowedMessageLevel
import io.slychat.messenger.core.persistence.GroupMembershipLevel
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ContactsUtilsTest {
    companion object {
        val password = "test"
        val keyVault = generateNewKeyVault(password)
    }

    @Test
    fun `decryptRemoteAddressBookEntries should be able to decrypt the output of encryptRemoteAddressBookEntries`() {
        val updates = listOf(
            AddressBookUpdate.Contact(randomUserId(), AllowedMessageLevel.ALL),
            AddressBookUpdate.Group(randomGroupId(), randomGroupName(), randomUserIds(), GroupMembershipLevel.JOINED)
        )

        val encrypted = encryptRemoteAddressBookEntries(keyVault, updates)

        val decrypted = decryptRemoteAddressBookEntries(keyVault, encrypted)

        assertEquals(updates, decrypted, "Updates don't match")
    }

    @Test
    fun `md5 should concat all its args`() {
        val a = getRandomBits(256)
        val b = getRandomBits(256)

        assertEquals(md5(a).hexify(), md5(a).hexify())
        assertNotEquals(md5(a).hexify(), md5(a, b).hexify())
    }
}