package io.slychat.messenger.services

import io.slychat.messenger.core.persistence.AllowedMessageLevel
import io.slychat.messenger.core.persistence.ContactInfo
import io.slychat.messenger.services.contacts.ContactEvent
import io.slychat.messenger.services.contacts.ContactUpdate
import io.slychat.messenger.services.messaging.MessengerService
import rx.Observable
import rx.Subscription

class MutualContactNotifierImpl(
    contactEvents: Observable<ContactEvent>,
    private val messengerService: MessengerService
) : MutualContactNotifier {
    private var subscription: Subscription? = null

    init {
        subscription = contactEvents.subscribe { onContactEvent(it)  }
    }

    private fun onContactEvent(event: ContactEvent) {
        when (event) {
            is ContactEvent.Added -> processNewContacts(event.contacts)
            is ContactEvent.Updated -> processUpdatedContacts(event.contacts)
        }
    }

    private fun processUpdatedContacts(contacts: Set<ContactUpdate>) {
        val added = contacts.filter { it.new.allowedMessageLevel == AllowedMessageLevel.ALL && it.old.allowedMessageLevel != AllowedMessageLevel.ALL }
        if (added.isEmpty())
            return

        messengerService.notifyContactAdd(added.map { it.new.id })
    }

    private fun processNewContacts(contacts: Set<ContactInfo>) {
        val added = contacts.filter { it.allowedMessageLevel == AllowedMessageLevel.ALL }
        if (added.isEmpty())
            return

        messengerService.notifyContactAdd(added.map { it.id })
    }

    override fun init() {}

    override fun shutdown() {
        subscription?.unsubscribe()
        subscription = null
    }
}