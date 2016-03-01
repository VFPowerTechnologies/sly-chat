package com.vfpowertech.keytap.core.persistence

/** An attempt to update a non-existent contact was made */
class InvalidContactException(val email: String) : RuntimeException("Invalid contact: $email")