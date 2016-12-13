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
import io.slychat.messenger.android.activites.services.impl.GroupServiceImpl
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.persistence.GroupConversation
import io.slychat.messenger.core.persistence.GroupId
import nl.komponents.kovenant.ui.successUi
import org.slf4j.LoggerFactory

class GroupFragment : Fragment() {
    private val log = LoggerFactory.getLogger(javaClass)

    private var v: View? = null

    private lateinit var groupService: GroupServiceImpl

    private lateinit var groupList: LinearLayout
    private lateinit var createGroupBtn: FloatingActionButton
    private var groupData: MutableMap<GroupId, Int> = mutableMapOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        v = inflater!!.inflate(R.layout.address_book_groups_fragment, container, false)

        groupList = v?.findViewById(R.id.address_book_group_list) as LinearLayout
        createGroupBtn = v?.findViewById(R.id.address_book_create_group_btn) as FloatingActionButton

        groupService = GroupServiceImpl(activity as AppCompatActivity)

        fetchGroups()
        createEventListeners()

        return v
    }

    private fun createEventListeners() {
        createGroupBtn.setOnClickListener {
            startActivity(Intent(activity.baseContext, CreateGroupActivity::class.java))
        }
    }

    fun fetchGroups() {
        groupService.fetchGroupConversations() successUi { convo ->
            displayGroups(convo)
        }
    }

    private fun displayGroups(groupConvo: MutableMap<GroupId, GroupConversation>) {
        groupList.removeAllViews()
        groupData = mutableMapOf()
        groupConvo.forEach {
            groupList.addView(createGroupNode(it.value))
        }

        createMemberList()
    }

    private fun createGroupNode(group: GroupConversation): View {
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
