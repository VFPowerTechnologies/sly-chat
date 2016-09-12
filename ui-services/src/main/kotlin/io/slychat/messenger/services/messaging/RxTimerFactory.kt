package io.slychat.messenger.services.messaging

import rx.Observable
import rx.Scheduler
import java.util.concurrent.TimeUnit

open class RxTimerFactory(
    private val timerScheduler: Scheduler
) {
    open fun createTimer(delay: Long, timeUnit: TimeUnit): Observable<Long> {
        return Observable.timer(delay, timeUnit, timerScheduler).observeOn(timerScheduler)
    }
}