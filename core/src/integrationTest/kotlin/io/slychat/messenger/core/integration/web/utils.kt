package io.slychat.messenger.core.integration.web

import io.slychat.messenger.core.AuthToken
import io.slychat.messenger.core.SlyAddress
import io.slychat.messenger.core.UserCredentials
import io.slychat.messenger.core.http.JavaHttpClient
import io.slychat.messenger.core.http.get
import org.junit.Assume
import java.net.ConnectException

val serverBaseUrl = "http://localhost:8000"
val fileServerBaseUrl = "http://localhost:9000"

//some common dummy values
//this is md5('')
val emptyMd5 = "d41d8cd98f00b204e9800998ecf8427e"
val defaultRegistrationId = 12345

private fun isServerRunning(baseUrl: String) {
    try {
        val response = JavaHttpClient().get("$baseUrl/dev")
        if (response.code == 404)
            throw ServerDevModeDisabledException()
    }
    catch (e: ConnectException) {
        Assume.assumeTrue(false)
    }
}


fun isDevServerRunning() {
    isServerRunning(serverBaseUrl)
}

fun isDevFileServerRunning() {
    isServerRunning(fileServerBaseUrl)
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
