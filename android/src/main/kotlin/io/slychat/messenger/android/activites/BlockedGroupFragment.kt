package io.slychat.messenger.android.activites

import android.content.DialogInterface
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import io.slychat.messenger.android.R
import io.slychat.messenger.android.activites.services.impl.GroupServiceImpl
import io.slychat.messenger.core.persistence.GroupId
import io.slychat.messenger.core.persistence.GroupInfo
import nl.komponents.kovenant.ui.failUi
import nl.komponents.kovenant.ui.successUi
import org.slf4j.LoggerFactory

class BlockedGroupFragment : Fragment() {
    private val log = LoggerFactory.getLogger(javaClass)

    private var v: View? = null
    private lateinit var groupService: GroupServiceImpl
    private var groupData: MutableMap<GroupId, Int> = mutableMapOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        v = inflater!!.inflate(R.layout.blocked_groups_fragment, container, false)

        groupService = GroupServiceImpl(activity as AppCompatActivity)

        fetchBlockedGroups()

        return v
    }

    fun fetchBlockedGroups() {
        groupService.getBlockedGroups() successUi { blocked ->
            displayBlockedGroups(blocked)
        } failUi {
            log.error("Failed to fetch blocked group list")
        }
    }

    private fun displayBlockedGroups(groups: List<GroupInfo>) {
        val groupList = v?.findViewById(R.id.blocked_group_list) as LinearLayout
        groupList.removeAllViews()
        if (groups.size > 0) {
            groupData = mutableMapOf()
            groups.forEach {
                groupList.addView(createGroupNode(it))
            }
        }
        else {
            val emptyNode = LayoutInflater.from(activity).inflate(R.layout.empty_block_node, groupList, false)
            val textNode = emptyNode.findViewById(R.id.empty_block_text) as TextView
            textNode.text = "No blocked group"
            groupList.addView(emptyNode)
        }
    }

    private fun createGroupNode(group: GroupInfo): View {
        val groupList = v?.findViewById(R.id.blocked_group_list) as LinearLayout
        val node = LayoutInflater.from(activity).inflate(R.layout.blocked_group_node, groupList, false)
        val name = node.findViewById(R.id.group_node_name) as TextView
        val initial = node.findViewById(R.id.group_node_initial) as TextView
        val id = View.generateViewId()
        val unblockButton = node.findViewById(R.id.unblock_button) as Button

        name.text = group.name
        initial.text = group.name[0].toString().toUpperCase()

        unblockButton.setOnClickListener {
            AlertDialog.Builder(activity).setTitle("Unblock group?").setMessage("Are you sure you want to unblock this group?")
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setPositiveButton(android.R.string.yes, DialogInterface.OnClickListener { dialog: DialogInterface, whichButton: Int ->
                        unblockGroup(group.id)
                    }).setNegativeButton(android.R.string.no, null).show()
        }

        node.id = id
        groupData.put(group.id, id)

        return node
    }

    private fun unblockGroup(groupId: GroupId) {
        groupService.unblockGroup(groupId) successUi {
            val groupList = v?.findViewById(R.id.blocked_group_list) as LinearLayout
            val nodeId = groupData[groupId]
            if (nodeId !== null) {
                val node = groupList.findViewById(nodeId)
                groupList.removeView(node)
            }
        } failUi {
            log.error("Failed to unblock group: $groupId")
        }
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
