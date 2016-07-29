package io.slychat.messenger.services.contacts

import com.google.i18n.phonenumbers.PhoneNumberUtil
import io.slychat.messenger.core.PlatformContact
import io.slychat.messenger.core.UserCredentials
import io.slychat.messenger.core.http.api.contacts.*
import io.slychat.messenger.core.mapToMap
import io.slychat.messenger.core.mapToSet
import io.slychat.messenger.core.persistence.*
import io.slychat.messenger.services.*
import io.slychat.messenger.services.auth.AuthTokenManager
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.bind
import nl.komponents.kovenant.functional.map
import org.slf4j.LoggerFactory
import java.util.*

class ContactSyncJobImpl(
    private val authTokenManager: AuthTokenManager,
    private val contactClient: ContactAsyncClient,
    private val addressBookClient: AddressBookAsyncClient,
    private val contactsPersistenceManager: ContactsPersistenceManager,
    private val groupPersistenceManager: GroupPersistenceManager,
    private val userLoginData: UserData,
    private val accountInfoPersistenceManager: AccountInfoPersistenceManager,
    private val platformContacts: PlatformContacts
) : ContactSyncJob {
    private val log = LoggerFactory.getLogger(javaClass)

    private fun getDefaultRegionCode(): Promise<String, Exception> {
        return accountInfoPersistenceManager.retrieve() map { getAccountRegionCode(it!!) }
    }

    private fun getPlatformContacts(defaultRegion: String): Promise<List<PlatformContact>, Exception> {
        return platformContacts.fetchContacts() map { contacts ->
            val phoneNumberUtil = PhoneNumberUtil.getInstance()

            val updated = contacts.map { contact ->
                val phoneNumbers = contact.phoneNumbers
                    .map { parsePhoneNumber(it, defaultRegion) }
                    .filter { it != null }
                    .map { phoneNumberUtil.format(it, PhoneNumberUtil.PhoneNumberFormat.E164).substring(1) }
                contact.copy(phoneNumbers = phoneNumbers)
            }

            log.debug("Platform contacts: {}", updated)

            updated
        }
    }

    private fun queryAndAddNewContacts(userCredentials: UserCredentials, missingContacts: List<PlatformContact>): Promise<Unit, Exception> {
        return if (missingContacts.isNotEmpty()) {
            contactClient.findLocalContacts(userCredentials, FindLocalContactsRequest(missingContacts)) bind { foundContacts ->
                log.debug("Found platform contacts: {}", foundContacts)

                contactsPersistenceManager.add(foundContacts.contacts.map { it.toCore(AllowedMessageLevel.ALL) }) map { Unit }
            }
        }
        else
            Promise.ofSuccess(Unit)

    }

    /** Attempts to find any registered users matching the user's platform contacts. */
    private fun syncPlatformContacts(): Promise<Unit, Exception> {
        log.info("Beginning platform contact sync")

        return getDefaultRegionCode() bind { defaultRegion ->
            authTokenManager.bind { userCredentials ->
                getPlatformContacts(defaultRegion) bind { contacts ->
                    contactsPersistenceManager.findMissing(contacts) bind { missingContacts ->
                        log.debug("Missing platform contacts:", missingContacts)
                        queryAndAddNewContacts(userCredentials, missingContacts)
                    }
                }
            }
        }
    }

    private fun updateContacts(userCredentials: UserCredentials, updates: Collection<AddressBookUpdate.Contact>): Promise<Unit, Exception> {
        if (updates.isEmpty())
            return Promise.ofSuccess(Unit)

        val messageLevelByUserId = updates.mapToMap {
            it.userId to it.allowedMessageLevel
        }

        val all = updates.mapToSet { it.userId }

        return contactsPersistenceManager.exists(all) bind { exists ->
            val missing = HashSet(all)
            missing.removeAll(exists)

            log.debug("Already exists: {}", exists)
            log.debug("Need to fetch remote info for: {}", missing)

            val updateExists = updates.filter { it.userId in exists }

            val p = if (missing.isNotEmpty()) {
                val request = FetchContactInfoByIdRequest(missing.toList())
                contactClient.fetchContactInfoById(userCredentials, request) map { response ->
                    response.contacts.map { it.toCore(messageLevelByUserId[it.id]!!) }
                }
            }
            else
                Promise.ofSuccess(emptyList())

            p bind { contactsPersistenceManager.applyDiff(it, updateExists) }
        }
    }

    private fun updateGroups(updates: Collection<AddressBookUpdate.Group>): Promise<Unit, Exception> {
        return if (updates.isNotEmpty()) {
            log.debug("Updating groups: {}", updates.map { it.groupId })
            groupPersistenceManager.applyDiff(updates)
        }
        else
            Promise.ofSuccess(Unit)
    }

    /** Syncs the local contact list with the remote contact list. */
    private fun syncRemoteContactsList(): Promise<Unit, Exception> {
        log.debug("Beginning remote contact list sync")

        val keyVault = userLoginData.keyVault

        return authTokenManager.bind { userCredentials ->
            addressBookClient.get(userCredentials) bind { response ->
                val allUpdates = decryptRemoteAddressBookEntries(keyVault, response.entries)

                val contactUpdates = ArrayList<AddressBookUpdate.Contact>()
                val groupUpdates = ArrayList<AddressBookUpdate.Group>()

                allUpdates.forEach {
                    when (it) {
                        is AddressBookUpdate.Contact -> contactUpdates.add(it)
                        is AddressBookUpdate.Group -> groupUpdates.add(it)
                    }
                }

                //order is important
                updateContacts(userCredentials, contactUpdates) bind {
                    updateGroups(groupUpdates)
                }
            }
        }
    }

    private fun getAddressBookUpdates(): Promise<List<AddressBookUpdate>, Exception> {
        return contactsPersistenceManager.getRemoteUpdates() bind { contactUpdates ->
            groupPersistenceManager.getRemoteUpdates() map { groupUpdates ->
                val allUpdates = ArrayList<AddressBookUpdate>()

                allUpdates.addAll(contactUpdates)
                allUpdates.addAll(groupUpdates)

                allUpdates
            }
        }
    }

    private fun processAddressBookUpdates(
        userCredentials: UserCredentials,
        contactUpdates: Collection<AddressBookUpdate.Contact>,
        groupUpdates: Collection<AddressBookUpdate.Group>
    ): Promise<Unit, Exception> {
        val allUpdates = ArrayList<AddressBookUpdate>()

        allUpdates.addAll(contactUpdates)
        allUpdates.addAll(groupUpdates)

        return if (allUpdates.isEmpty()) {
            log.info("No pending updates")
            Promise.ofSuccess(Unit)
        }
        else {
            log.info("Remote updates: {}", allUpdates)

            val keyVault = userLoginData.keyVault

            val request = updateRequestFromAddressBookUpdates(keyVault, allUpdates)
            addressBookClient.update(userCredentials, request) bind {
                contactsPersistenceManager.removeRemoteUpdates(contactUpdates.map { it.userId }) bind {
                    groupPersistenceManager.removeRemoteUpdates(groupUpdates.map { it.groupId })
                }
            }
        }
    }

    private fun updateRemoteContactList(): Promise<Unit, Exception> {
        log.info("Beginning remote contact list update")

        return authTokenManager.bind { userCredentials ->
            contactsPersistenceManager.getRemoteUpdates() bind { contactUpdates ->
                groupPersistenceManager.getRemoteUpdates() bind { groupUpdates ->
                    processAddressBookUpdates(userCredentials, contactUpdates, groupUpdates)
                }
            }
        }
    }

    override fun run(jobDescription: ContactSyncJobDescription): Promise<Unit, Exception> {
        val jobRunners = ArrayList<(Unit) -> Promise<Unit, Exception>>()

        if (jobDescription.platformContactSync)
            jobRunners.add { syncPlatformContacts() }

        if (jobDescription.updateRemote)
            jobRunners.add { updateRemoteContactList() }

        if (jobDescription.remoteSync)
            jobRunners.add {
                syncRemoteContactsList()
            }

        return jobRunners.fold(Promise.ofSuccess(Unit)) { z, v ->
            z bindUi v
        }
    }
}