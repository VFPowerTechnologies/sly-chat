package com.vfpowertech.keytap.core.persistence

/** An attempt to add an existing contact was made. */
class DuplicateContactException(val email: String) : RuntimeException("Attempt to add a duplicate contact: $email")