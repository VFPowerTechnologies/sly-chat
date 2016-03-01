package com.vfpowertech.keytap.services

import rx.Observable

interface NetworkStatusService {
    /**
     * Should only send changes when the value changes.
     * Should send the current status to new subscribers.
     * Registered observers must be called on the main thread.
     */
    val updates: Observable<Boolean>
}