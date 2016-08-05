package io.slychat.messenger.services.contacts

import rx.Observable
import rx.Scheduler
import rx.subjects.PublishSubject
import java.util.concurrent.TimeUnit

class DebounceScheduler(
    timeout: Long,
    timeUnit: TimeUnit,
    scheduler: Scheduler
) : SyncScheduler {
    private val scheduledEventSubject = PublishSubject.create<Unit>()

    override val scheduledEvent: Observable<Unit> = scheduledEventSubject.debounce(timeout, timeUnit, scheduler)

    override fun schedule() {
        scheduledEventSubject.onNext(Unit)
    }
}