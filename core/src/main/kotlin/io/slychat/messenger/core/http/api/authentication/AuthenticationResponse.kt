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
    @param:JsonProperty("auth-token")
    @get:JsonProperty("auth-token")
    val authToken: AuthToken,

    @param:JsonProperty("key-vault")
    @get:JsonProperty("key-vault")
    val keyVault: SerializedKeyVault,

    @param:JsonProperty("account-info")
    @get:JsonProperty("account-info")
    val accountInfo: AccountInfo,

    @param:JsonProperty("otherDevices")
    @get:JsonProperty("otherDevices")
    val otherDevices: List<DeviceInfo>
)

data class AuthenticationResponse(
    @param:JsonProperty("error-message")
    @get:JsonProperty("error-message")
    val errorMessage: String?,

    @param:JsonProperty("data")
    @get:JsonProperty("data")
    val data: AuthenticationData?
) {
    @get:JsonIgnore
    val isSuccess: Boolean = errorMessage == null
}