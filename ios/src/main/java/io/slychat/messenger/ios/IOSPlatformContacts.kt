package io.slychat.messenger.ios

import apple.contacts.*
import apple.contacts.c.Contacts
import apple.contacts.enums.CNContactFormatterStyle
import apple.contacts.enums.CNEntityType
import apple.foundation.NSArray
import apple.foundation.NSError
import apple.foundation.NSNotificationCenter
import apple.foundation.NSOperationQueue
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

    private var contactsObserver: apple.protocol.NSObject? = null

    override val contactsUpdated: Observable<Unit>
        get() = contactsUpdateSubject

    init {
        registerForContactChanges()
    }

    private fun requestPermission(): Promise<Boolean, Exception> {
        val d = deferred<Boolean, Exception>()
        val contactStore = CNContactStore.alloc().init()

        contactStore.requestAccessForEntityTypeCompletionHandler(CNEntityType.CNEntityTypeContacts) { granted, error ->
            d.resolve(granted)
        }

        return d.promise
    }

    private fun registerForContactChanges() {
        val notificationCenter = NSNotificationCenter.defaultCenter()

        //won't receive these until permission check has been done on startup
        contactsObserver = notificationCenter.addObserverForNameObjectQueueUsingBlock(
            Contacts.CNContactStoreDidChangeNotification(),
            null,
            NSOperationQueue.mainQueue()
        ) {
            contactsUpdateSubject.onNext(Unit)
        }
    }

    private fun enumContacts(): List<PlatformContact> {
        val contactStore = CNContactStore.alloc().init()

        val keysToFetch = NSArray.arrayWithObjects(
            Contacts.CNContactEmailAddressesKey(),
            Contacts.CNContactPhoneNumbersKey(),
            CNContactFormatter.descriptorForRequiredKeysForStyle(CNContactFormatterStyle.FullName),
            null
        )

        val fetchRequest = CNContactFetchRequest.alloc().initWithKeysToFetch(keysToFetch)

        val fetchErrorPtr = PtrFactory.newObjectReference(NSError::class.java)

        val platformContacts = ArrayList<PlatformContact>()

        contactStore.enumerateContactsWithFetchRequestErrorUsingBlock(fetchRequest, fetchErrorPtr) { contact, stop ->
            val name = CNContactFormatter.stringFromContactStyle(contact, CNContactFormatterStyle.FullName)
            val emailAddresses = contact.emailAddresses().map { it.value() as String }
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