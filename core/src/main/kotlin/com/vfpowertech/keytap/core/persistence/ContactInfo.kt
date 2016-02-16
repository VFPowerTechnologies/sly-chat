package com.vfpowertech.keytap.core.persistence

data class ContactInfo(
    val email: String,
    val name: String,
    val phoneNumber: String?,
    val publicKey: String
)