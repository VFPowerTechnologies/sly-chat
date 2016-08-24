package io.slychat.messenger.services.contacts

import com.google.i18n.phonenumbers.PhoneNumberUtil
import io.slychat.messenger.core.*
import io.slychat.messenger.core.http.api.ResourceConflictException
import io.slychat.messenger.core.http.api.contacts.*
import io.slychat.messenger.core.kovenant.bindRecoverFor
import io.slychat.messenger.core.persistence.AddressBookUpdate
import io.slychat.messenger.core.persistence.AllowedMessageLevel
import io.slychat.messenger.core.persistence.ContactsPersistenceManager
import io.slychat.messenger.core.persistence.GroupPersistenceManager
import io.slychat.messenger.services.PlatformContacts
import io.slychat.messenger.services.UserData
import io.slychat.messenger.services.auth.AuthTokenManager
import io.slychat.messenger.services.bindUi
import io.slychat.messenger.services.parsePhoneNumber
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.bind
import nl.komponents.kovenant.functional.map
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.TimeUnit

class AddressBookSyncJobImpl(
    private val authTokenManager: AuthTokenManager,
    private val contactClient: ContactAsyncClient,
    private val addressBookClient: AddressBookAsyncClient,
    private val contactsPersistenceManager: ContactsPersistenceManager,
    private val groupPersistenceManager: GroupPersistenceManager,
    private val userLoginData: UserData,
    private val accountRegionCode: String,
    private val platformContacts: PlatformContacts,
    private val timerFactory: TimerFactory
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
    private fun findPlatformContacts(): Promise<Unit, Exception> {
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

    /** Syncs the local address book with the remote address book. */
    private fun pullRemoteUpdates(): Promise<Unit, Exception> {
        log.debug("Beginning remote update pull")

        val keyVault = userLoginData.keyVault

        return authTokenManager.bind { userCredentials ->
            contactsPersistenceManager.getAddressBookVersion() bind { addressBookVersion ->
                log.debug("Local address book version: {}", addressBookVersion)

                addressBookClient.get(userCredentials, GetAddressBookRequest(addressBookVersion)) bind { response ->
                    if (response.entries.isNotEmpty()) {
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
                            updateGroups(groupUpdates) bind {
                                val newVersion = response.version

                                if (newVersion != addressBookVersion) {
                                    log.debug("Address book version updated to: {}", newVersion)
                                    contactsPersistenceManager.updateAddressBookVersion(newVersion)
                                }
                                else
                                    Promise.ofSuccess(Unit)
                            }
                        }
                    }
                    else {
                        log.debug("No address book updates")

                        Promise.ofSuccess(Unit)
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

                    timerFactory.run(secs, TimeUnit.SECONDS) bind {
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

            contactsPersistenceManager.getAddressBookVersion() bind { currentVersion ->
                val request = updateRequestFromAddressBookUpdates(keyVault, currentVersion, allUpdates)
                updateRemoteAddressBook(userCredentials, request) bind { response ->
                    val newVersion = response.version
                    log.debug("Address book version updated to: {}", newVersion)

                    contactsPersistenceManager.removeRemoteUpdates(contactUpdates.map { it.userId }) bind {
                        groupPersistenceManager.removeRemoteUpdates(groupUpdates.map { it.groupId }) bind {
                            contactsPersistenceManager.updateAddressBookVersion(newVersion)
                        }
                    }
                }
            }
        }
    }

    private fun pushRemoteUpdates(): Promise<Unit, Exception> {
        log.info("Beginning remote update push")

        return authTokenManager.bind { userCredentials ->
            contactsPersistenceManager.getRemoteUpdates() bind { contactUpdates ->
                groupPersistenceManager.getRemoteUpdates() bind { groupUpdates ->
                    processAddressBookUpdates(userCredentials, contactUpdates, groupUpdates)
                }
            }
        }
    }

    override fun run(jobDescription: AddressBookSyncJobDescription): Promise<Unit, Exception> {
        val jobRunners = ArrayList<(Unit) -> Promise<Unit, Exception>>()

        if (jobDescription.findPlatformContacts)
            jobRunners.add { findPlatformContacts() }

        if (jobDescription.push)
            jobRunners.add { pushRemoteUpdates() }

        if (jobDescription.pull)
            jobRunners.add { pullRemoteUpdates() }

        return jobRunners.fold(Promise.ofSuccess(Unit)) { z, v ->
            z bindUi v
        }
    }
}