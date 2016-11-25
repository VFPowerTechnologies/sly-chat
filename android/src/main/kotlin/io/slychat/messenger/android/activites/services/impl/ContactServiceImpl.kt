package io.slychat.messenger.android.activites.services.impl

import android.support.v7.app.AppCompatActivity
import io.slychat.messenger.android.AndroidApp
import io.slychat.messenger.android.activites.services.ContactService
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.persistence.AllowedMessageLevel
import io.slychat.messenger.core.persistence.ContactInfo
import io.slychat.messenger.services.contacts.ContactEvent
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.map
import nl.komponents.kovenant.ui.successUi
import rx.Subscription

class ContactServiceImpl (activity: AppCompatActivity): ContactService {
    private val app = AndroidApp.get(activity)
    private val contactService = app.getUserComponent().contactsService

    private var contactListener: Subscription? = null
    private var contactUIListener: ((ContactEvent) -> Unit)? = null

    var contactList: MutableMap<UserId, ContactInfo> = mutableMapOf()

    override fun addContactListener (listener: ((ContactEvent) -> Unit)) {
        contactUIListener = listener
        contactListener = contactService.contactEvents.subscribe {
            handleContactEvent(it)
        }
    }

    override fun clearListeners () {
        contactUIListener = null
        contactListener?.unsubscribe()
    }

    override fun getContacts (): Promise<MutableMap<UserId, ContactInfo>, Exception> {
        contactList = mutableMapOf()
        return contactService.getAll() map { contacts ->
            contacts.forEach {
                contactList.put(it.id, it)
            }
            contactList
        }
    }

    override fun getContact (id: UserId): Promise<ContactInfo?, Exception> {
        if (contactList[id] != null)
            return Promise.ofSuccess(contactList[id])

        return contactService.get(id) map { contact ->
            if (contact != null) {
                contactList[contact.id] = contact
            }
            contact
        }
    }

    override fun deleteContact (contactInfo: ContactInfo): Promise<Boolean, Exception> {
        return contactService.removeContact(contactInfo)
    }

    override fun blockContact (id: UserId): Promise<Unit, Exception> {
        return contactService.block(id)
    }

    private fun handleContactEvent (event: ContactEvent) {
        when (event) {
            is ContactEvent.Added -> { handleNewContactAdded(event) }
            is ContactEvent.Blocked -> { handleContactBlocked(event) }
            is ContactEvent.Removed -> { handleContactRemoved(event) }
            is ContactEvent.Unblocked -> { handleContactUnblocked(event) }
            is ContactEvent.Updated -> { handleContactUpdated(event) }
        }

        notifyUi(event)
    }

    private fun notifyUi (event: ContactEvent) {
        contactUIListener?.invoke(event)
    }

    private fun handleContactBlocked (event: ContactEvent.Blocked) {
        val c = contactList[event.userId]
        if (c != null) {
            contactList[event.userId] = ContactInfo(c.id, c.email, c.name, AllowedMessageLevel.BLOCKED, c.publicKey)
        }
        else {
            contactService.get(event.userId) successUi {
                if (it != null)
                    contactList[event.userId] = it
            }
        }
    }

    private fun handleNewContactAdded (event: ContactEvent.Added) {
        event.contacts.forEach { c ->
            contactList[c.id] = c
        }
    }

    private fun handleContactRemoved (event: ContactEvent.Removed) {
        event.contacts.forEach { c ->
            contactList.remove(c.id)
        }
    }

    private fun handleContactUnblocked (event: ContactEvent.Unblocked) {
        val c = contactList[event.userId]
        if (c != null) {
            contactList[event.userId] = ContactInfo(c.id, c.email, c.name, AllowedMessageLevel.GROUP_ONLY, c.publicKey)
        }
        else {
            contactService.get(event.userId) successUi {
                if (it != null)
                    contactList[event.userId] = it
            }
        }
    }

    private fun handleContactUpdated (event: ContactEvent.Updated) {
        event.contacts.forEach { c ->
            contactList[c.new.id] = c.new
        }
    }

}