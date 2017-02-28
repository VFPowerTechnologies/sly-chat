package io.slychat.messenger.core.integration.web

import io.slychat.messenger.core.AuthToken
import io.slychat.messenger.core.SlyAddress
import io.slychat.messenger.core.UserCredentials
import io.slychat.messenger.core.http.JavaHttpClient
import io.slychat.messenger.core.http.get
import io.slychat.messenger.core.persistence.sqlite.JSONMapper
import org.junit.Assume
import java.net.ConnectException

val serverBaseUrl = "http://localhost:8000"
val fileServerBaseUrl = "http://localhost:9000"

//some common dummy values
//this is md5('')
val emptyMd5 = "d41d8cd98f00b204e9800998ecf8427e"
val defaultRegistrationId = 12345

fun isDevServerRunning() {
    try {
        val response = JavaHttpClient().get("$serverBaseUrl/dev")
        if (response.code == 404)
            throw ServerDevModeDisabledException()
    }
    catch (e: ConnectException) {
        Assume.assumeTrue(false)
    }
}

fun isDevFileServerRunning(): FileServerDevResponse {
    return try {
        val response = JavaHttpClient().get("$fileServerBaseUrl/dev")
        if (response.code == 404)
            throw ServerDevModeDisabledException()

        JSONMapper.mapper.readValue(response.body, FileServerDevResponse::class.java)
    }
    catch (e: ConnectException) {
        //never returns
        Assume.assumeTrue(false)
        throw e
    }
}

fun SiteUser.getUserCredentials(authToken: AuthToken, deviceId: Int = DEFAULT_DEVICE_ID): UserCredentials {
    return UserCredentials(
        SlyAddress(id, deviceId),
        authToken
    )
}

fun GeneratedSiteUser.getUserCredentials(authToken: AuthToken, deviceId: Int = DEFAULT_DEVICE_ID): UserCredentials {
    return user.getUserCredentials(authToken, deviceId)
}

fun DevClient.newAuthUser(userManagement: SiteUserManagement): AuthUser {
    val user = userManagement.injectNewSiteUser()
    val username = user.user.email
    val deviceId = addDevice(username, defaultRegistrationId, DeviceState.ACTIVE)
    val authToken = createAuthToken(username, deviceId)

    return AuthUser(user, deviceId, user.getUserCredentials(authToken, deviceId))
}