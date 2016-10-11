package io.slychat.messenger.core.http.api.prekeys

import com.fasterxml.jackson.databind.ObjectMapper
import io.slychat.messenger.core.crypto.generateNewKeyVault
import io.slychat.messenger.core.crypto.signal.generatePrekeys
import io.slychat.messenger.core.hexify
import org.junit.Test
import org.whispersystems.libsignal.state.PreKeyBundle
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PreKeyUtilsTest {
    val password = "test"
    val keyVault = generateNewKeyVault(password)

    fun assertBundleEquals(expected: PreKeyBundle, got: PreKeyBundle) {
        assertEquals(expected.registrationId, got.registrationId, "Registration IDs differ")
        assertEquals(expected.deviceId, got.deviceId, "Device IDs differ")

        //(Signed)PreKeyRecord don't implement equals
        assertEquals(expected.signedPreKeyId, got.signedPreKeyId, "Signed PreKey IDs differ")
        assertTrue(Arrays.equals(expected.signedPreKey.serialize(), got.signedPreKey.serialize()), "Signed PreKeys differ")
        assertTrue(Arrays.equals(expected.signedPreKeySignature, got.signedPreKeySignature), "Signed PreKeys signatures differ")

        assertEquals(expected.preKeyId, got.preKeyId, "One-time PreKey IDs differ")
        assertTrue(Arrays.equals(expected.preKey.serialize(), got.preKey.serialize()), "Signed PreKeys differ")

        assertTrue(Arrays.equals(expected.identityKey.serialize(), got.identityKey.serialize()), "Identity keys differ")
    }

    @Test
    fun `toPreKeyBundle should properly deserialize keys from a SerializedPreKeySet`() {
        val preKeyBundle = generatePrekeys(keyVault.identityKeyPair, 1, 1, 1)
        val signedPreKey = preKeyBundle.signedPreKey
        val oneTimePreKey = preKeyBundle.oneTimePreKeys.first()
        val registrationId = 12345
        val deviceId = 1
        val objectMapper = ObjectMapper()
        val serializedPreKeySet = SerializedPreKeySet(
            registrationId,
            keyVault.identityKeyPair.publicKey.serialize().hexify(),
            objectMapper.writeValueAsString(signedPreKey.toPublicData()),
            objectMapper.writeValueAsString(preKeyBundle.oneTimePreKeys.first().toPublicData())
        )

        val expected = PreKeyBundle(
                registrationId,
                deviceId,
                oneTimePreKey.id,
                oneTimePreKey.keyPair.publicKey,
                signedPreKey.id,
                signedPreKey.keyPair.publicKey,
                signedPreKey.signature,
                keyVault.identityKeyPair.publicKey
            )

        val got = serializedPreKeySet.toPreKeyBundle(deviceId)

        assertBundleEquals(expected, got)
    }
}