package io.slychat.messenger.core.persistence

import com.fasterxml.jackson.annotation.JsonProperty
import io.slychat.messenger.core.BuildConfig
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
    val startupInfoKey: Key,
    @JsonProperty("lastRunVersion")
    val lastRunVersion: String
) {
    companion object {
        private fun generateInstallationId(): String = randomUUID()

        /** Generate new installation data. */
        fun generate(): InstallationData =
            InstallationData(generateInstallationId(), randomRegistrationId(), generateKey(256), BuildConfig.VERSION)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false

        other as InstallationData

        if (installationId != other.installationId) return false
        if (registrationId != other.registrationId) return false
        if (startupInfoKey != other.startupInfoKey) return false
        if (lastRunVersion != other.lastRunVersion) return false

        return true
    }

    override fun hashCode(): Int {
        var result = installationId.hashCode()
        result = 31 * result + registrationId
        result = 31 * result + startupInfoKey.hashCode()
        result = 31 * result + lastRunVersion.hashCode()
        return result
    }
}