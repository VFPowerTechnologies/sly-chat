package io.slychat.messenger.services

import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.persistence.AllowedMessageLevel
import io.slychat.messenger.core.persistence.ContactInfo
import io.slychat.messenger.services.contacts.ContactEvent
import io.slychat.messenger.services.contacts.ContactUpdate
import io.slychat.messenger.services.messaging.MessengerService
import org.slf4j.LoggerFactory
import rx.Observable
import rx.Subscription

//TODO add a setting to disable this behavior
class MutualContactNotifierImpl(
    contactEvents: Observable<ContactEvent>,
    private val messengerService: MessengerService
) : MutualContactNotifier {
    private val log = LoggerFactory.getLogger(javaClass)

    private var subscription: Subscription? = null

    init {
        subscription = contactEvents.subscribe { onContactEvent(it)  }
    }

    private fun onContactEvent(event: ContactEvent) {
        when (event) {
            is ContactEvent.Added -> if (event.fromSync == false) processNewContacts(event.contacts)
            is ContactEvent.Updated -> if (event.fromSync == false) processUpdatedContacts(event.contacts)
        }
    }

    private fun notifyContacts(userIds: Collection<UserId>) {
        log.debug("Sending added notifications to: {}", userIds)

        messengerService.notifyContactAdd(userIds) fail {
            log.error("Unable to send notification for contacts {}: {}", userIds, it.message, it)
        }
    }

    private fun processUpdatedContacts(contacts: List<ContactUpdate>) {
        val added = contacts.filter { it.new.allowedMessageLevel == AllowedMessageLevel.ALL && it.old.allowedMessageLevel != AllowedMessageLevel.ALL }
        if (added.isEmpty())
            return

        notifyContacts(added.map { it.new.id })
    }

    private fun processNewContacts(contacts: List<ContactInfo>) {
        val added = contacts.filter { it.allowedMessageLevel == AllowedMessageLevel.ALL }
        if (added.isEmpty())
            return

        notifyContacts(added.map { it.id })
    }

    override fun init() {}

    override fun shutdown() {
        subscription?.unsubscribe()
        subscription = null
    }
}