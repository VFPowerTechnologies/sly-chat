package io.slychat.messenger.services.ui

import com.vfpowertech.jsbridge.processor.annotations.JSToJavaGenerate
import nl.komponents.kovenant.Promise

@JSToJavaGenerate("FeedbackService")
interface UIFeedbackService {
    fun submitFeedback(feedbackText: String): Promise<Unit, Exception>
}