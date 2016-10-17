package io.slychat.messenger.services.contacts

import com.google.i18n.phonenumbers.PhoneNumberUtil
import io.slychat.messenger.core.*
import io.slychat.messenger.core.crypto.KeyVault
import io.slychat.messenger.core.http.api.ResourceConflictException
import io.slychat.messenger.core.http.api.contacts.*
import io.slychat.messenger.core.kovenant.bindRecoverFor
import io.slychat.messenger.core.persistence.*
import io.slychat.messenger.services.PlatformContacts
import io.slychat.messenger.services.auth.AuthTokenManager
import io.slychat.messenger.services.bindUi
import io.slychat.messenger.services.parsePhoneNumber
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.bind
import nl.komponents.kovenant.functional.map
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.TimeUnit

//we can't access contacts/groupservice methods during a sync, so we just persistence managers directly
//this is kinda annoying, since we need to return info to the ui from applyDiff by including them in the result instead
//of adding appropriate methods on the services
class AddressBookSyncJobImpl(
    private val authTokenManager: AuthTokenManager,
    private val contactLookupClient: ContactLookupAsyncClient,
    private val addressBookClient: AddressBookAsyncClient,
    private val contactsPersistenceManager: ContactsPersistenceManager,
    private val groupPersistenceManager: GroupPersistenceManager,
    private val keyVault: KeyVault,
    private val accountRegionCode: String,
    private val platformContacts: PlatformContacts,
    private val promiseTimerFactory: PromiseTimerFactory
) : AddressBookSyncJob {
    companion object {
        internal val UPDATE_MAX_RETRIES: Int = 3
    }

    private val log = LoggerFactory.getLogger(javaClass)

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

    private fun queryAndAddNewContacts(userCredentials: UserCredentials, missingContacts: List<PlatformContact>): Promise<Set<ContactInfo>, Exception> {
        return if (missingContacts.isNotEmpty()) {
            contactLookupClient.findLocalContacts(userCredentials, FindLocalContactsRequest(missingContacts)) bind { foundContacts ->
                log.debug("Found platform contacts: {}", foundContacts)

                contactsPersistenceManager.add(foundContacts.contacts.map { it.toCore(AllowedMessageLevel.ALL) })
            }
        }
        else
            Promise.ofSuccess(emptySet())

    }

    /** Attempts to find any registered users matching the user's platform contacts. */
    private fun findPlatformContacts(): Promise<Set<ContactInfo>, Exception> {
        log.info("Beginning platform contact sync")

        return authTokenManager.bind { userCredentials ->
            getPlatformContacts(accountRegionCode) bind { contacts ->
                contactsPersistenceManager.findMissing(contacts) bind { missingContacts ->
                    log.debug("Missing platform contacts: {}", missingContacts)
                    queryAndAddNewContacts(userCredentials, missingContacts)
                }
            }
        }
    }

    private fun updateContacts(userCredentials: UserCredentials, updates: Collection<AddressBookUpdate.Contact>): Promise<List<ContactDiffDelta>, Exception> {
        if (updates.isEmpty())
            return Promise.ofSuccess(emptyList())

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
                val request = FindAllByIdRequest(missing.toList())
                contactLookupClient.findAllById(userCredentials, request) map { response ->
                    response.contacts.map { it.toCore(messageLevelByUserId[it.id]!!) }
                }
            }
            else
                Promise.ofSuccess(emptyList())

            p bind { contactsPersistenceManager.applyDiff(it, updateExists) }
        }
    }

    private fun updateGroups(updates: Collection<AddressBookUpdate.Group>): Promise<List<GroupDiffDelta>, Exception> {
        return if (updates.isNotEmpty()) {
            log.debug("Updating groups: {}", updates.map { it.groupId })
            groupPersistenceManager.applyDiff(updates)
        }
        else
            Promise.ofSuccess(emptyList())
    }

    /** Syncs the local address book with the remote address book. */
    private fun pullRemoteUpdates(): Promise<PullResults, Exception> {
        log.debug("Beginning remote update pull")

        return authTokenManager.bind { userCredentials ->
            contactsPersistenceManager.getAddressBookHash() bind { addressBookHash ->
                log.debug("Local address book hash: {}", addressBookHash)

                addressBookClient.get(userCredentials, GetAddressBookRequest(addressBookHash)) bind { response ->
                    val remoteEntries = response.entries

                    if (remoteEntries.isNotEmpty()) {
                        log.debug("Updating address book")
                        val allUpdates = decryptRemoteAddressBookEntries(keyVault, remoteEntries)

                        val contactUpdates = ArrayList<AddressBookUpdate.Contact>()
                        val groupUpdates = ArrayList<AddressBookUpdate.Group>()

                        allUpdates.forEach {
                            when (it) {
                                is AddressBookUpdate.Contact -> contactUpdates.add(it)
                                is AddressBookUpdate.Group -> groupUpdates.add(it)
                            }
                        }

                        //order is important
                        updateContacts(userCredentials, contactUpdates) bind { contactDeltas ->
                            updateGroups(groupUpdates) bind { groupDeltas ->
                                contactsPersistenceManager.addRemoteEntryHashes(remoteEntries) map {
                                    PullResults(true, contactDeltas, groupDeltas)
                                }
                            }
                        }
                    }
                    else {
                        log.debug("No address book updates")

                        Promise.ofSuccess(PullResults(false, emptyList(), emptyList()))
                    }
                }
            }
        }
    }

    private fun updateRemoteAddressBook(userCredentials: UserCredentials, request: UpdateAddressBookRequest): Promise<UpdateAddressBookResponse, Exception> {
        fun updateWithRetry(attemptN: Int): Promise<UpdateAddressBookResponse, Exception> {
            return addressBookClient.update(userCredentials, request) bindRecoverFor { e: ResourceConflictException ->
                if (attemptN >= UPDATE_MAX_RETRIES)
                    throw e
                else {
                    //we'd like if retries from multiple devices didn't occur simultaneously
                    val min = (attemptN + 1) * 5
                    val max = (attemptN + 2) * 5
                    val secs = randomInt(min, max).toLong()

                    promiseTimerFactory.run(secs, TimeUnit.SECONDS) bind {
                        updateWithRetry(attemptN + 1)
                    }
                }
            }
        }

        return updateWithRetry(0)
    }

    private fun processAddressBookUpdates(
        userCredentials: UserCredentials,
        contactUpdates: Collection<AddressBookUpdate.Contact>,
        groupUpdates: Collection<AddressBookUpdate.Group>
    ): Promise<Int, Exception> {
        val allUpdates = ArrayList<AddressBookUpdate>()

        allUpdates.addAll(contactUpdates)
        allUpdates.addAll(groupUpdates)

        val updateCount = allUpdates.size

        return if (allUpdates.isEmpty()) {
            log.info("No pending updates")
            Promise.ofSuccess(0)
        }
        else {
            log.info("Remote updates: {}", allUpdates)

            val entries = encryptRemoteAddressBookEntries(keyVault, allUpdates)

            contactsPersistenceManager.addRemoteEntryHashes(entries) bind { localHash ->
                val request = UpdateAddressBookRequest(localHash, entries)

                updateRemoteAddressBook(userCredentials, request) bind { response ->
                    val serverHash = response.hash
                    val updated = response.updated

                    log.debug("Remote/local Address book hashes: {}/{}; updated: {}", serverHash, localHash, updated)

                    contactsPersistenceManager.removeRemoteUpdates(contactUpdates.map { it.userId }) bind {
                        groupPersistenceManager.removeRemoteUpdates(groupUpdates.map { it.groupId }) map {
                            if (updated)
                                 updateCount
                            else
                                0
                        }
                    }
                }
            }
        }
    }

    private fun pushRemoteUpdates(): Promise<Int, Exception> {
        log.info("Beginning remote update push")

        return authTokenManager.bind { userCredentials ->
            contactsPersistenceManager.getRemoteUpdates() bind { contactUpdates ->
                groupPersistenceManager.getRemoteUpdates() bind { groupUpdates ->
                    processAddressBookUpdates(userCredentials, contactUpdates, groupUpdates)
                }
            }
        }
    }

    override fun run(jobDescription: AddressBookSyncJobDescription): Promise<AddressBookSyncResult, Exception> {
        val jobRunners = ArrayList<(AddressBookSyncResult) -> Promise<AddressBookSyncResult, Exception>>()

        if (jobDescription.findPlatformContacts)
            jobRunners.add { result -> findPlatformContacts() map { result.copy(addedLocalContacts = it.toList()) } }

        if (jobDescription.push)
            jobRunners.add { result -> pushRemoteUpdates() map { result.copy(updateCount = it) } }

        if (jobDescription.pull)
            jobRunners.add { result -> pullRemoteUpdates() map { result.copy(pullResults = it) } }

        return jobRunners.fold(Promise.ofSuccess(AddressBookSyncResult(true, 0, PullResults(), emptyList()))) { z, v ->
            z bindUi v
        }
    }
}