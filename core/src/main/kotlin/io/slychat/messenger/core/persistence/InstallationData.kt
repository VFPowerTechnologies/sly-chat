package io.slychat.messenger.core.persistence

import com.fasterxml.jackson.annotation.JsonProperty
import io.slychat.messenger.core.randomRegistrationId
import io.slychat.messenger.core.randomUUID

data class InstallationData(
    @JsonProperty("installationId")
    val installationId: String,
    @JsonProperty("registrationId")
    val registrationId: Int
) {
    companion object {
        fun generateInstallationId(): String = randomUUID()

        /** Generate new installation data. */
        fun generate(): InstallationData =
            InstallationData(generateInstallationId(), randomRegistrationId())
    }
}