package io.slychat.messenger.android.activites

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.support.v7.widget.Toolbar
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.view.ViewGroup.LayoutParams
import io.slychat.messenger.android.AndroidApp
import io.slychat.messenger.android.R
import io.slychat.messenger.android.activites.services.impl.ContactServiceImpl
import io.slychat.messenger.android.activites.services.impl.GroupServiceImpl
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.persistence.ContactInfo
import io.slychat.messenger.core.persistence.ConversationId
import io.slychat.messenger.core.persistence.GroupInfo
import nl.komponents.kovenant.ui.failUi
import nl.komponents.kovenant.ui.successUi
import org.slf4j.LoggerFactory

class MessageInfoActivity : BaseActivity() {
    companion object {
        val EXTRA_MESSAGE_ID = "io.slychat.messenger.android.activities.MessageInfoActivity.messageId"
        val EXTRA_SENT_TIME = "io.slychat.messenger.android.activities.MessageInfoActivity.sentTime"
        val EXTRA_RECEIVED_TIME = "io.slychat.messenger.android.activities.MessageInfoActivity.receivedTime"
        val EXTRA_IS_SENT = "io.slychat.messenger.android.activities.MessageInfoActivity.isSent"
        val EXTRA_CONVERSTATION_ID = "io.slychat.messenger.android.activities.MessageInfoActivity.conversationId"
        val EXTRA_SPEAKER_ID = "io.slychat.messenger.android.activities.MessageInfoActivity.speakerId"
    }
    private val log = LoggerFactory.getLogger(javaClass)

    private lateinit var app: AndroidApp
    private lateinit var contactService: ContactServiceImpl
    private lateinit var groupService: GroupServiceImpl

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        log.debug("onCreate")

        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_message_info)

        app = AndroidApp.get(this)
        contactService = ContactServiceImpl(this)
        groupService = GroupServiceImpl(this)

        val actionBar = findViewById(R.id.message_info_toolbar) as Toolbar
        actionBar.title = "Message Information"
