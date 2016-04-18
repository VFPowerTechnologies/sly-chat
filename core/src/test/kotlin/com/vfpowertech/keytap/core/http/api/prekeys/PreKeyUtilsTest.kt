package com.vfpowertech.keytap.core.http.api.prekeys

import com.vfpowertech.keytap.core.UserId
import com.vfpowertech.keytap.core.crypto.generateNewKeyVault
import com.vfpowertech.keytap.core.crypto.generatePrekeys
import com.vfpowertech.keytap.core.crypto.hexify
import com.vfpowertech.keytap.core.crypto.signal.UserPreKeySet
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PreKeyUtilsTest {
    val password = "test"
    val keyVault = generateNewKeyVault(password)
    val userId = UserId(1)

    fun assertKeySetEquals(expected: UserPreKeySet, got: UserPreKeySet) {
        //(Signed)PreKeyRecord don't implement equals
        assertEquals(expected.signedPreKey.id, got.signedPreKey.id, "Signed PreKey IDs differ")
        assertTrue(Arrays.equals(expected.signedPreKey.serialize(), got.signedPreKey.serialize()), "Signed PreKeys differ")

        assertEquals(expected.oneTimePreKey.id, got.oneTimePreKey.id, "One-time PreKey IDs differ")
        assertTrue(Arrays.equals(expected.oneTimePreKey.serialize(), got.oneTimePreKey.serialize()), "Signed PreKeys differ")
    }

    @Test
    fun `userPreKeySetFromRetrievalResponse should properly deserialize keys from a response`() {
        val preKeyBundle = generatePrekeys(keyVault.identityKeyPair, 1, 1, 1)
        val signedPreKey = preKeyBundle.signedPreKey
        val oneTimePreKey = preKeyBundle.oneTimePreKeys.first()
        val response = PreKeyRetrievalResponse(
            null,
            userId,
            SerializedPreKeySet(
                keyVault.identityKeyPair.serialize().hexify(),
                preKeyBundle.signedPreKey.serialize().hexify(),
                preKeyBundle.oneTimePreKeys.first().serialize().hexify()
            )
        )

        val expected = UserPreKeySet(signedPreKey, oneTimePreKey)

        val got = userPreKeySetFromRetrieveResponse(response)
        assertNotNull(got)
        assertKeySetEquals(expected, got!!)
    }
}