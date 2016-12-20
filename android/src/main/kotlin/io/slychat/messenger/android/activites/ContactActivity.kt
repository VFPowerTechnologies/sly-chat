package io.slychat.messenger.android.activites

import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentPagerAdapter
import android.support.v4.view.ViewPager
import android.support.v7.app.AppCompatActivity
import android.view.WindowManager
import android.support.v7.widget.Toolbar
import android.view.MenuItem
import android.support.design.widget.TabLayout
import io.slychat.messenger.android.AndroidApp
import io.slychat.messenger.android.R
import io.slychat.messenger.android.activites.services.impl.ContactServiceImpl
import io.slychat.messenger.android.activites.services.impl.GroupServiceImpl
import io.slychat.messenger.android.activites.services.impl.MessengerServiceImpl
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.persistence.ContactInfo
import io.slychat.messenger.services.contacts.ContactEvent
import nl.komponents.kovenant.ui.failUi
import nl.komponents.kovenant.ui.successUi
import org.slf4j.LoggerFactory

class ContactActivity : AppCompatActivity() {
    private val log = LoggerFactory.getLogger(javaClass)

    private lateinit var app : AndroidApp
    private lateinit var messengerService: MessengerServiceImpl
    private lateinit var contactService: ContactServiceImpl
    private lateinit var groupService: GroupServiceImpl

    private var contactFragment: ContactFragment? = null
    private var groupFragment: GroupFragment? = null

    private lateinit var viewPager: ViewPager

    var contactList = mutableMapOf<UserId, ContactInfo>()

    override fun onCreate (savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        log.debug("onCreate")

        app = AndroidApp.get(this)

        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_contact)

        val actionBar = findViewById(R.id.contact_toolbar) as Toolbar
        actionBar.title = "Address Book"
        setSupportActionBar(actionBar)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        viewPager = findViewById(R.id.contact_viewpager) as ViewPager
        setupViewPager(viewPager)

        val tabLayout = findViewById(R.id.contact_tabs) as TabLayout
        tabLayout.setupWithViewPager(viewPager)

        messengerService = MessengerServiceImpl(this)
        contactService = ContactServiceImpl(this)
        groupService = GroupServiceImpl(this)
    }

    private fun init () {
        fetchContactList()
        setAppActivity()
        setListeners()
    }

    private fun fetchContactList() {
        contactService.getContacts() successUi {
            contactList = it
        } failUi {
            log.debug("Failed to fetch all contacts")
        }
    }

    private fun setupViewPager(viewPager: ViewPager) {
        val adapter = ViewPagerAdapter(supportFragmentManager)
        contactFragment = ContactFragment()
        groupFragment = GroupFragment()
        adapter.addFragment(contactFragment!!, "Contacts")
        adapter.addFragment(groupFragment!!, "Groups")
        viewPager.adapter = adapter
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item?.itemId) {
            android.R.id.home -> { finish() }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun handleContactEvent (event: ContactEvent) {
        when (event) {
            is ContactEvent.Added -> {
                event.contacts.forEach { contact ->
                    contactFragment?.addedContact(contact)
                }
            }
            is ContactEvent.Blocked -> {
                contactFragment?.removeContactFromList(event.userId)
            }
            is ContactEvent.Removed -> {
                log.debug("Contact Removed Event")
                event.contacts.forEach {
                    log.debug("Contact to be removed ${it.name}")
                    contactFragment?.removeContactFromList(it.id)
                }
            }
            is ContactEvent.Sync -> {
                log.debug("Is contact sync running: ${event.isRunning}")
                if(!event.isRunning) {
                    contactFragment?.fetchConversations()
                }
            }
            is ContactEvent.Unblocked -> { log.debug("Contact was unblocked: ${event.userId}") }
            is ContactEvent.Updated -> {
                event.contacts.forEach {
                    contactFragment?.updateContactFromList(it.old, it.new)
                }
            }
        }
    }

    private fun setListeners() {
        contactService.addContactListener {
            handleContactEvent(it)
        }

        messengerService.addNewMessageListener {
            contactFragment?.handleNewMessage(it)
        }

        groupService.addGroupListener { groupFragment?.handleGroupEvent(it) }
    }

    private fun unsubscribeListeners() {
        contactService.clearListeners()
        messengerService.clearListeners()
    }

    private fun setAppActivity() {
        log.debug("set ui visible")
        app.setCurrentActivity(this, true)
    }

    private fun clearAppActivity() {
        log.debug("set ui hidden")
        app.setCurrentActivity(this, false)
    }

    override fun onStart() {
        super.onStart()
        log.debug("onStart")
    }

    override fun onPause() {
        super.onPause()
        log.debug("onPause")
        clearAppActivity()
        unsubscribeListeners()
    }

    override fun onResume() {
        super.onResume()
        init()
        log.debug("onResume")
    }

    override fun onStop() {
        super.onStop()
        log.debug("onStop")
    }

    override fun onDestroy() {
        super.onDestroy()
        clearAppActivity()
        log.debug("onDestroy")
    }

    internal inner class ViewPagerAdapter(manager: FragmentManager) : FragmentPagerAdapter(manager) {
        private var mFragmentList = mutableListOf<Fragment>()
        private var mFragmentTitleList = mutableListOf<String>()

        override fun getItem(position: Int): Fragment {
            return mFragmentList.get(position)
        }

        override fun getCount(): Int {
            return mFragmentList.size
        }

        fun addFragment(fragment: Fragment, title: String) {
            mFragmentList.add(fragment)
            mFragmentTitleList.add(title)
        }

        override fun getPageTitle(position: Int): CharSequence {
            return mFragmentTitleList[position]
        }
    }
}