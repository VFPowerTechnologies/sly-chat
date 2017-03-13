package io.slychat.messenger.services

import com.nhaarman.mockito_kotlin.mock
import io.slychat.messenger.core.crypto.KeyVault
import io.slychat.messenger.core.crypto.generateNewKeyVault
import io.slychat.messenger.core.emptyByteArray
import io.slychat.messenger.core.persistence.*
import io.slychat.messenger.core.randomSlyAddress
import io.slychat.messenger.services.auth.AuthTokenManager
import io.slychat.messenger.services.config.DummyConfigBackend
import io.slychat.messenger.services.config.UserConfigService
import io.slychat.messenger.services.contacts.AddressBookOperationManager
import io.slychat.messenger.services.contacts.ContactsService
import io.slychat.messenger.services.crypto.MessageCipherService
import io.slychat.messenger.services.crypto.MockAuthTokenManager
import io.slychat.messenger.services.di.UserComponent
import io.slychat.messenger.services.files.StorageService
import io.slychat.messenger.services.files.TransferManager
import io.slychat.messenger.services.messaging.*
import rx.Observable
import rx.subjects.PublishSubject

class MockUserComponent : UserComponent {
    companion object {
        val keyVaultPassword = "test"

        val keyVault = generateNewKeyVault(keyVaultPassword)
    }

    override val keyVault: KeyVault = MockUserComponent.keyVault

    override val accountLocalInfoManager: AccountLocalInfoManager = mock()

    override val keyVaultPersistenceManager: KeyVaultPersistenceManager = mock()

    override val persistenceManager: PersistenceManager = mock()

    override val contactsPersistenceManager: ContactsPersistenceManager = mock()

    override val messagePersistenceManager: MessagePersistenceManager = mock()

    override val sessionDataManager: SessionDataManager = mock()

    override val preKeyPersistenceManager: PreKeyPersistenceManager = mock()

    override val contactsService: ContactsService = mock()

    override val messengerService: MessengerService = mock()

    override val groupService: GroupService = mock()

    override val messageCipherService: MessageCipherService = mock()

    override val notifierService: NotifierService = mock()

    override val userLoginData: UserData = UserData(randomSlyAddress(), emptyByteArray())

    override val relayClock: RelayClock = mock()

    override val relayClientManager: RelayClientManager = mock()

    override val preKeyManager: PreKeyManager = mock()

    override val offlineMessageManager: OfflineMessageManager = mock()

    val mockAuthTokenManager = MockAuthTokenManager()

    override val authTokenManager: AuthTokenManager
        get() = mockAuthTokenManager

    override val userConfigService: UserConfigService = UserConfigService(DummyConfigBackend())

    override val addressBookOperationManager: AddressBookOperationManager = mock()

    override val accountInfoManager: AccountInfoManager = mock()

    override val mutualContactNotifier: MutualContactNotifier = mock()

    override val conversationWatcher: ConversationWatcher = mock()

    override val addressBookSyncWatcher: AddressBookSyncWatcher = mock()

    override val messageProcessor: MessageProcessor = mock()

    override val messageService: MessageService = mock()

    override val messageExpirationWatcher: MessageExpirationWatcher = mock()

    override val messageReadWatcher: MessageReadWatcher = mock()

    override val groupEventLoggerWatcher: GroupEventLoggerWatcher = mock()

    override val storageService: StorageService = mock()

    override val transferManager: TransferManager = mock()

    override val eventLogService: EventLogService = mock()

    val readMessageQueueIsEmptySubject: PublishSubject<Unit> = PublishSubject.create()

    override val readMessageQueueIsEmpty: Observable<Unit>
        get() = readMessageQueueIsEmptySubject

    override val messageDeletionWatcher: MessageDeletionWatcher = mock()
}
