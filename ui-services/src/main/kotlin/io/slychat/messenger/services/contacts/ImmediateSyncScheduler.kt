package io.slychat.messenger.services.contacts

import rx.Observable
import rx.subjects.PublishSubject

class ImmediateSyncScheduler : SyncScheduler {
    private val scheduledEventSubject = PublishSubject.create<Unit>()

    override val scheduledEvent: Observable<Unit> = scheduledEventSubject

    override fun schedule() {
        scheduledEventSubject.onNext(Unit)
    }
}