package io.slychat.messenger.services.contacts

import com.nhaarman.mockito_kotlin.*
import io.slychat.messenger.core.*
import io.slychat.messenger.core.crypto.KeyVault
import io.slychat.messenger.core.crypto.generateNewKeyVault
import io.slychat.messenger.core.http.api.ResourceConflictException
import io.slychat.messenger.core.http.api.contacts.*
import io.slychat.messenger.core.persistence.*
import io.slychat.messenger.services.PlatformContacts
import io.slychat.messenger.services.UserData
import io.slychat.messenger.services.crypto.MockAuthTokenManager
import io.slychat.messenger.testutils.KovenantTestModeRule
import io.slychat.messenger.testutils.thenAnswerSuccess
import io.slychat.messenger.testutils.thenReject
import io.slychat.messenger.testutils.thenResolve
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AddressBookSyncJobImplTest {
    companion object {
        @JvmField
        @ClassRule
        val kovenantTestMode = KovenantTestModeRule()

        val keyVault = generateNewKeyVault("test")
    }

    val emptyMd5 = "d41d8cd98f00b204e9800998ecf8427e"
    val updateMd5 = "d41d8cd98f00b204e9800998ecf8427f"

    val contactLookupAsyncClient: ContactLookupAsyncClient = mock()
    val addressBookAsyncClient: AddressBookAsyncClient = mock()
    val contactsPersistenceManager: ContactsPersistenceManager = mock()
    val groupPersistenceManager:  GroupPersistenceManager = mock()
    val userLoginData = UserData(SlyAddress(randomUserId(), 1), keyVault, emptyByteArray())
    val accountRegionCode = "1"
    val platformContacts: PlatformContacts = mock()
    val promiseTimerFactory: PromiseTimerFactory = mock()

    @Before
    fun before() {
        whenever(platformContacts.fetchContacts()).thenResolve(emptyList())

        whenever(contactsPersistenceManager.findMissing(any())).thenResolve(listOf())
        whenever(contactsPersistenceManager.add(any<Collection<ContactInfo>>())).thenResolve(emptySet())
        whenever(contactsPersistenceManager.getRemoteUpdates()).thenResolve(emptyList())
        whenever(contactsPersistenceManager.removeRemoteUpdates(any())).thenResolve(Unit)
        whenever(contactsPersistenceManager.applyDiff(any(), any())).thenResolve(Unit)
        whenever(contactsPersistenceManager.exists(anySet())).thenAnswerSuccess {
            val a = it.arguments[0]
            @Suppress("UNCHECKED_CAST")
            (a as Set<UserId>)
        }

        whenever(groupPersistenceManager.applyDiff(any())).thenResolve(Unit)
        whenever(groupPersistenceManager.getRemoteUpdates()).thenResolve(emptyList())
        whenever(groupPersistenceManager.removeRemoteUpdates(any())).thenResolve(Unit)

        whenever(contactLookupAsyncClient.findLocalContacts(any(), any())).thenResolve(FindLocalContactsResponse(emptyList()))
        whenever(contactLookupAsyncClient.findAllById(any(), any())).thenResolve(FindAllByIdResponse(emptyList()))

        whenever(contactsPersistenceManager.getAddressBookHash()).thenResolve(emptyMd5)
        whenever(contactsPersistenceManager.addRemoteEntryHashes(any())).thenResolve(emptyMd5)

        whenever(addressBookAsyncClient.update(any(), any())).thenResolve(UpdateAddressBookResponse(updateMd5, true))
        whenever(addressBookAsyncClient.get(any(), any())).thenResolve(GetAddressBookResponse(emptyList()))

        whenever(promiseTimerFactory.run(any(), any())).thenResolve(Unit)
    }

    fun newJob(): AddressBookSyncJobImpl {
        return AddressBookSyncJobImpl(
            MockAuthTokenManager(),
            contactLookupAsyncClient,
            addressBookAsyncClient,
            contactsPersistenceManager,
            groupPersistenceManager,
            userLoginData,
            accountRegionCode,
            platformContacts,
            promiseTimerFactory
        )
    }

    fun runJobWithDescription(body: AddressBookSyncJobDescription.() -> Unit): AddressBookSyncResult {
        val syncJob = newJob()

        val description = AddressBookSyncJobDescription()
        description.body()

        return syncJob.run(description).get()
    }

    fun runPush(): AddressBookSyncResult {
        return runJobWithDescription { doPush() }
    }

    fun runFindPlatformContacts(): AddressBookSyncResult {
        return runJobWithDescription { doFindPlatformContacts() }
    }

    fun runPull(): AddressBookSyncResult {
        return runJobWithDescription { doPull() }
    }

    fun randomRemoteEntries(): List<RemoteAddressBookEntry> {
        val missing = randomUserIds()
        return encryptRemoteAddressBookEntries(keyVault, missing.map { AddressBookUpdate.Contact(it, AllowedMessageLevel.ALL) })
    }

    @Test
    fun `a pull should fetch any missing contact info`() {
        val missing = randomUserIds()
        val remoteEntries = encryptRemoteAddressBookEntries(keyVault, missing.map { AddressBookUpdate.Contact(it, AllowedMessageLevel.ALL) })

        whenever(addressBookAsyncClient.get(any(), any())).thenResolve(GetAddressBookResponse(remoteEntries))
        whenever(contactsPersistenceManager.exists(missing)).thenResolve(emptySet())

        runPull()

        verify(contactLookupAsyncClient).findAllById(any(), capture {
            assertThat(it.ids).apply {
                `as`("Missing ids should be looked up")
                containsOnlyElementsOf(missing)
            }
        })
    }

    @Test
    fun `a pull should add missing contacts with the proper message levels`() {
        val missing = randomUserIds(3)
        val messageLevels = missing.zip(listOf(
            AllowedMessageLevel.ALL,
            AllowedMessageLevel.BLOCKED,
            AllowedMessageLevel.GROUP_ONLY
        )).toMap()

        val apiContacts = missing.map { ApiContactInfo(it, "$it@a.com", it.toString(), it.toString(), it.toString()) }
        val remoteEntries = encryptRemoteAddressBookEntries(keyVault, missing.map { AddressBookUpdate.Contact(it, messageLevels[it]!!) })

        whenever(addressBookAsyncClient.get(any(), any())).thenResolve(GetAddressBookResponse(remoteEntries))
        whenever(contactsPersistenceManager.exists(missing)).thenResolve(emptySet())
        whenever(contactLookupAsyncClient.findAllById(any(), any())).thenResolve(FindAllByIdResponse(apiContacts))

        runPull()

        verify(contactsPersistenceManager).applyDiff(capture {
            assertEquals(missing.size, it.size, "New contacts size doesn't match")

            it.forEach {
                assertEquals(messageLevels[it.id]!!, it.allowedMessageLevel, "Invalid message level")
            }
        }, any())
    }

    @Test
    fun `a pull should update existing contacts with the proper message level`() {
        val present = randomUserIds(3)
        val messageLevels = listOf(AllowedMessageLevel.ALL, AllowedMessageLevel.GROUP_ONLY, AllowedMessageLevel.BLOCKED)
        val remoteUpdates = present.zip(messageLevels).map { AddressBookUpdate.Contact(it.first, it.second) }
        val remoteEntries = encryptRemoteAddressBookEntries(keyVault, remoteUpdates)

        whenever(addressBookAsyncClient.get(any(), any())).thenResolve(GetAddressBookResponse(remoteEntries))
        whenever(contactsPersistenceManager.exists(present)).thenResolve(present)

        runPull()

        verify(contactsPersistenceManager).applyDiff(any(), capture {
            assertThat(it).apply {
                `as`("Existing contacts should have their message levels updated")
                containsOnlyElementsOf(remoteUpdates)
            }
        })
    }

    @Test
    fun `a pull not should add contacts if none are present`() {
        whenever(addressBookAsyncClient.get(any(), any())).thenResolve(GetAddressBookResponse(emptyList()))

        runPull()

        verify(contactsPersistenceManager, never()).applyDiff(any(), any())
    }

    @Test
    fun `a pull should indicate a full pull was done if entries are returned from the server`() {
        val update = AddressBookUpdate.Contact(randomUserId(), AllowedMessageLevel.ALL)
        val entries = encryptRemoteAddressBookEntries(keyVault, listOf(update))

        whenever(addressBookAsyncClient.get(any(), any())).thenResolve(GetAddressBookResponse(entries))

        val result = runPull()

        assertTrue(result.fullPull, "Indicates a full pull wasn't done")
    }

    @Test
    fun `a pull should indicate no full pull was done if no entries are returned from the server`() {
        whenever(addressBookAsyncClient.get(any(), any())).thenResolve(GetAddressBookResponse(emptyList()))

        val result = runPull()

        assertFalse(result.fullPull, "Indicates a full pull was done")
    }

    @Test
    fun `a pull should update the address book hashes`() {
        val update = AddressBookUpdate.Contact(randomUserId(), AllowedMessageLevel.ALL)
        val entries = encryptRemoteAddressBookEntries(keyVault, listOf(update))

        whenever(addressBookAsyncClient.get(any(), any())).thenResolve(GetAddressBookResponse(entries))

        runPull()

        verify(contactsPersistenceManager).addRemoteEntryHashes(entries)
    }

    @Test
    fun `a pull should add contacts before groups`() {
        val groupInfo = randomGroupInfo()

        val userId = randomUserId()
        val apiContacts = listOf(ApiContactInfo(userId, "$userId@a.com", userId.toString(), userId.toString(), userId.toString()))
        val remoteUpdates = listOf(
            AddressBookUpdate.Group(groupInfo.id, groupInfo.name, emptySet(), GroupMembershipLevel.JOINED),
            AddressBookUpdate.Contact(userId, AllowedMessageLevel.ALL)
        )

        val remoteEntries = encryptRemoteAddressBookEntries(keyVault, remoteUpdates)

        whenever(addressBookAsyncClient.get(any(), any())).thenResolve(GetAddressBookResponse(remoteEntries))
        whenever(contactLookupAsyncClient.findAllById(any(), any())).thenResolve(FindAllByIdResponse(apiContacts))

        runPull()

        val order = inOrder(contactsPersistenceManager, groupPersistenceManager)
        order.verify(contactsPersistenceManager).applyDiff(any(), any())
        order.verify(groupPersistenceManager).applyDiff(any())
    }

    @Test
    fun `a pull should add missing groups`() {
        val groupInfo = randomGroupInfo()

        val remoteUpdates = listOf(
            AddressBookUpdate.Group(groupInfo.id, groupInfo.name, emptySet(), GroupMembershipLevel.JOINED)
        )
        val remoteEntries = encryptRemoteAddressBookEntries(keyVault, remoteUpdates)

        whenever(addressBookAsyncClient.get(any(), any())).thenResolve(GetAddressBookResponse(remoteEntries))

        runPull()

        verify(groupPersistenceManager).applyDiff(remoteUpdates)
    }

    @Test
    fun `a pull not should add groups if none are present`() {
        val remoteEntries = encryptRemoteAddressBookEntries(keyVault, emptyList())

        whenever(addressBookAsyncClient.get(any(), any())).thenResolve(GetAddressBookResponse(remoteEntries))

        runPull()

        verify(groupPersistenceManager, never()).applyDiff(any())
    }

    @Test
    fun `a push should not issue a remote request if no missing platform contacts are found`() {
        runPush()

        verify(addressBookAsyncClient, never()).update(any(), any())
    }

    @Test
    fun `a push should indicate no updates were performed if no updates are present`() {
        val result = runPush()

        assertEquals(0, result.updateCount, "Update count wasn't 0")
    }

    @Test
    fun `a push should indicate no updates were performed if the server returns updated=false`() {
        val groupInfo = randomGroupInfo()

        val groupUpdates = listOf(
            AddressBookUpdate.Group(groupInfo.id, groupInfo.name, emptySet(), groupInfo.membershipLevel)
        )

        val contactUpdates = listOf(
            AddressBookUpdate.Contact(randomUserId(), AllowedMessageLevel.ALL)
        )

        whenever(contactsPersistenceManager.getRemoteUpdates()).thenResolve(contactUpdates)
        whenever(groupPersistenceManager.getRemoteUpdates()).thenResolve(groupUpdates)

        whenever(contactsPersistenceManager.getAddressBookHash()).thenResolve(emptyMd5)
        whenever(addressBookAsyncClient.update(any(), any())).thenResolve(UpdateAddressBookResponse(emptyMd5, false))

        val result = runPush()

        assertEquals(0, result.updateCount, "Update count wasn't 0")
    }

    @Test
    fun `a push should indicate the number of updates available if the server returns updated=true`() {
        val groupInfo = randomGroupInfo()

        val groupUpdates = listOf(
            AddressBookUpdate.Group(groupInfo.id, groupInfo.name, emptySet(), groupInfo.membershipLevel)
        )

        val contactUpdates = listOf(
            AddressBookUpdate.Contact(randomUserId(), AllowedMessageLevel.ALL)
        )

        whenever(contactsPersistenceManager.getRemoteUpdates()).thenResolve(contactUpdates)
        whenever(groupPersistenceManager.getRemoteUpdates()).thenResolve(groupUpdates)

        val result = runPush()

        assertEquals(2, result.updateCount, "Update count is incorrect")
    }

    @Test
    fun `a push should send group updates`() {
        val groupInfo = randomGroupInfo()

        val updates = listOf(
            AddressBookUpdate.Group(groupInfo.id, groupInfo.name, emptySet(), groupInfo.membershipLevel)
        )

        whenever(groupPersistenceManager.getRemoteUpdates()).thenResolve(updates)

        runPush()

        verify(addressBookAsyncClient).update(any(), capture {
            assertRemoteEntriesEqual(keyVault, it.entries, updates)
        })
    }

    private fun assertRemoteEntriesEqual(keyVault: KeyVault, got: List<RemoteAddressBookEntry>, expected: List<AddressBookUpdate>) {
        val sent = decryptRemoteAddressBookEntries(keyVault, got)

        assertThat(sent).apply {
            `as`("Should match the sent entries")
            containsOnlyElementsOf(expected)
        }
    }

    @Test
    fun `a push should send contact updates`() {
        val updates = listOf(
            AddressBookUpdate.Contact(randomUserId(), AllowedMessageLevel.ALL)
        )

        whenever(contactsPersistenceManager.getRemoteUpdates()).thenResolve(updates)

        runPush()

        verify(addressBookAsyncClient).update(any(), capture {
            assertRemoteEntriesEqual(keyVault, it.entries, updates)
        })
    }

    @Test
    fun `a push should delete group updates after a successful update`() {
        val groupInfo = randomGroupInfo()

        val updates = listOf(
            AddressBookUpdate.Group(groupInfo.id, groupInfo.name, emptySet(), groupInfo.membershipLevel)
        )

        whenever(groupPersistenceManager.getRemoteUpdates()).thenResolve(updates)

        runPush()

        verify(groupPersistenceManager).removeRemoteUpdates(updates.map { it.groupId })
    }

    @Test
    fun `a push should delete contact updates after a successful update`() {
        val updates = listOf(
            AddressBookUpdate.Contact(randomUserId(), AllowedMessageLevel.ALL)
        )

        whenever(contactsPersistenceManager.getRemoteUpdates()).thenResolve(updates)

        runPush()

        verify(contactsPersistenceManager).removeRemoteUpdates(updates.map { it.userId })
    }

    @Test
    fun `a push should update the address book hashes`() {
        val updates = listOf(
            AddressBookUpdate.Contact(randomUserId(), AllowedMessageLevel.ALL)
        )

        whenever(contactsPersistenceManager.getRemoteUpdates()).thenResolve(updates)

        runPush()

        verify(contactsPersistenceManager).addRemoteEntryHashes(capture {
            assertRemoteEntriesEqual(keyVault, it.toList(), updates)
        })
    }

    @Test
    fun `a push should retry up to MAX_RETRIES times and finally rethrow the exception when receiving a ResourceConflictException`() {
        val retries = AddressBookSyncJobImpl.UPDATE_MAX_RETRIES
        val totalAttempts = retries + 1

        val updates = listOf(
            AddressBookUpdate.Contact(randomUserId(), AllowedMessageLevel.ALL)
        )

        whenever(contactsPersistenceManager.getRemoteUpdates()).thenResolve(updates)

        var ongoing = whenever(addressBookAsyncClient.update(any(), any()))
            .thenReject(ResourceConflictException())

        (1..retries).forEach {
            ongoing = ongoing.thenReject(ResourceConflictException())
        }

        assertFailsWith(ResourceConflictException::class) {
            runPush()
        }

        verify(addressBookAsyncClient, times(totalAttempts)).update(any(), any())
    }

    @Test
    fun `a pull should not issue a remote request if no contacts need to be added`() {
        whenever(contactsPersistenceManager.getRemoteUpdates()).thenResolve(emptyList())

        runPull()

        verify(contactLookupAsyncClient, never()).findAllById(any(), any())
    }

    @Test
    fun `a pull request should include the current address book hash`() {
        whenever(contactsPersistenceManager.getAddressBookHash()).thenResolve(emptyMd5)

        runPull()

        verify(contactsPersistenceManager).getAddressBookHash()
        verify(addressBookAsyncClient).get(any(), eq(GetAddressBookRequest(emptyMd5)))
    }

    @Test
    fun `a find platform contacts should not issue a remote request if no platform contacts are found`() {
        whenever(platformContacts.fetchContacts()).thenResolve(emptyList())

        runFindPlatformContacts()

        verify(contactLookupAsyncClient, never()).findLocalContacts(any(), any())
    }

    @Test
    fun `a find platform contacts should not issue a remote request if no missing local contacts are found`() {
        val platformContact = PlatformContact("name", listOf("a@a.com"), listOf("15555555555"))
        whenever(platformContacts.fetchContacts()).thenResolve(listOf(platformContact))
        whenever(contactsPersistenceManager.findMissing(anyList())).thenResolve(emptyList())

        runFindPlatformContacts()

        verify(contactLookupAsyncClient, never()).findLocalContacts(any(), any())
    }

    @Test
    fun `a find platform contacts should query for new contacts using missing local platform contact data`() {
        val platformContact = PlatformContact("name", listOf("a@a.com"), listOf("15555555555"))
        val missingContacts = listOf(platformContact)

        whenever(platformContacts.fetchContacts()).thenResolve(missingContacts)
        whenever(contactsPersistenceManager.findMissing(anyList())).thenResolve(missingContacts)

        runFindPlatformContacts()

        verify(contactLookupAsyncClient).findLocalContacts(any(), eq(FindLocalContactsRequest(missingContacts)))
    }

    fun testFindPlatformAdd(): Pair<AddressBookSyncResult, ContactInfo> {
        val userId = randomUserId()
        val email = randomEmailAddress()
        val name = randomName()
        val phoneNumber = randomPhoneNumber()
        val publicKey = "pubkey"
        val platformContact = randomPlatformContact()

        val missingContacts = listOf(platformContact)
        val apiContacts =  listOf(
            ApiContactInfo(userId, email, name, phoneNumber, publicKey)
        )
        val contactInfo = ContactInfo(userId, email, name, AllowedMessageLevel.ALL, phoneNumber, publicKey)

        whenever(platformContacts.fetchContacts()).thenResolve(missingContacts)
        whenever(contactsPersistenceManager.findMissing(anyList())).thenResolve(missingContacts)
        whenever(contactsPersistenceManager.add(anyCollection())).thenResolve(setOf(contactInfo))
        whenever(contactLookupAsyncClient.findLocalContacts(any(), any())).thenResolve(FindLocalContactsResponse(apiContacts))

        val result = runFindPlatformContacts()

        return result to contactInfo
    }

    @Test
    fun `a find platform contacts should add local contacts with remote accounts to the contact list with ALL message level`() {
        val (result, contactInfo) = testFindPlatformAdd()

        verify(contactsPersistenceManager).add(capture<Collection<ContactInfo>> {
            assertThat(it).apply {
                `as`("Contacts should be added")
                containsOnly(contactInfo)
            }
        })
    }

    @Test
    fun `a find platform contacts should return added contacts in the result`() {
        val (result, contactInfo) = testFindPlatformAdd()

        assertThat(result.addedLocalContacts).apply {
            `as`("Should contain the added contact")
            containsOnly(contactInfo)
        }
    }
}