package com.vfpowertech.keytap.core.persistence

import com.fasterxml.jackson.annotation.JsonProperty
import com.vfpowertech.keytap.core.randomUUID

data class InstallationData(@JsonProperty("installationId") val installationId: String) {
    companion object {
        fun generateInstallationId(): String = randomUUID()

        /** Generate new installation data. */
        fun generate(): InstallationData =
            InstallationData(generateInstallationId())
    }
}