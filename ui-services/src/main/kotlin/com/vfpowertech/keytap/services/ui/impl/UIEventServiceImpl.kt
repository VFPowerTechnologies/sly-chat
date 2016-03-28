package com.vfpowertech.keytap.services.ui.impl

import com.vfpowertech.keytap.services.UIEvent
import com.vfpowertech.keytap.services.ui.UIEventService
import rx.Observable
import rx.subjects.PublishSubject

class UIEventServiceImpl : UIEventService {
    private val eventsSubject = PublishSubject.create<UIEvent>()
    override val events: Observable<UIEvent> = eventsSubject

    override fun dispatchEvent(event: UIEvent) {
        eventsSubject.onNext(event)
    }
}