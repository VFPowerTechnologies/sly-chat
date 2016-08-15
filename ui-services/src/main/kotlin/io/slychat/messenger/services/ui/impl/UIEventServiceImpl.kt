package io.slychat.messenger.services.ui.impl

import io.slychat.messenger.services.UIEvent
import io.slychat.messenger.services.ui.UIEventService
import rx.Observable
import rx.subjects.PublishSubject

class UIEventServiceImpl : UIEventService {
    private val eventsSubject = PublishSubject.create<UIEvent>()
    override val events: Observable<UIEvent>
        get() = eventsSubject

    override fun dispatchEvent(event: UIEvent) {
        eventsSubject.onNext(event)
    }
}