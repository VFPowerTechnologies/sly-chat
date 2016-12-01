package io.slychat.messenger.ios

import apple.contacts.*
import apple.contacts.c.Contacts
import apple.contacts.enums.CNContactFormatterStyle
import apple.contacts.enums.CNEntityType
import apple.foundation.NSError
import apple.foundation.NSMutableArray
import io.slychat.messenger.core.PlatformContact
import io.slychat.messenger.services.PlatformContacts
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.deferred
import nl.komponents.kovenant.functional.map
import org.moe.natj.general.ptr.impl.PtrFactory
import org.slf4j.LoggerFactory
import rx.Observable
import rx.subjects.PublishSubject
import java.util.*

class IOSPlatformContacts : PlatformContacts {
    companion object {
        //force loading for casts to work properly
        init {
            Class.forName(CNLabeledValue::class.java.name)
            Class.forName(CNPhoneNumber::class.java.name)
        }
    }

    private val log = LoggerFactory.getLogger(javaClass)

    private val contactsUpdateSubject = PublishSubject.create<Unit>()

    override val contactsUpdated: Observable<Unit>
        get() = contactsUpdateSubject

    private fun requestPermission(): Promise<Boolean, Exception> {
        val d = deferred<Boolean, Exception>()
        val contactStore = CNContactStore.alloc().init()

        contactStore.requestAccessForEntityTypeCompletionHandler(CNEntityType.CNEntityTypeContacts) { granted, error ->
            d.resolve(granted)
        }

        return d.promise
    }

    private fun enumContacts(): List<PlatformContact> {
        val contactStore = CNContactStore.alloc().init()

        //make kotlin happy
        @Suppress("UNCHECKED_CAST")
        val keysToFetch = NSMutableArray.alloc().init() as NSMutableArray<Any>
        keysToFetch.add(Contacts.CNContactEmailAddressesKey())
        keysToFetch.add(Contacts.CNContactPhoneNumbersKey())
        keysToFetch.add(CNContactFormatter.descriptorForRequiredKeysForStyle(CNContactFormatterStyle.FullName))

        val fetchRequest = CNContactFetchRequest.alloc().initWithKeysToFetch(keysToFetch)

        val fetchErrorPtr = PtrFactory.newObjectReference(NSError::class.java)

        val platformContacts = ArrayList<PlatformContact>()

        contactStore.enumerateContactsWithFetchRequestErrorUsingBlock(fetchRequest, fetchErrorPtr) { contact, stop ->
            val name = CNContactFormatter.stringFromContactStyle(contact, CNContactFormatterStyle.FullName)
            val emailAddresses = contact.emailAddresses().map { it.value() as String }
            //TODO format phonenumbers
            val phoneNumbers = contact.phoneNumbers().map { (it.value() as CNPhoneNumber).stringValue() }

            platformContacts.add(
                PlatformContact(name, emailAddresses, phoneNumbers)
            )
        }

        val fetchError = fetchErrorPtr.get()
        if (fetchError != null)
            log.warn("enumerateContactsWithFetchRequest:error:block failed: {}", fetchError.description())

        return platformContacts
    }

    override fun fetchContacts(): Promise<List<PlatformContact>, Exception> {
        return requestPermission() map { granted ->
            if (granted)
                enumContacts()
            else
                emptyList<PlatformContact>()
        }
    }
}