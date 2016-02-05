package com.vfpowertech.keytap.core.crypto

/** Serialized form of CipherParams and HashParams. */
data class SerializedCryptoParams(val algorithmName: String, val params: Map<String, String>)