//        actionBar.title = resources.getString(R.string.profile_title)
        setSupportActionBar(actionBar)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun init() {
        fetchInfo(intent.extras)
    }

    private fun fetchInfo(bundle: Bundle) {
        val cId = ConversationId.fromString(bundle.get(EXTRA_CONVERSTATION_ID) as String)
        if (cId is ConversationId.User) {
            contactService.getContact(cId.id) successUi { contactInfo ->
                if (contactInfo != null)
                        displaySingleMessageInfo(contactInfo, bundle)
                else {
                    log.error("Message Info failed, user id: ${cId.id} was not found")
                    finish()
                }
            } failUi {
                log.error(it.message)
                finish()
            }
        }
        else if (cId is ConversationId.Group) {
            groupService.getGroupInfo(cId.id) successUi { groupInfo ->
                if (groupInfo != null) {
                    groupService.getMembersInfo(cId.id) successUi { members ->
                        displayGroupMessageInfo(groupInfo, members, bundle)
                    } failUi {
                        log.error(it.message)
                        finish()
                    }
                }
                else {
                    log.error("Message Info failed, group id: ${cId.id} was not found")
                    finish()
                }
            } failUi {
                log.error(it.message)
                finish()
            }
        }
    }

    private fun displaySingleMessageInfo(contactInfo: ContactInfo, bundle: Bundle) {
        val isSent = bundle.get(EXTRA_IS_SENT) as Boolean
        val messageId = bundle.get(EXTRA_MESSAGE_ID) as String

        val mInfoContainer = findViewById(R.id.message_info_container) as LinearLayout
        if (isSent) {
            val mIsSent = createInfoTitle("Message was sent to:")
            mIsSent.setPadding(0, 0, 0, pxToDp(16))

            mInfoContainer.addView(mIsSent)
        }


        val mContactName = createInformationNode("Contact name:", contactInfo.name)
        mInfoContainer.addView(mContactName)

        val mContactEmail = createInformationNode("Contact email:", contactInfo.email)
        mInfoContainer.addView(mContactEmail)

        val mContactPubKey = createInformationNode("Contact public key:", contactInfo.publicKey)
        mInfoContainer.addView(mContactPubKey)

        val mMessageId = createInformationNode("Message id:", messageId)
        mInfoContainer.addView(mMessageId)

        if (isSent) {
            val sentTime = bundle.get(EXTRA_SENT_TIME) as Long
            val mSentTime = createInformationNode("Sent time:", sentTime.toString())
            mInfoContainer.addView(mSentTime)
        }
        else {
            val receivedTime = bundle.get(EXTRA_RECEIVED_TIME) as Long
            if (receivedTime > 0) {
                val mReceivedTime = createInformationNode("Received time:", receivedTime.toString())
                mInfoContainer.addView(mReceivedTime)
            }
        }
    }

    private fun displayGroupMessageInfo(groupInfo: GroupInfo, members: Map<UserId, ContactInfo>, bundle: Bundle) {
        val isSent = bundle.get(EXTRA_IS_SENT) as Boolean
        val messageId = bundle.get(EXTRA_MESSAGE_ID) as String
        val speakerId = bundle.get(EXTRA_SPEAKER_ID) as Long?
        val mInfoContainer = findViewById(R.id.message_info_container) as LinearLayout

        if (isSent) {
            val mSentTo = createInformationNode("Message was sent to group:", groupInfo.name)
            mInfoContainer.addView(mSentTo)
        }
        else {
            val mReceivedIn = createInformationNode("Message received in group:", groupInfo.name)
            mInfoContainer.addView(mReceivedIn)

            if (speakerId != null) {
                val fromContact = members[UserId(speakerId)]
                if (fromContact != null) {
                    val mFromContactName = createInformationNode("Contact name:", fromContact.name)
                    mInfoContainer.addView(mFromContactName)

                    val mFromContactEmail = createInformationNode("Contact email:", fromContact.email)
                    mInfoContainer.addView(mFromContactEmail)
                }
            }
        }

        val mMessageId = createInformationNode("Message id:", messageId)
        mInfoContainer.addView(mMessageId)

        if (isSent) {
            val sentTime = bundle.get(EXTRA_SENT_TIME) as Long
            val mSentTime = createInformationNode("Sent time:", sentTime.toString())
            mInfoContainer.addView(mSentTime)
        }
        else {
            val receivedTime = bundle.get(EXTRA_RECEIVED_TIME) as Long
            if (receivedTime > 0) {
                val mReceivedTime = createInformationNode("Received time:", receivedTime.toString())
                mInfoContainer.addView(mReceivedTime)
            }
        }

        val mGroupId = createInformationNode("Group id:", groupInfo.id.string)
        mInfoContainer.addView(mGroupId)

        val mMembers = createMembersNode(members)
        mInfoContainer.addView(mMembers)
    }

    private fun createMembersNode(members: Map<UserId, ContactInfo>): View {
        val node = LinearLayout(this)
        node.orientation = LinearLayout.VERTICAL
        val titleNode = createInfoTitle("Group members:")
        node.addView(titleNode)
        members.forEach { member ->
            node.addView(createMemberInfo(member.value.name, member.value.email))
        }

        node.setPadding(0, 0, 0, pxToDp(16))

        return node
    }

    private fun createInformationNode(title: String, text: String): View {
        val node = LinearLayout(this)
        val titleNode = createInfoTitle(title)
        val infoData = createInfoData(text)
        node.addView(titleNode)
        node.addView(infoData)
        node.orientation = LinearLayout.VERTICAL
        node.setPadding(0, 0, 0, pxToDp(16))

        return node
    }

    private fun createInfoTitle(text: String): TextView {
        val view = TextView(this)
        view.text = text
        setTextAppearance(view, this, android.R.style.TextAppearance_Medium)
        view.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)

        return view
    }

    private fun createInfoData(text: String): TextView {
        val view = TextView(this)
        view.text = text
        view.setPadding(pxToDp(10), 0, pxToDp(10), 0)
        setTextAppearance(view, this, android.R.style.TextAppearance_Small)
        view.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)

        return view
    }

    private fun createMemberInfo(name: String, email: String): View {
        val node = LinearLayout(this)
        node.orientation = LinearLayout.VERTICAL

        val lp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)

        val mName = TextView(this)
        mName.text = name
        mName.setPadding(pxToDp(10), 0, pxToDp(10), 0)
        setTextAppearance(mName, this, android.R.style.TextAppearance_Small)
        mName.layoutParams = lp
        node.addView(mName)

        val mEmail = TextView(this)
        mEmail.text = email
        mEmail.setPadding(pxToDp(16), 0, pxToDp(16), 0)
        setTextAppearance(mEmail, this, android.R.style.TextAppearance_Small)
        mEmail.layoutParams = lp
        node.addView(mEmail)

        node.setPadding(0, 0, 0, pxToDp(5))

        return node
    }

    private fun setTextAppearance(view: TextView, context: Context, resId: Int) {
        if (Build.VERSION.SDK_INT < 23) {
            view.setTextAppearance(context, resId)
        }
        else {
            view.setTextAppearance(resId)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item?.itemId) {
            android.R.id.home -> { finish() }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onResume() {
        super.onResume()
        init()
    }
}