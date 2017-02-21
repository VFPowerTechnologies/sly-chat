package io.slychat.messenger.android.activites

import android.app.ProgressDialog
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import io.slychat.messenger.android.AndroidApp
import io.slychat.messenger.android.R
import io.slychat.messenger.android.activites.services.impl.AndroidGroupServiceImpl
import io.slychat.messenger.android.activites.services.impl.AndroidMessengerServiceImpl
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.persistence.GroupId
import io.slychat.messenger.core.persistence.UserConversation
import io.slychat.messenger.services.messaging.GroupEvent
import nl.komponents.kovenant.ui.failUi
import nl.komponents.kovenant.ui.successUi
import org.slf4j.LoggerFactory

class CreateGroupActivity : BaseActivity() {
    private val log = LoggerFactory.getLogger(javaClass)

    private lateinit var app: AndroidApp
    private lateinit var groupService: AndroidGroupServiceImpl
    private lateinit var progressDialog: ProgressDialog

    private var contactData: MutableMap<UserId, Int> = mutableMapOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        log.debug("onCreate")

        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_create_group)

        app = AndroidApp.get(this)
        groupService = AndroidGroupServiceImpl(this)

        val actionBar = findViewById(R.id.create_group_toolbar) as Toolbar
        actionBar.title = resources.getString(R.string.create_group_title)
        setSupportActionBar(actionBar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        progressDialog = ProgressDialog(this)
        progressDialog.isIndeterminate = true
        progressDialog.setCancelable(false)
        progressDialog.setCanceledOnTouchOutside(false)
        progressDialog.setMessage(resources.getString(R.string.create_group_process_message))

        createEventListeners()
    }

    private fun init() {
        setAppActivity()
        setListeners()
        createContactList()
    }

    private fun createContactList() {
        val mContactList = findViewById(R.id.create_group_member_list) as LinearLayout
        val messengerService = AndroidMessengerServiceImpl(this)
        contactData = mutableMapOf()
        messengerService.fetchAllConversation() successUi { conversations ->
            messengerService.getSortedByNameConversation(conversations).forEach {
                mContactList.addView(createContactNode(it, mContactList))
            }
        }
    }

    private fun createContactNode(conversation: UserConversation, rootView: LinearLayout): View {
        val node = LayoutInflater.from(this).inflate(R.layout.create_group_contact_node, rootView, false)
        val checkbox = node.findViewById(R.id.checkBox) as CheckBox
        val id = View.generateViewId()

        contactData.put(conversation.contact.id, id)

        checkbox.id = id
        checkbox.text = conversation.contact.name

        return node
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item?.itemId) {
            android.R.id.home -> { finish() }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun createEventListeners() {
        val mSubmitBtn = findViewById(R.id.create_group_submit_btn) as Button
        mSubmitBtn.setOnClickListener {
            handleCreateGroup()
        }
    }

    private fun handleCreateGroup() {
        progressDialog.show()

        val mGroupName = findViewById(R.id.create_group_name) as EditText
        val name = mGroupName.text.toString()
        if (name.isEmpty()) {
            mGroupName.error = resources.getString(R.string.create_group_name_required_error)
            progressDialog.dismiss()
            return
        }

        val checkedContacts: MutableSet<UserId> = mutableSetOf()

        contactData.forEach {
            val checkbox = findViewById(it.value) as CheckBox
            if (checkbox.isChecked)
                checkedContacts.add(it.key)
        }

        if (checkedContacts.size > 0) {
            groupService.createGroup(name, checkedContacts) failUi {
                log.error("Something failed ${it.message}", it)
            }
        }
        else
            progressDialog.dismiss()
    }

    private fun handleGroupEvent(event: GroupEvent) {
        if (event is GroupEvent.Joined)
            loadGroupChat(event.id)
    }

    private fun loadGroupChat(groupId: GroupId) {
        progressDialog.dismiss()
        val intent = Intent(baseContext, ChatActivity::class.java)
        intent.putExtra("EXTRA_ISGROUP", true)
        intent.putExtra("EXTRA_ID", groupId.string)
        startActivity(intent)
        finish()
    }

    private fun setListeners() {
        groupService.addGroupListener { handleGroupEvent(it) }
    }

    private fun unsubscribeListener() {
        groupService.clearListeners()
    }

    override fun onPause() {
        super.onPause()
        unsubscribeListener()
    }

    override fun onResume() {
        super.onResume()
        init()
    }

    override fun onStop() {
        super.onStop()
        unsubscribeListener()
    }
}