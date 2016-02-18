package com.vfpowertech.keytap.core

class DevServerInsaneException(
    reason: String,
    cause: Throwable? = null) : RuntimeException("Dev server sanity check failed: $reason", cause)