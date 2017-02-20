package io.slychat.messenger.android.activites

import android.content.DialogInterface
import android.content.Intent
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
import io.slychat.messenger.android.activites.services.impl.ContactServiceImpl
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.persistence.ContactInfo
import io.slychat.messenger.services.contacts.ContactEvent
import nl.komponents.kovenant.ui.failUi
import nl.komponents.kovenant.ui.successUi
import org.slf4j.LoggerFactory

class BlockedContactFragment : Fragment() {
    private val log = LoggerFactory.getLogger(javaClass)
    private lateinit var contactService: ContactServiceImpl

    private var contactListData: MutableMap<UserId, Int> = mutableMapOf()

    private var v: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        v = inflater?.inflate(R.layout.blocked_contacts_fragment, container, false)
        contactService = ContactServiceImpl(activity as AppCompatActivity)

        fetchBlockedContact()

        return v
    }

    fun fetchBlockedContact() {
        contactService.getBlockedContacts() successUi { blocked ->
            displayBlockedContacts(blocked)
        } failUi {
            log.error("Something failed ${it.message}", it)
        }
    }

    fun handleContactEvent(event: ContactEvent) {
        when (event) {
            is ContactEvent.Blocked -> {
                addNewBlockedContact(event.userId)
            }
            is ContactEvent.Unblocked -> {
                handleUnblockedContactEvent(event.userId)
            }
        }
    }

    private fun addNewBlockedContact(userId: UserId) {
        val contactList = v?.findViewById(R.id.blocked_contact_list) as LinearLayout
        contactService.getContact(userId) successUi { contact ->
            if (contact !== null)
                contactList.addView(createContactNode(contact))
        } failUi {
            log.error("Something failed ${it.message}", it)
        }
    }

    private fun displayBlockedContacts(contacts: List<ContactInfo>) {
        val contactList = v?.findViewById(R.id.blocked_contact_list) as LinearLayout
        contactList.removeAllViews()
        if (contacts.size > 0) {
            contacts.forEach {
                contactList.addView(createContactNode(it))
            }
        }
        else {
            val emptyNode = LayoutInflater.from(activity).inflate(R.layout.empty_block_node, contactList, false)
            val textNode = emptyNode.findViewById(R.id.empty_block_text) as TextView
            textNode.text = resources.getString(R.string.blocked_contacts_empty)
            contactList.addView(emptyNode)
        }
    }

    private fun createContactNode (contact: ContactInfo): View {
        val contactList = v?.findViewById(R.id.blocked_contact_list) as LinearLayout
        val contactNode = LayoutInflater.from(activity).inflate(R.layout.blocked_contact_node, contactList, false)
        val contactNameNode = contactNode.findViewById(R.id.contact_name) as TextView
        val contactEmailNode = contactNode.findViewById(R.id.contact_email) as TextView
        val unblockButton = contactNode.findViewById(R.id.unblock_button) as Button
        contactNameNode.text = contact.name
        contactEmailNode.text = contact.email

        unblockButton.setOnClickListener {
            AlertDialog.Builder(activity).setTitle(resources.getString(R.string.unblock_contact_dialog_title)).setMessage(resources.getString(R.string.unblock_contact_dialog_message))
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setPositiveButton(android.R.string.yes, DialogInterface.OnClickListener { dialog: DialogInterface, whichButton: Int ->
                        unblockContact(contact.id)
                    }).setNegativeButton(android.R.string.no, null).show()
        }

        val nodeId = View.generateViewId()
        contactListData.put(contact.id, nodeId)
        contactNode.id = nodeId

        return contactNode
    }

    private fun unblockContact(userId: UserId) {
        contactService.unblockContact(userId) successUi {
            val contactList = v?.findViewById(R.id.blocked_contact_list) as LinearLayout
            val nodeId = contactListData[userId]
            if (nodeId !== null) {
                val node = contactList.findViewById(nodeId)
                contactList.removeView(node)
            }
        } failUi {
            log.error("Something failed ${it.message}", it)
        }
    }

    private fun handleUnblockedContactEvent(userId: UserId) {
        contactService.getContact(userId) successUi { contactInfo ->
            if (contactInfo !== null) {
                AlertDialog.Builder(activity).setTitle(resources.getString(R.string.unblock_contact_add_back_dialog_title)).setMessage(contactInfo.email + resources.getString(R.string.unblock_contact_add_back_dialog_message))
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setPositiveButton("YES", DialogInterface.OnClickListener { dialog: DialogInterface, whichButton: Int ->
                            addContact(contactInfo)
                        }).setNegativeButton("NO", null).show()
            }
        }
    }

    private fun addContact(contactInfo: ContactInfo) {
        contactService.allowAll(contactInfo.id) successUi { success ->
            loadChatPageFor(contactInfo.id)
        } failUi {
            log.error("Something failed ${it.message}", it)
        }
    }

    private fun loadChatPageFor (id: UserId) {
        val intent = Intent(activity.baseContext, ChatActivity::class.java)
        intent.putExtra("EXTRA_ISGROUP", false)
        intent.putExtra("EXTRA_ID", id.long)
        startActivity(intent)
    }
}
