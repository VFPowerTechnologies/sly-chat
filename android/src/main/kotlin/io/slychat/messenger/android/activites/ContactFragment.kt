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
import io.slychat.messenger.android.activites.services.impl.ContactServiceImpl
import io.slychat.messenger.android.activites.services.impl.MessengerServiceImpl
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.persistence.ContactInfo
import io.slychat.messenger.core.persistence.ConversationId
import io.slychat.messenger.core.persistence.UserConversation
import io.slychat.messenger.services.messaging.ConversationMessage
import nl.komponents.kovenant.ui.failUi
import nl.komponents.kovenant.ui.successUi
import org.slf4j.LoggerFactory

class ContactFragment : Fragment() {
    private val log = LoggerFactory.getLogger(javaClass)

    private lateinit var recentContactList : LinearLayout
    private lateinit var contactList : LinearLayout
    private lateinit var addContactBtn : FloatingActionButton

    private lateinit var messengerService: MessengerServiceImpl
    private lateinit var contactService: ContactServiceImpl

    private var contactListData: MutableMap<UserId, Int> = mutableMapOf()
    private var recentContactListData: MutableMap<UserId, Int> = mutableMapOf()

    private lateinit var conversations : MutableMap<UserId, UserConversation>

    private var v: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        v = inflater?.inflate(R.layout.address_book_contacts_fragment, container, false)

        contactList = v?.findViewById(R.id.contact_list) as LinearLayout
        recentContactList = v?.findViewById(R.id.recent_contact_list) as LinearLayout
        addContactBtn = v?.findViewById(R.id.contacts_add_contact_btn) as FloatingActionButton

        messengerService = MessengerServiceImpl(activity as AppCompatActivity)
        contactService = ContactServiceImpl(activity as AppCompatActivity)

        createEventListeners()

        return v
    }

    private fun createEventListeners () {
        addContactBtn.setOnClickListener {
            startActivity(Intent(activity.baseContext, AddContactActivity::class.java))
        }
    }

    fun fetchConversations () {
        messengerService.fetchAllConversation() successUi {
            conversations = it
            displayContacts()
        }
    }

    private fun displayContacts () {
        contactList.removeAllViews()
        recentContactList.removeAllViews()
        if (conversations.isNotEmpty()) {
            messengerService.getSortedByNameConversation(conversations).forEach {
                contactList.addView(createContactNode(it.contact))
            }
            messengerService.getActualSortedConversation(conversations).forEach {
                recentContactList.addView(createRecentContactNode(it.contact.id, it.contact.name))
            }
        }
        else {
            LayoutInflater.from(activity).inflate(R.layout.contact_empty_node, contactList, true)
        }
    }

    private fun createContactNode (contact: ContactInfo): View {
        val contactNode = LayoutInflater.from(activity).inflate(R.layout.contact_node, contactList, false)
        val contactNameNode = contactNode.findViewById(R.id.contact_name) as TextView
        val contactEmailNode = contactNode.findViewById(R.id.contact_email) as TextView
        contactNameNode.text = contact.name
        contactEmailNode.text = contact.email

        contactNode.setOnClickListener {
            loadChatPageFor(contact.id)
        }

        val nodeId = View.generateViewId()
        contactListData.put(contact.id, nodeId)
        contactNode.id = nodeId

        return contactNode
    }

    private fun createRecentContactNode (id: UserId, name: String): View {
        val recentContactNode = LayoutInflater.from(activity).inflate(R.layout.recent_contact_node, recentContactList, false)
        val contactNameNode = recentContactNode.findViewById(R.id.contact_name) as TextView
        val contactInitial = recentContactNode.findViewById(R.id.contact_initial) as TextView
        contactNameNode.text = name
        contactInitial.text = name[0].toString()

        recentContactNode.setOnClickListener {
            loadChatPageFor(id)
        }

        val nodeId = View.generateViewId()
        recentContactListData.put(id, nodeId)
        recentContactNode.id = nodeId

        return recentContactNode
    }


    private fun loadChatPageFor (id: UserId) {
        val intent = Intent(activity.baseContext, ChatActivity::class.java)
        intent.putExtra("EXTRA_ISGROUP", false)
        intent.putExtra("EXTRA_ID", id.long)
        startActivity(intent)
    }

    fun addedContact(contact: ContactInfo) {
        if (contactListData[contact.id] == null) {
            contactList.addView(createContactNode(contact))
        }
    }

    fun removeContactFromList (userId: UserId) {
        val nodeId = contactListData[userId]
        val recentNodeId = recentContactListData[userId]
        if (nodeId != null) {
            contactListData.remove(userId)
            contactList.removeView(v?.findViewById(nodeId))
        }

        if (recentNodeId != null) {
            recentContactListData.remove(userId)
            recentContactList.removeView(v?.findViewById(recentNodeId))
        }
    }

    fun updateContactFromList (old: ContactInfo, new: ContactInfo) {
        val nodeId = contactListData[old.id]
        val recentNodeId = recentContactListData[old.id]
        if (nodeId != null) {
            val node = contactList.findViewById(nodeId) as LinearLayout
            val name = node.findViewById(R.id.contact_name) as TextView
            val email = node.findViewById(R.id.contact_email) as TextView
            name.text = new.name
            email.text = new.email
        }
        else {
            contactList.addView(createContactNode(new))
        }

        if (recentNodeId != null) {
            val recentNode = recentContactList.findViewById(recentNodeId) as LinearLayout
            val initial = recentNode.findViewById(R.id.contact_initial) as TextView
            val recentName = recentNode.findViewById(R.id.contact_name) as TextView

            initial.text = new.name[0].toString()
            recentName.text = new.name
        }
    }

    fun handleNewMessage (message: ConversationMessage) {
        val conversationId = message.conversationId
        if (conversationId is ConversationId.User) {
            val contactId = conversationId.id
            val nodeId = recentContactListData[contactId]
            if (nodeId != null) {
                recentContactList.removeView(v?.findViewById(nodeId))
            }

            val contact = contactService.contactList[contactId]
            if (contact != null) {
                recentContactList.addView(createRecentContactNode(contact.id, contact.name), 0)
            }
            else {
                contactService.getContact(contactId) successUi {
                    if (it != null) {
                        recentContactList.addView(createRecentContactNode(it.id, it.name), 0)
                    }
                } failUi  {
                    log.error("Failed fetching the contact")
                }
            }

        }
    }

    override fun onResume() {
        super.onResume()
        log.debug("onResume")
        fetchConversations()
    }

}
