package com.vfpowertech.keytap.core.persistence

import com.fasterxml.jackson.annotation.JsonProperty
import com.vfpowertech.keytap.core.randomUUID
import org.whispersystems.libsignal.util.KeyHelper

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
            InstallationData(generateInstallationId(), KeyHelper.generateRegistrationId(false))
    }
}