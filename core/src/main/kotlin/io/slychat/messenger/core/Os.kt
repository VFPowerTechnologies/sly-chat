package io.slychat.messenger.core

/**
 * Info about an operating system.
 *
 * @property name User-readable OS name.
 * @property version Version string.
 */
data class Os(val type: Type, val version: String) {
    enum class Type(val displayName: String, val isPosix: Boolean) {
        LINUX("Linux", true),
        WINDOWS("Windows", false),
        OSX("OSX", true),
        ANDROID("Android", false),
        UNKNOWN("Unknown", false)
    }

    val name: String = type.displayName
}