package io.slychat.messenger.services.files

import io.slychat.messenger.core.Quota
import rx.Observable
import rx.subjects.BehaviorSubject

class QuotaManagerImpl : QuotaManager {
    private val quotaSubject = BehaviorSubject.create<Quota>()

    override val quota: Observable<Quota>
        get() = quotaSubject

    override fun update(quota: Quota) {
        quotaSubject.onNext(quota)
    }
}