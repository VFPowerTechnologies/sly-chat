package io.slychat.messenger.services

data class VersionCheckResult(
    val isLatest: Boolean,
    val latestVersion: String
)