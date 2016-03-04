package com.vfpowertech.keytap.core.http.api.prekeys

import com.vfpowertech.keytap.core.crypto.axolotl.UserPreKeySet
import com.vfpowertech.keytap.core.crypto.generateNewKeyVault
import com.vfpowertech.keytap.core.crypto.generatePrekeys
import com.vfpowertech.keytap.core.crypto.hexify
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PreKeyUtilsTest {
    val password = "test"
    val keyVault = generateNewKeyVault(password)
    val username = "test-user"

    fun assertKeySetEquals(expected: UserPreKeySet, got: UserPreKeySet) {
        //implements equals
        assertEquals(got.identityKey, expected.identityKey, "Identity keys differ")
        //the rest don't

        assertEquals(expected.signedPreKey.id, expected.signedPreKey.id, "Signed PreKey IDs differ")
        assertTrue(Arrays.equals(expected.signedPreKey.serialize(), expected.signedPreKey.serialize()), "Signed PreKeys differ")

        assertEquals(expected.oneTimePreKey.id, expected.oneTimePreKey.id, "One-time PreKey IDs differ")
        assertTrue(Arrays.equals(expected.oneTimePreKey.serialize(), expected.oneTimePreKey.serialize()), "Signed PreKeys differ")
    }


    @Test
    fun `userPreKeySetFromRetrievalResponse should properly deserialize keys from a response`() {
        val preKeyBundle = generatePrekeys(keyVault.identityKeyPair, 1, 1, 1)
        val signedPreKey = preKeyBundle.signedPreKey
        val oneTimePreKey = preKeyBundle.oneTimePreKeys.first()
        val response = PreKeyRetrievalResponse(
            null,
            username,
            SerializedPreKeySet(
                keyVault.fingerprint,
                preKeyBundle.signedPreKey.serialize().hexify(),
                preKeyBundle.oneTimePreKeys.first().serialize().hexify()
            )
        )

        val expected = UserPreKeySet(keyVault.identityKeyPair.publicKey, signedPreKey, oneTimePreKey)

        val got = userPreKeySetFromRetrieveResponse(response)
        assertNotNull(got)
        assertKeySetEquals(expected, got!!)
    }
}