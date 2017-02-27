package io.slychat.messenger.core.integration.web

import io.slychat.messenger.core.UserCredentials

class AuthUser(
    val user: GeneratedSiteUser,
    val deviceId: Int,
    val userCredentials: UserCredentials
)