package io.slychat.messenger.core.persistence

import com.fasterxml.jackson.annotation.JsonProperty
import io.slychat.messenger.core.crypto.ciphers.Key
import io.slychat.messenger.core.crypto.generateKey
import io.slychat.messenger.core.crypto.randomRegistrationId
import io.slychat.messenger.core.crypto.randomUUID

class InstallationData(
    @JsonProperty("installationId")
    val installationId: String,
    @JsonProperty("registrationId")
    val registrationId: Int,
    @JsonProperty("startupInfoKey")
    val startupInfoKey: Key
) {
    companion object {
        fun generateInstallationId(): String = randomUUID()

        /** Generate new installation data. */
        fun generate(): InstallationData =
            InstallationData(generateInstallationId(), randomRegistrationId(), generateKey(256))
    }
}