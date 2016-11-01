package io.slychat.messenger.core.sentry

import io.slychat.messenger.core.currentTimestamp
import org.assertj.core.api.Assertions
import org.junit.Test

class SentryEventBuilderTest {
    @Test
    fun `withTag should include tag in output`() {
        val builder = SentryEventBuilder(
            "logger",
            Thread.currentThread().name,
            LoggerLevel.INFO,
            currentTimestamp(),
            "Some message",
            "culprit"
        )
        
        builder.withTag("k", "v")

        val event = builder.build()
        Assertions.assertThat(event.tags).apply {
            `as`("Should contain the set tag")
            containsEntry("k", "v")
        }
    }
}