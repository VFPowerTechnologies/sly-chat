package io.slychat.messenger.core.http.api.authentication

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyOrder
import io.slychat.messenger.core.AuthToken
import io.slychat.messenger.core.crypto.SerializedKeyVault
import io.slychat.messenger.core.persistence.AccountInfo

@JsonFormat(shape = JsonFormat.Shape.ARRAY)
@JsonPropertyOrder("id", "registrationId")
data class DeviceInfo(
    @JsonProperty("id")
    val id: Int,
    @JsonProperty("registrationId")
    val registrationId: Int
)

/**
 * @property otherDevices Current list of active devices for this account, excluding the current device. This excludes
 * pending devices, as we can't send messages to devices without registered prekeys.
 */
data class AuthenticationData(
    @JsonProperty("authToken")
    val authToken: AuthToken,

    @JsonProperty("keyVault")
    val keyVault: SerializedKeyVault,

    @JsonProperty("accountInfo")
    val accountInfo: AccountInfo,

    @JsonProperty("otherDevices")
    val otherDevices: List<DeviceInfo>
)

data class AuthenticationResponse(
    @JsonProperty("errorMessage")
    val errorMessage: String?,

    @JsonProperty("data")
    val authData: AuthenticationData?
) {
    @get:JsonIgnore
    val isSuccess: Boolean = errorMessage == null
}