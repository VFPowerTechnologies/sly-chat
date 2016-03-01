package com.vfpowertech.keytap.services

import rx.Observable
import rx.subjects.BehaviorSubject

/** */
class AlwaysOnNetworkStatusService : NetworkStatusService {
    override val updates: Observable<Boolean> = BehaviorSubject.create(true)
}