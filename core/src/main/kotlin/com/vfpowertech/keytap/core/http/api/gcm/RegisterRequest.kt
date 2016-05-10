package com.vfpowertech.keytap.core.http.api.gcm

data class RegisterRequest(
    val token: String,
    val installationId: String
)