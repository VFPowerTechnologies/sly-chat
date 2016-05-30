package io.slychat.messenger.core.persistence

import io.slychat.messenger.core.PlatformContact
import io.slychat.messenger.core.UserId
import nl.komponents.kovenant.Promise

/** Manages contacts. */
interface ContactsPersistenceManager {
    fun get(userId: UserId): Promise<ContactInfo?, Exception>
    fun getAll(): Promise<List<ContactInfo>, Exception>

    fun exists(userId: UserId): Promise<Boolean, Exception>
    fun exists(users: Set<UserId>): Promise<Set<UserId>, Exception>

    /** Get pending users. */
    fun getPending(): Promise<List<ContactInfo>, Exception>

    /** Mark the given users as not pending. */
    fun markAccepted(users: Set<UserId>): Promise<Unit, Exception>

    /** Returns info for all available conversations. */
    fun getAllConversations(): Promise<List<Conversation>, Exception>

    /** Returns a ConversationInfo for the given user. */
    fun getConversationInfo(userId: UserId): Promise<ConversationInfo, Exception>

    /** Resets unread message count for the given contact's conversation. */
    fun markConversationAsRead(userId: UserId): Promise<Unit, Exception>

    /** Adds a new contact and conversation for a contact. Returns true if contact was not already present. */
    fun add(contactInfo: ContactInfo): Promise<Boolean, Exception>

    /** Adds all the given contacts and returns the list of contacts were not previously present. */
    fun add(contacts: Collection<ContactInfo>): Promise<Set<ContactInfo>, Exception>
    /** Updates the given contact's info. */
    fun update(contactInfo: ContactInfo): Promise<Unit, Exception>
    /** Removes a contact and their associated conversation. */
    fun remove(contactInfo: ContactInfo): Promise<Boolean, Exception>

    fun searchByPhoneNumber(phoneNumber: String): Promise<List<ContactInfo>, Exception>
    fun searchByName(name: String): Promise<List<ContactInfo>, Exception>
    fun searchByEmail(email: String): Promise<List<ContactInfo>, Exception>

    /** Find which platform contacts aren't currently in the contacts list. */
    fun findMissing(platformContacts: List<PlatformContact>): Promise<List<PlatformContact>, Exception>

    /** Diff the current contact list with the given remote one. */
    fun getDiff(ids: Collection<UserId>): Promise<ContactListDiff, Exception>

    fun applyDiff(newContacts: Collection<ContactInfo>, removedContacts: Collection<UserId>): Promise<Unit, Exception>

    /** Contacts with pending messages but no available info. */
    fun getUnadded(): Promise<Set<UserId>, Exception>

    fun addRemoteUpdate(remoteUpdates: Collection<RemoteContactUpdate>): Promise<Unit, Exception>
    fun getRemoteUpdates(): Promise<List<RemoteContactUpdate>, Exception>
    fun removeRemoteUpdates(remoteUpdates: Collection<RemoteContactUpdate>): Promise<Unit, Exception>
}