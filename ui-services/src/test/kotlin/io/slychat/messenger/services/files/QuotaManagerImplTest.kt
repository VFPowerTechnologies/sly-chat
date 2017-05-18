package io.slychat.messenger.services.files

import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import io.slychat.messenger.core.persistence.QuotaPersistenceManager
import io.slychat.messenger.core.randomQuota
import io.slychat.messenger.testutils.desc
import io.slychat.messenger.testutils.testSubscriber
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class QuotaManagerImplTest {
    private val persistenceManager = mock<QuotaPersistenceManager>()
    private val manager = QuotaManagerImpl(persistenceManager)

    @Test
    fun `it should not emit an event during init if no cached quota is found`() {
        val testSubscriber = manager.quota.testSubscriber()

        whenever(persistenceManager.retrieve()).thenReturn(null)

        manager.init()

        assertThat(testSubscriber.onNextEvents).desc("Should not emit anything") {
            isEmpty()
        }
    }

    @Test
    fun `it should emit an event during init if a cached quota is found`() {
        val testSubscriber = manager.quota.testSubscriber()

        val quota = randomQuota()
        whenever(persistenceManager.retrieve()).thenReturn(quota)

        manager.init()

        assertThat(testSubscriber.onNextEvents).desc("Should emit cached value") {
            containsOnly(quota)
        }
    }

    @Test
    fun `it should update cached quota when the quota is updated`() {
        val quota = randomQuota()

        manager.update(quota)

        verify(persistenceManager).store(quota)
    }
}