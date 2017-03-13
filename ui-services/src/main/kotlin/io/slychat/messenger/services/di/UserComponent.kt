package io.slychat.messenger.services.di

import dagger.Subcomponent
import io.slychat.messenger.core.crypto.KeyVault
import io.slychat.messenger.core.persistence.*
import io.slychat.messenger.services.*
import io.slychat.messenger.services.auth.AuthTokenManager
import io.slychat.messenger.services.config.UserConfigService
import io.slychat.messenger.services.contacts.AddressBookOperationManager
import io.slychat.messenger.services.contacts.ContactsService
import io.slychat.messenger.services.crypto.MessageCipherService
import io.slychat.messenger.services.di.annotations.EmptyReadMessageQueue
import io.slychat.messenger.services.files.StorageService
import io.slychat.messenger.services.files.TransferManager
import io.slychat.messenger.services.messaging.*
import rx.Observable

/** Scoped to a user's login session. */
@UserScope
@Subcomponent(modules = arrayOf(UserModule::class, UserPersistenceModule::class))
interface UserComponent {
    val keyVaultPersistenceManager: KeyVaultPersistenceManager

    val persistenceManager: PersistenceManager

    val contactsPersistenceManager: ContactsPersistenceManager

    val messagePersistenceManager: MessagePersistenceManager

    val preKeyPersistenceManager: PreKeyPersistenceManager

    val contactsService: ContactsService

    val messengerService: MessengerService

    val groupService: GroupService

    val messageCipherService: MessageCipherService

    val notifierService: NotifierService

    val userLoginData: UserData

    val relayClientManager: RelayClientManager

    val preKeyManager: PreKeyManager

    val offlineMessageManager: OfflineMessageManager

    val authTokenManager: AuthTokenManager

    val userConfigService: UserConfigService

    val addressBookOperationManager: AddressBookOperationManager

    val accountInfoManager: AccountInfoManager

    val relayClock: RelayClock

    val sessionDataManager: SessionDataManager

    val mutualContactNotifier: MutualContactNotifier

    val conversationWatcher: ConversationWatcher

    val addressBookSyncWatcher: AddressBookSyncWatcher

    val messageProcessor: MessageProcessor

    val messageService: MessageService

    val messageExpirationWatcher: MessageExpirationWatcher

    val messageReadWatcher: MessageReadWatcher

    val messageDeletionWatcher: MessageDeletionWatcher

    val accountLocalInfoManager: AccountLocalInfoManager

    val groupEventLoggerWatcher: GroupEventLoggerWatcher

    val eventLogService: EventLogService

    val keyVault: KeyVault

    val storageService: StorageService

    val transferManager: TransferManager

    @get:EmptyReadMessageQueue
    val readMessageQueueIsEmpty: Observable<Unit>
}
