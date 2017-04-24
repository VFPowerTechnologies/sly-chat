package io.slychat.messenger.android.activites

import android.os.Bundle
import android.support.v7.widget.Toolbar
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import io.slychat.messenger.android.AndroidApp
import io.slychat.messenger.android.R
import io.slychat.messenger.android.activites.services.impl.AndroidGroupServiceImpl
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.persistence.ContactInfo
import io.slychat.messenger.core.persistence.GroupId
import io.slychat.messenger.core.persistence.GroupInfo
import nl.komponents.kovenant.ui.successUi
import org.slf4j.LoggerFactory

class GroupInfoActivity : BaseActivity() {
    companion object {
        val EXTRA_GROUP_ID = "io.slychat.messenger.android.activites.GroupInfoActivity.groupId"
    }
    private val log = LoggerFactory.getLogger(javaClass)

    private lateinit var app: AndroidApp

    private lateinit var groupId: GroupId
    private lateinit var groupService: AndroidGroupServiceImpl
    private var groupInfo: GroupInfo? = null
    private var membersInfo: Map<UserId, ContactInfo>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!successfullyLoaded)
            return

        app = AndroidApp.get(this)

        groupService = AndroidGroupServiceImpl(this)

        setContentView(R.layout.activity_group_info)

        val groupIdStringVal = intent.extras[EXTRA_GROUP_ID] as String
        groupId = GroupId(groupIdStringVal)

        val actionBar = findViewById(R.id.group_info_toolbar) as Toolbar
        actionBar.title = resources.getString(R.string.group_info_title)
        setSupportActionBar(actionBar)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun init() {
        getInfo(groupId)
        setAppActivity()
    }

    private fun getInfo(groupId: GroupId) {
        groupService.getGroupInfo(groupId) successUi { gInfo ->
            if (gInfo == null)
                finish()
            else
                displayInfo(gInfo)
        }

        groupService.getMembersInfo(groupId) successUi { mInfo ->
            displayMembers(mInfo)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item?.itemId) {
            android.R.id.home -> { finish() }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun displayInfo(gInfo: GroupInfo) {
        groupInfo = gInfo
        val groupNameView = findViewById(R.id.group_info_name_value) as TextView
        groupNameView.text = gInfo.name
    }

    private fun displayMembers(mInfo: Map<UserId, ContactInfo>) {
        membersInfo = mInfo
        val memberList = findViewById(R.id.group_info_members_list) as LinearLayout
        memberList.removeAllViews()
        mInfo.forEach {
            memberList.addView(createMemberNode(it.value))
        }
    }

    private fun createMemberNode(contactInfo: ContactInfo): View {
        val memberList = findViewById(R.id.group_info_members_list) as LinearLayout
        val node = LayoutInflater.from(this).inflate(R.layout.member_node, memberList, false)
        val memberEmail = node.findViewById(R.id.group_info_member_email) as TextView
        val memberName = node.findViewById(R.id.group_info_member_name) as TextView

        memberEmail.text = contactInfo.email
        memberName.text = contactInfo.name

        return node
    }

    override fun onResume() {
        super.onResume()
        init()
    }
}