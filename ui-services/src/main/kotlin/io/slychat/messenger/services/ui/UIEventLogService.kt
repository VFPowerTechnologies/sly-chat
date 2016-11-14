package io.slychat.messenger.services.ui

import com.vfpowertech.jsbridge.processor.annotations.JSToJavaGenerate
import io.slychat.messenger.services.ui.impl.UILogEvent
import nl.komponents.kovenant.Promise

@JSToJavaGenerate("EventLogService")
interface UIEventLogService {
    fun getSecurityEvents(startingAt: Int, count: Int): Promise<List<UILogEvent>, Exception>
}