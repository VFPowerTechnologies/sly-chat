package com.vfpowertech.keytap.core.persistence

import com.vfpowertech.keytap.core.UserId

/** An attempt to update a non-existent contact was made */
class InvalidContactException(val userId: UserId) : RuntimeException("Invalid contact: $userId")