package io.slychat.messenger.core

/**
 * Info about an operating system.
 *
 * @property name User-readable OS name.
 * @property version Version string.
 */
data class Os(val type: Type, val version: String) {
    enum class Type(val displayName: String) {
        LINUX("Linux"),
        WINDOWS("Windows"),
        OSX("OSX"),
        ANDROID("Android"),
        UNKNOWN("Unknown")
    }

    val name: String = type.displayName
}