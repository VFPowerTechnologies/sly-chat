package io.slychat.messenger.services

import rx.Observable

/**
 * Checks the
 */
interface VersionChecker {
    /** Emitted whenever a version check is performed, if the version is out of date. */
    val versionOutOfDate: Observable<Unit>

    fun init()

    fun shutdown()
}

