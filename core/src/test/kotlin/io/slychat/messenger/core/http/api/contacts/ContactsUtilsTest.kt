package io.slychat.messenger.core.http.api.contacts

import io.slychat.messenger.core.crypto.generateNewKeyVault
import io.slychat.messenger.core.persistence.AddressBookUpdate
import io.slychat.messenger.core.persistence.AllowedMessageLevel
import io.slychat.messenger.core.persistence.GroupMembershipLevel
import io.slychat.messenger.core.randomGroupId
import io.slychat.messenger.core.randomGroupName
import io.slychat.messenger.core.randomUserId
import io.slychat.messenger.core.randomUserIds
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
            AddressBookUpdate.Contact(randomUserId(), AllowedMessageLevel.ALL),
            AddressBookUpdate.Group(randomGroupId(), randomGroupName(), randomUserIds(), GroupMembershipLevel.JOINED)
        )

        val encrypted = encryptRemoteAddressBookEntries(keyVault, updates)

        val decrypted = decryptRemoteAddressBookEntries(keyVault, encrypted)

        assertEquals(updates, decrypted, "Updates don't match")
    }
}