package io.slychat.messenger.core.sentry

import io.slychat.messenger.core.currentTimestamp
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class SentryEventBuilderTest {
    private val builder = SentryEventBuilder(
        "logger",
        Thread.currentThread().name,
        LoggerLevel.INFO,
        currentTimestamp(),
        "Some message",
        "culprit"
    )

    @Test
    fun `withTag should include tag in output`() {
        builder.withTag("k", "v")

        val event = builder.build()
        assertThat(event.tags).apply {
            describedAs("Should contain the set tag")
            containsEntry("k", "v")
        }
    }

    @Test
    fun `withExtra should include extra in output`() {
        builder.withExtra("k", "v")

        val event = builder.build()
        assertThat(event.extra).apply {
            describedAs("Should contain the set extra context")
            containsEntry("k", "v")
        }
    }

    @Test
    fun `it should include the current thread name in the extra output`() {
        builder.withExtra("k", "v")

        val event = builder.build()
        assertThat(event.extra).apply {
            describedAs("Should contain the set extra context")
            containsEntry(SentryEvent.EXTRA_THREAD_NAME, builder.threadName)
        }
    }
}