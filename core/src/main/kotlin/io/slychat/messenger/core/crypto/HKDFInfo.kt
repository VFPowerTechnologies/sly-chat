package io.slychat.messenger.core.crypto

import java.util.*

//just to provide a bit of type safety... don't wanna accidently swap info and key or something stupid
class HKDFInfo(infoString: String) {
    val raw: ByteArray = infoString.toByteArray(Charsets.UTF_8)

    override fun toString(): String {
        return "HKDFInfo(raw=${raw.toString(Charsets.UTF_8)})"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false

        other as HKDFInfo

        if (!Arrays.equals(raw, other.raw)) return false

        return true
    }

    override fun hashCode(): Int {
        return Arrays.hashCode(raw)
    }
}