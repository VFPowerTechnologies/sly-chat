package io.slychat.messenger.services

import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.persistence.*
import io.slychat.messenger.services.contacts.AddressBookOperationManager
import io.slychat.messenger.services.messaging.GroupEvent
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.bind
import nl.komponents.kovenant.ui.successUi
import org.slf4j.LoggerFactory
import rx.Observable
import rx.subjects.PublishSubject

//TODO should move the group message generation to here; in a hurry now so do it later
class GroupServiceImpl(
    private val groupPersistenceManager: GroupPersistenceManager,
    private val contactsPersistenceManager: ContactsPersistenceManager,
    private val addressBookOperationManager: AddressBookOperationManager,
    private val messagePersistenceManager: MessagePersistenceManager
) : GroupService {
    private val log = LoggerFactory.getLogger(javaClass)

    private val groupEventSubject = PublishSubject.create<GroupEvent>()
    override val groupEvents: Observable<GroupEvent>
        get() = groupEventSubject

    override fun getGroups(): Promise<List<GroupInfo>, Exception> {
        return groupPersistenceManager.getList()
    }

    override fun getMembers(groupId: GroupId): Promise<Set<UserId>, Exception> {
        return groupPersistenceManager.getMembers(groupId)
    }

    override fun getNonBlockedMembers(groupId: GroupId): Promise<Set<UserId>, Exception> {
        return groupPersistenceManager.getNonBlockedMembers(groupId)
    }

    override fun getGroupConversations(): Promise<List<GroupConversation>, Exception> {
        return messagePersistenceManager.getAllGroupConversations()
    }

    override fun getInfo(groupId: GroupId): Promise<GroupInfo?, Exception> {
        return groupPersistenceManager.getInfo(groupId)
    }

    override fun addMessage(groupId: GroupId, conversationMessageInfo: ConversationMessageInfo): Promise<Unit, Exception> {
        return messagePersistenceManager.addMessage(groupId.toConversationId(), conversationMessageInfo)
    }

    override fun markMessageAsDelivered(groupId: GroupId, messageId: String, timestamp: Long): Promise<ConversationMessageInfo?, Exception> {
        return messagePersistenceManager.markMessageAsDelivered(groupId.toConversationId(), messageId, timestamp)
    }

    override fun isUserMemberOf(groupId: GroupId, userId: UserId): Promise<Boolean, Exception> {
        return groupPersistenceManager.isUserMemberOf(groupId, userId)
    }

    override fun markConversationAsRead(groupId: GroupId): Promise<Unit, Exception> {
        return messagePersistenceManager.markConversationAsRead(groupId.toConversationId())
    }

    override fun getMembersWithInfo(groupId: GroupId): Promise<List<ContactInfo>, Exception> {
        return groupPersistenceManager.getMembers(groupId) bind {
            contactsPersistenceManager.get(it)
        }
    }

    override fun join(groupInfo: GroupInfo, members: Set<UserId>): Promise<Unit, Exception> {
        return addressBookOperationManager.runOperation {
            log.debug("Joining group: {}", groupInfo.id)

            groupPersistenceManager.join(groupInfo, members) mapUi { wasJoined ->
                if (wasJoined) {
                    log.info("Joined new group {} with members={}", groupInfo.id, members)
                    groupEventSubject.onNext(GroupEvent.NewGroup(groupInfo.id, members))
                    triggerRemoteSync()
                }
                else
                    log.info("Group {} was already joined", groupInfo.id)
            }
        }
    }

    override fun part(groupId: GroupId): Promise<Boolean, Exception> {
        return addressBookOperationManager.runOperation {
            log.debug("Parting group: {}", groupId)

            groupPersistenceManager.part(groupId) successUi { wasParted ->
                if (wasParted) {
                    log.info("Parted group {}", groupId)
                    triggerRemoteSync()
                }
                else
                    log.info("Group {} was already parted", groupId)
            }
        }
    }

    override fun block(groupId: GroupId): Promise<Unit, Exception> {
        return addressBookOperationManager.runOperation {
            log.debug("Blocking group: {}", groupId)

            groupPersistenceManager.block(groupId) mapUi { wasBlocked ->
                if (wasBlocked) {
                    log.info("Group {} was blocked", groupId)
                    triggerRemoteSync()
                }
                else
                    log.info("Group {} was already blocked", groupId)

            }
        }
    }

    override fun unblock(groupId: GroupId): Promise<Unit, Exception> {
        return addressBookOperationManager.runOperation {
            log.debug("Unblocking group: {}", groupId)

            groupPersistenceManager.unblock(groupId) mapUi { wasUnblocked ->
                if (wasUnblocked) {
                    log.info("Group {} was unblocked", groupId)
                    triggerRemoteSync()
                }
                else
                    log.info("Group {} was already unblocked")
            }
        }
    }

    override fun getBlockList(): Promise<Set<GroupId>, Exception> {
        return groupPersistenceManager.getBlockList()
    }

    override fun getLastMessages(groupId: GroupId, startingAt: Int, count: Int): Promise<List<ConversationMessageInfo>, Exception> {
        return messagePersistenceManager.getLastMessages(groupId.toConversationId(), startingAt, count)
    }

    override fun deleteAllMessages(groupId: GroupId): Promise<Unit, Exception> {
        return messagePersistenceManager.deleteAllMessages(groupId.toConversationId())
    }

    override fun deleteMessages(groupId: GroupId, messageIds: Collection<String>): Promise<Unit, Exception> {
        return messagePersistenceManager.deleteMessages(groupId.toConversationId(), messageIds)
    }

    private fun triggerRemoteSync() {
        addressBookOperationManager.withCurrentSyncJob { doPull() }
    }

    override fun addMembers(groupId: GroupId, users: Set<UserId>): Promise<Unit, Exception> {
        return addressBookOperationManager.runOperation {
            log.debug("Adding new members {} for group: {}", users, groupId)

            groupPersistenceManager.addMembers(groupId, users) mapUi { wasAdded ->
                if (wasAdded.isNotEmpty()) {
                    log.info("Users {} joined group {}", wasAdded, groupId)
                    groupEventSubject.onNext(GroupEvent.Joined(groupId, wasAdded))
                    triggerRemoteSync()
                }
            }
        }
    }

    override fun removeMember(groupId: GroupId, userId: UserId): Promise<Unit, Exception> {
        return addressBookOperationManager.runOperation {
            log.debug("Removing {} from group: {}", userId, groupId)

            groupPersistenceManager.removeMember(groupId, userId) mapUi { wasRemoved ->
                if (wasRemoved) {
                    log.info("User {} has left group {}", userId, groupId.string)
                    groupEventSubject.onNext(GroupEvent.Parted(groupId, userId))
                    triggerRemoteSync()
                }
            }
        }
    }
}