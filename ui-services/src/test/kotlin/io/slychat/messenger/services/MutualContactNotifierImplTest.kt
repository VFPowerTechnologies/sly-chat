package io.slychat.messenger.services

import com.nhaarman.mockito_kotlin.*
import io.slychat.messenger.core.persistence.AllowedMessageLevel
import io.slychat.messenger.core.randomContactInfo
import io.slychat.messenger.services.contacts.ContactEvent
import io.slychat.messenger.services.contacts.ContactUpdate
import io.slychat.messenger.services.messaging.MessengerService
import io.slychat.messenger.testutils.thenResolve
import org.junit.Before
import org.junit.Test
import rx.subjects.PublishSubject

class MutualContactNotifierImplTest {
    val contactEvents: PublishSubject<ContactEvent> = PublishSubject.create()
    val messengerService: MessengerService = mock()

    val mutualContactNotifier = MutualContactNotifierImpl(contactEvents, messengerService)

    @Before
    fun before() {
        whenever(messengerService.notifyContactAdd(any())).thenResolve(Unit)
    }

    @Test
    fun `it should process added contacts where the message level is ALL`() {
        val contactInfo = randomContactInfo(AllowedMessageLevel.ALL)

        contactEvents.onNext(ContactEvent.Added(listOf(contactInfo), false))

        verify(messengerService).notifyContactAdd(listOf(contactInfo.id))
    }

    @Test
    fun `it should ignore added contacts where the message level is not ALL`() {
        val contactInfo = randomContactInfo(AllowedMessageLevel.GROUP_ONLY)

        contactEvents.onNext(ContactEvent.Added(listOf(contactInfo), false))

        verify(messengerService, never()).notifyContactAdd(any())
    }

    @Test
    fun `it should ignore contact updates when the new message level is ALL but the old message level was already ALL`() {
        val contactInfo = randomContactInfo(AllowedMessageLevel.ALL)

        val contactUpdate = ContactUpdate(contactInfo, contactInfo)

        contactEvents.onNext(ContactEvent.Updated(listOf(contactUpdate), false))

        verify(messengerService, never()).notifyContactAdd(any())
    }

    @Test
    fun `it should process contact updates when the new message level is ALL and the old message level was not ALL`() {
        val contactInfo = randomContactInfo(AllowedMessageLevel.GROUP_ONLY)

        val contactUpdate = ContactUpdate(contactInfo, contactInfo.copy(allowedMessageLevel = AllowedMessageLevel.ALL))

        contactEvents.onNext(ContactEvent.Updated(listOf(contactUpdate), false))

        verify(messengerService).notifyContactAdd(listOf(contactInfo.id))
    }

    @Test
    fun `it should ignore contact updates when fromSync is true`() {
        val contactInfo = randomContactInfo(AllowedMessageLevel.GROUP_ONLY)

        val contactUpdate = ContactUpdate(contactInfo, contactInfo.copy(allowedMessageLevel = AllowedMessageLevel.ALL))

        contactEvents.onNext(ContactEvent.Updated(listOf(contactUpdate), true))

        verify(messengerService, never()).notifyContactAdd(any())
    }

    @Test
    fun `it should ignore contact adds when fromSync is true`() {
        val contactInfo = randomContactInfo(AllowedMessageLevel.ALL)

        contactEvents.onNext(ContactEvent.Added(listOf(contactInfo), true))

        verify(messengerService, never()).notifyContactAdd(any())
    }
}