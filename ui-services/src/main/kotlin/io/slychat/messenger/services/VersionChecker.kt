package io.slychat.messenger.services

import rx.Observable

/**
 * Checks the
 */
interface VersionChecker {
    /** Emitted whenever a version check is performed. */
    val versionCheckResult: Observable<VersionCheckResult>

    fun init()

    fun shutdown()
}

