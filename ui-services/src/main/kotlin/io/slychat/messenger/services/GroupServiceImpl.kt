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
    private val addressBookOperationManager: AddressBookOperationManager
) : GroupService {
    private val log = LoggerFactory.getLogger(javaClass)

    private val groupEventSubject = PublishSubject.create<GroupEvent>()
    override val groupEvents: Observable<GroupEvent> = groupEventSubject

    override fun getGroups(): Promise<List<GroupInfo>, Exception> {
        return groupPersistenceManager.getList()
    }

    override fun getMembers(groupId: GroupId): Promise<Set<UserId>, Exception> {
        return groupPersistenceManager.getMembers(groupId)
    }

    override fun getGroupConversations(): Promise<List<GroupConversation>, Exception> {
        return groupPersistenceManager.getAllConversations()
    }

    override fun getInfo(groupId: GroupId): Promise<GroupInfo?, Exception> {
        return groupPersistenceManager.getInfo(groupId)
    }

    override fun addMessage(groupId: GroupId, groupMessageInfo: GroupMessageInfo): Promise<GroupMessageInfo, Exception> {
        return groupPersistenceManager.addMessage(groupId, groupMessageInfo)
    }

    override fun markMessageAsDelivered(groupId: GroupId, messageId: String): Promise<GroupMessageInfo, Exception> {
        return groupPersistenceManager.markMessageAsDelivered(groupId, messageId)
    }

    override fun isUserMemberOf(groupId: GroupId, userId: UserId): Promise<Boolean, Exception> {
        return groupPersistenceManager.isUserMemberOf(groupId, userId)
    }

    override fun markConversationAsRead(groupId: GroupId): Promise<Unit, Exception> {
        return groupPersistenceManager.markConversationAsRead(groupId)
    }

    override fun getMembersWithInfo(groupId: GroupId): Promise<List<ContactInfo>, Exception> {
        return groupPersistenceManager.getMembers(groupId) bind {
            contactsPersistenceManager.get(it)
        }
    }

    override fun join(groupInfo: GroupInfo, members: Set<UserId>): Promise<Unit, Exception> {
        return addressBookOperationManager.runOperation {
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

    override fun getLastMessages(groupId: GroupId, startingAt: Int, count: Int): Promise<List<GroupMessageInfo>, Exception> {
        return groupPersistenceManager.getLastMessages(groupId, startingAt, count)
    }

    override fun deleteAllMessages(groupId: GroupId): Promise<Unit, Exception> {
        return groupPersistenceManager.deleteAllMessages(groupId)
    }

    override fun deleteMessages(groupId: GroupId, messageIds: Collection<String>): Promise<Unit, Exception> {
        return groupPersistenceManager.deleteMessages(groupId, messageIds)
    }

    private fun triggerRemoteSync() {
        addressBookOperationManager.withCurrentSyncJob { doRemoteSync() }
    }

    override fun addMembers(groupId: GroupId, users: Set<UserId>): Promise<Unit, Exception> {
        return addressBookOperationManager.runOperation {
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