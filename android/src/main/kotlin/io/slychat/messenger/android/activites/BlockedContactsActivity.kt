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
import io.slychat.messenger.android.activites.services.impl.AndroidContactServiceImpl
import io.slychat.messenger.android.activites.services.impl.AndroidGroupServiceImpl
import org.slf4j.LoggerFactory

class BlockedContactsActivity : BaseActivity() {
    private val log = LoggerFactory.getLogger(javaClass)

    private lateinit var app: AndroidApp
    private lateinit var contactService: AndroidContactServiceImpl

    private var contactFragment: BlockedContactFragment? = null
    private var groupFragment: BlockedGroupFragment? = null

    private lateinit var viewPager: ViewPager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        log.debug("onCreate")

        app = AndroidApp.get(this)

        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_blocked_contacts)

        val actionBar = findViewById(R.id.blocked_contact_toolbar) as Toolbar
        actionBar.title = resources.getString(R.string.blocked_contacts_page_title)
        setSupportActionBar(actionBar)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        viewPager = findViewById(R.id.blocked_contact_viewpager) as ViewPager
        setupViewPager(viewPager)

        val tabLayout = findViewById(R.id.blocked_contact_tabs) as TabLayout
        tabLayout.setupWithViewPager(viewPager)

        contactService = AndroidContactServiceImpl(this)
    }

    private fun init() {
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

    override fun onPause() {
        super.onPause()
        unsubscribeListeners()
    }

    override fun onResume() {
        super.onResume()
        init()
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