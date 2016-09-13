package io.slychat.messenger.core.persistence

import io.slychat.messenger.core.PlatformContact
import io.slychat.messenger.core.UserId
import nl.komponents.kovenant.Promise

/**
 * Manages contacts.
 *
 * Once a contact entry has been created for a user, it's never removed. If a user "removes" the contact or blocks them,
 * it just updates their associated message level.
 *
 * Removing or blocking a contact removes the associated conversation info, as well as deletes any conversation log.
 */
interface ContactsPersistenceManager {
    fun get(userId: UserId): Promise<ContactInfo?, Exception>
    fun get(ids: Collection<UserId>): Promise<List<ContactInfo>, Exception>
    fun getAll(): Promise<List<ContactInfo>, Exception>

    fun exists(userId: UserId): Promise<Boolean, Exception>

    /** Returns a list of contacts for which we already have data for. */
    fun exists(users: Set<UserId>): Promise<Set<UserId>, Exception>

    fun getBlockList(): Promise<Set<UserId>, Exception>
    fun filterBlocked(users: Collection<UserId>): Promise<Set<UserId>, Exception>

    fun block(userId: UserId): Promise<Unit, Exception>
    fun unblock(userId: UserId): Promise<Unit, Exception>

    fun allowAll(userId: UserId): Promise<Unit, Exception>

    /**
     * Adds a new contact with the given message level.
     *
     * If the contact was not previously present, or was modified, then true is returned.
     *
     * If a contact was previously present but had a lower message level, then true is returned as well as
     * upgrading the message level. If the added level is lower than the current one, the previous message level is left
     * as-is and false is returned.
     *
     * If the given message level is ALL, then conversation log and info are created for the user.
     */
    fun add(contactInfo: ContactInfo): Promise<Boolean, Exception>

    /** This adds the given contact info, but doesn't generate a remote update. Only used to add yourself on initialization. */
    fun addSelf(selfInfo: ContactInfo): Promise<Unit, Exception>

    /** Adds all the given contacts and returns the list of contacts were not previously present. */
    fun add(contacts: Collection<ContactInfo>): Promise<Set<ContactInfo>, Exception>
    /** Updates the given contact's info. */
    fun update(contactInfo: ContactInfo): Promise<Unit, Exception>
    /** Sets a contact's message level to group-only, and removes their associated conversation. */
    fun remove(userId: UserId): Promise<Boolean, Exception>

    fun searchByPhoneNumber(phoneNumber: String): Promise<List<ContactInfo>, Exception>
    fun searchByName(name: String): Promise<List<ContactInfo>, Exception>
    fun searchByEmail(email: String): Promise<List<ContactInfo>, Exception>

    /** Find which platform contacts aren't currently in the contacts list. */
    fun findMissing(platformContacts: List<PlatformContact>): Promise<List<PlatformContact>, Exception>

    /** Used to apply remote updates. Does not generate any remote updates. */
    fun applyDiff(newContacts: Collection<ContactInfo>, updated: Collection<AddressBookUpdate.Contact>): Promise<Unit, Exception>

    fun getRemoteUpdates(): Promise<List<AddressBookUpdate.Contact>, Exception>
    fun removeRemoteUpdates(remoteUpdates: Collection<UserId>): Promise<Unit, Exception>

    /** Returns the new final address book hash. */
    fun addRemoteEntryHashes(remoteEntries: Collection<RemoteAddressBookEntry>): Promise<String, Exception>
    fun getAddressBookHash(): Promise<String, Exception>
}
