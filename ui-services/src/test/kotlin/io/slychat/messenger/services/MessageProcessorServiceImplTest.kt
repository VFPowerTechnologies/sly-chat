package io.slychat.messenger.services

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.currentTimestamp
import io.slychat.messenger.core.persistence.GroupPersistenceManager
import io.slychat.messenger.core.persistence.MessageInfo
import io.slychat.messenger.core.persistence.MessagePersistenceManager
import io.slychat.messenger.core.randomUUID
import io.slychat.messenger.testutils.KovenantTestModeRule
import io.slychat.messenger.testutils.testSubscriber
import nl.komponents.kovenant.Promise
import org.assertj.core.api.Assertions.assertThat
import org.junit.ClassRule
import org.junit.Test
import kotlin.test.assertEquals

class MessageProcessorServiceImplTest {
    companion object {
        @JvmField
        @ClassRule
        val kovenantTestMode = KovenantTestModeRule()
    }

    val contactsService: ContactsService = mock()
    val messagePersistenceManager: MessagePersistenceManager = mock()
    val groupPersistenceManager: GroupPersistenceManager = mock()

    fun createService(): MessageProcessorServiceImpl {
        whenever(messagePersistenceManager.addMessages(any(), any())).thenAnswer {
            @Suppress("UNCHECKED_CAST")
            val a = it.arguments[1] as Collection<MessageInfo>
            Promise.ofSuccess<Collection<MessageInfo>, Exception>(a)
        }

        return MessageProcessorServiceImpl(
            contactsService,
            messagePersistenceManager,
            groupPersistenceManager
        )
    }

    @Test
    fun `it should store newly received text messages`() {
        val service = createService()

        val m = TextMessage(currentTimestamp(), "m", null)

        val wrapper = SlyMessageWrapper(randomUUID(), TextMessageWrapper(m))

        service.processMessage(UserId(1), wrapper).get()

        verify(messagePersistenceManager).addMessages(any(), any())
    }

    @Test
    fun `it should emit new message events after storing new text messages`() {
        val service = createService()

        val m = TextMessage(currentTimestamp(), "m", null)

        val wrapper = SlyMessageWrapper(randomUUID(), TextMessageWrapper(m))

        val testSubscriber = service.newMessages.testSubscriber()

        val from = UserId(1)

        service.processMessage(from, wrapper).get()

        val bundles = testSubscriber.onNextEvents

        assertThat(bundles)
            .hasSize(1)
            .`as`("Bundle check")

        val bundle = bundles[0]

        assertEquals(bundle.userId, from, "Invalid user id")
    }
}