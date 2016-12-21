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
import org.slf4j.LoggerFactory

class BlockedContactsActivity : AppCompatActivity() {
    private val log = LoggerFactory.getLogger(javaClass)

    private lateinit var app : AndroidApp
    private lateinit var contactService: ContactServiceImpl
    private lateinit var groupService: GroupServiceImpl

    private var contactFragment: BlockedContactFragment? = null
    private var groupFragment: BlockedGroupFragment? = null

    private lateinit var viewPager: ViewPager

    override fun onCreate (savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        log.debug("onCreate")

        app = AndroidApp.get(this)

        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_blocked_contacts)

        val actionBar = findViewById(R.id.blocked_contact_toolbar) as Toolbar
        actionBar.title = "Address Book"
        setSupportActionBar(actionBar)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        viewPager = findViewById(R.id.blocked_contact_viewpager) as ViewPager
        setupViewPager(viewPager)

        val tabLayout = findViewById(R.id.blocked_contact_tabs) as TabLayout
        tabLayout.setupWithViewPager(viewPager)

        contactService = ContactServiceImpl(this)
        groupService = GroupServiceImpl(this)
    }

    private fun init () {
        setAppActivity()
        setListeners()
    }

    private fun setupViewPager(viewPager: ViewPager) {
        val adapter = ViewPagerAdapter(supportFragmentManager)
        contactFragment = BlockedContactFragment()
        groupFragment = BlockedGroupFragment()
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

    private fun setListeners() {
        contactService.addContactListener {
            contactFragment?.handleContactEvent(it)
        }
    }

    private fun unsubscribeListeners() {
        contactService.clearListeners()
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