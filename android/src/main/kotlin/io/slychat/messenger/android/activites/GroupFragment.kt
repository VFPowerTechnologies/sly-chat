package io.slychat.messenger.android.activites

import android.content.Intent
import android.os.Bundle
import android.support.design.widget.FloatingActionButton
import android.support.v4.app.Fragment
import android.support.v7.app.AppCompatActivity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import io.slychat.messenger.android.R
import io.slychat.messenger.android.activites.services.impl.AndroidGroupServiceImpl
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.persistence.GroupConversation
import io.slychat.messenger.core.persistence.GroupId
import io.slychat.messenger.services.messaging.GroupEvent
import nl.komponents.kovenant.ui.successUi
import org.slf4j.LoggerFactory

class GroupFragment : Fragment() {
    private val log = LoggerFactory.getLogger(javaClass)

    private var v: View? = null

    private lateinit var groupService: AndroidGroupServiceImpl

    private var groupData: MutableMap<GroupId, Int> = mutableMapOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        v = inflater!!.inflate(R.layout.address_book_groups_fragment, container, false)

        groupService = AndroidGroupServiceImpl(activity as AppCompatActivity)

        createEventListeners()

        return v
    }

    private fun createEventListeners() {
        val createGroupBtn = v?.findViewById(R.id.address_book_create_group_btn) as FloatingActionButton
        createGroupBtn.setOnClickListener {
            startActivity(Intent(activity.baseContext, CreateGroupActivity::class.java))
        }
    }

    fun fetchGroups() {
        groupService.fetchGroupConversations() successUi { convo ->
            displayGroups(convo)
        }
    }

    fun handleGroupEvent(event: GroupEvent) {
        when (event) {
            is GroupEvent.Joined -> { handleJoinedEvent(event) }
            is GroupEvent.Parted -> { handlePartedEvent(event) }
            is GroupEvent.Blocked -> { handleBlockedEvent(event) }
            is GroupEvent.MembershipChanged -> { handleMembershipChanged(event) }
        }
    }

    private fun handleJoinedEvent(event: GroupEvent.Joined) {
        fetchGroups()
    }

    private fun handlePartedEvent(event: GroupEvent.Parted) {
        val groupList = v?.findViewById(R.id.address_book_group_list) as LinearLayout
        val nodeId = groupData[event.id]
        if (nodeId != null) {
            val node = groupList.findViewById(nodeId)
            groupList.removeView(node)
            groupData.remove(event.id)
        }
    }

    private fun handleBlockedEvent(event: GroupEvent.Blocked) {
        val groupList = v?.findViewById(R.id.address_book_group_list) as LinearLayout
        val nodeId = groupData[event.id]
        if (nodeId != null) {
            val node = groupList.findViewById(nodeId)
            groupList.removeView(node)
            groupData.remove(event.id)
        }
    }

    private fun handleMembershipChanged(event: GroupEvent.MembershipChanged) {
        fetchGroups()
    }

    private fun displayGroups(groupConvo: MutableMap<GroupId, GroupConversation>) {
        val groupList = v?.findViewById(R.id.address_book_group_list) as LinearLayout
        groupList.removeAllViews()
        if (groupConvo.count() > 0) {
            groupData = mutableMapOf()
            groupConvo.forEach {
                groupList.addView(createGroupNode(it.value))
            }

            createMemberList()
        }
        else {
            groupList.addView(LayoutInflater.from(activity).inflate(R.layout.empty_group_node, groupList, false))
        }
    }

    private fun createGroupNode(group: GroupConversation): View {
        val groupList = v?.findViewById(R.id.address_book_group_list) as LinearLayout
        val node = LayoutInflater.from(activity).inflate(R.layout.group_node, groupList, false)
        val name = node.findViewById(R.id.group_node_name) as TextView
        val initial = node.findViewById(R.id.group_node_initial) as TextView
        val id = View.generateViewId()

        name.text = group.group.name
        initial.text = group.group.name[0].toString().toUpperCase()

        node.setOnClickListener {
            loadGroupChat(group.group.id)
        }

        node.id = id
        groupData.put(group.group.id, id)

        return node
    }

    private fun loadGroupChat(id: GroupId) {
        val intent = Intent(activity.baseContext, ChatActivity::class.java)
        intent.putExtra("EXTRA_ISGROUP", true)
        intent.putExtra("EXTRA_ID", id.string)
        startActivity(intent)
    }

    private fun createMemberList() {
        groupData.forEach {
            val groupId = it.key
            val nodeId = it.value
            groupService.getMembers(groupId) successUi { userList ->
                displayMembers(nodeId, userList)
            }
        }
    }

    private fun displayMembers(nodeId: Int, userList: Set<UserId>) {
        val contactActivity = activity as ContactActivity
        val node = v?.findViewById(nodeId) as LinearLayout
        val members = node.findViewById(R.id.group_node_members) as TextView

        var memberList = ""
        userList.forEach {
            val c  = contactActivity.contactList[it]
            if(c != null) {
                memberList += c.name + ", "
            }
        }

        members.text = memberList.dropLast(2)
    }

    override fun onStart() {
        super.onStart()
        log.debug("onStart")
    }

    override fun onPause() {
        super.onPause()
        log.debug("onPause")
    }

    override fun onResume() {
        super.onResume()
        fetchGroups()
        log.debug("onResume")
    }

    override fun onStop() {
        super.onStop()
        log.debug("onStop")
    }

    override fun onDestroy() {
        super.onDestroy()
        log.debug("onDestroy")
    }

}
