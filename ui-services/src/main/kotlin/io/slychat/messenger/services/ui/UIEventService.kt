package io.slychat.messenger.services.ui

import com.vfpowertech.jsbridge.processor.annotations.Exclude
import com.vfpowertech.jsbridge.processor.annotations.JSToJavaGenerate
import io.slychat.messenger.services.UIEvent
import rx.Observable

//using this as a simple solution to handling notifications for the moment, even if it's a bit iffy
/** Relays events from the ui to the underlying application. */
@JSToJavaGenerate("EventService")
interface UIEventService {
    @Exclude
    val events: Observable<UIEvent>

    fun dispatchEvent(event: UIEvent)
}
