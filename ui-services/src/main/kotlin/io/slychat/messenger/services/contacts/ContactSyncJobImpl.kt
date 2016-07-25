package io.slychat.messenger.services.contacts

import com.google.i18n.phonenumbers.PhoneNumberUtil
import io.slychat.messenger.core.http.api.contacts.*
import io.slychat.messenger.core.mapToMap
import io.slychat.messenger.core.mapToSet
import io.slychat.messenger.core.persistence.AccountInfoPersistenceManager
import io.slychat.messenger.core.persistence.AllowedMessageLevel
import io.slychat.messenger.core.persistence.ContactsPersistenceManager
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
    private val contactListClient: ContactListAsyncClient,
    private val contactsPersistenceManager: ContactsPersistenceManager,
    private val userLoginData: UserData,
    private val accountInfoPersistenceManager: AccountInfoPersistenceManager,
    private val platformContacts: PlatformContacts
) : ContactSyncJob {
    private val log = LoggerFactory.getLogger(javaClass)

    private fun getDefaultRegionCode(): Promise<String, Exception> {
        return accountInfoPersistenceManager.retrieve() map { getAccountRegionCode(it!!) }
    }

    /** Attempts to find any registered users matching the user's local contacts. */
    private fun syncLocalContacts(): Promise<Unit, Exception> {
        log.info("Beginning local contact sync")

        return getDefaultRegionCode() bind { defaultRegion ->
            authTokenManager.bind { userCredentials ->
                platformContacts.fetchContacts() map { contacts ->
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
                } bind { contacts ->
                    contactsPersistenceManager.findMissing(contacts)
                } bind { missingContacts ->
                    log.debug("Missing local contacts:", missingContacts)
                    contactClient.findLocalContacts(userCredentials, FindLocalContactsRequest(missingContacts))
                } bind { foundContacts ->
                    log.debug("Found local contacts: {}", foundContacts)

                    contactsPersistenceManager.add(foundContacts.contacts.map { it.toCore(AllowedMessageLevel.ALL) }) map { Unit }
                }
            }
        }
    }

    /** Syncs the local contact list with the remote contact list. */
    private fun syncRemoteContactsList(): Promise<Unit, Exception> {
        log.debug("Beginning remote contact list sync")

        val keyVault = userLoginData.keyVault

        val contactsPersistenceManager = contactsPersistenceManager

        return authTokenManager.bind { userCredentials ->
            contactListClient.getContacts(userCredentials) bind { response ->
                val updates = decryptRemoteContactEntries(keyVault, response.contacts)

                val messageLevelByUserId = updates.mapToMap {
                    it.userId to it.allowedMessageLevel
                }

                val all = updates.mapToSet { it.userId }

                contactsPersistenceManager.exists(all) bind { exists ->
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
        }
    }

    private fun updateRemoteContactList(): Promise<Unit, Exception> {
        log.info("Beginning remote contact list update")

        return authTokenManager.bind { userCredentials ->
            contactsPersistenceManager.getRemoteUpdates() bind { updates ->
                if (updates.isEmpty()) {
                    log.info("No contact updates")
                    Promise.ofSuccess(Unit)
                } else {
                    log.info("Remote update: ", updates)

                    val keyVault = userLoginData.keyVault

                    val request = updateRequestFromRemoteContactUpdates(keyVault, updates)
                    contactListClient.updateContacts(userCredentials, request) bind {
                        contactsPersistenceManager.removeRemoteUpdates(updates)
                    }
                }
            }
        }
    }

    override fun run(jobDescription: ContactSyncJobDescription): Promise<Unit, Exception> {
        val jobRunners = ArrayList<(Unit) -> Promise<Unit, Exception>>()

        if (jobDescription.localSync)
            jobRunners.add { syncLocalContacts() }

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