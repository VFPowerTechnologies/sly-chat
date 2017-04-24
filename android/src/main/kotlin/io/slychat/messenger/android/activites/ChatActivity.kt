package io.slychat.messenger.android.activites

import android.content.*
import android.os.Bundle
import android.support.design.widget.NavigationView
import android.support.v4.view.GravityCompat
import android.support.v4.widget.DrawerLayout
import android.support.v7.app.AlertDialog
import android.support.v7.widget.Toolbar
import android.view.*
import android.widget.*
import hani.momanii.supernova_emoji_library.Actions.EmojIconActions
import hani.momanii.supernova_emoji_library.Helper.EmojiconEditText
import io.slychat.messenger.android.AndroidApp
import io.slychat.messenger.android.R
import io.slychat.messenger.android.activites.services.impl.AndroidContactServiceImpl
import io.slychat.messenger.android.activites.services.impl.AndroidGroupServiceImpl
import io.slychat.messenger.android.activites.services.impl.AndroidMessengerServiceImpl
import io.slychat.messenger.android.activites.services.impl.AndroidConfigServiceImpl
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.persistence.*
import io.slychat.messenger.services.MessageUpdateEvent
import io.slychat.messenger.services.contacts.ContactEvent
import io.slychat.messenger.services.messaging.ConversationMessage
import io.slychat.messenger.services.messaging.GroupEvent
import nl.komponents.kovenant.ui.failUi
import nl.komponents.kovenant.ui.successUi
import org.slf4j.LoggerFactory
import io.slychat.messenger.services.config.ConvoTTLSettings
import android.view.ContextMenu.ContextMenuInfo
import io.slychat.messenger.android.activites.services.AndroidUIMessageInfo
import io.slychat.messenger.android.activites.services.ChatListAdapter
import io.slychat.messenger.android.activites.services.OnScrollFinishListener
import io.slychat.messenger.core.condError
import io.slychat.messenger.core.isNotNetworkError

class ChatActivity : BaseActivity(), NavigationView.OnNavigationItemSelectedListener {
    companion object {
        val EXTRA_ISGROUP = "io.slychat.messenger.android.activities.ChatActivity.isGroup"
        val EXTRA_CONVERSTATION_ID = "io.slychat.messenger.android.activities.ChatActivity.converstationId"
        val LOAD_COUNT = 30
    }
    private val log = LoggerFactory.getLogger(javaClass)

    private lateinit var app: AndroidApp
    lateinit var messengerService: AndroidMessengerServiceImpl
    private lateinit var contactService: AndroidContactServiceImpl
    private lateinit var groupService: AndroidGroupServiceImpl
    private lateinit var configService: AndroidConfigServiceImpl

    private lateinit var actionBar: Toolbar
    private lateinit var chatList: ListView
    private lateinit var chatInput: EditText
    private lateinit var submitBtn: ImageButton
    private lateinit var expireBtn: ImageButton
    private lateinit var expireSlider: SeekBar
    private lateinit var expirationDelay: TextView
    private lateinit var expirationSliderContainer: LinearLayout
    private lateinit var jumpToRecentBtn: LinearLayout
    private lateinit var jumpToRecentLabel: TextView
    private lateinit var rootView: LinearLayout

    private lateinit var chatListAdapter: ChatListAdapter

    private lateinit var contactInfo: ContactInfo
    private lateinit var groupInfo: GroupInfo
    var groupMembers = mapOf<UserId, ContactInfo>()

    lateinit var conversationId: ConversationId
    private var contextMenuMessageId: String? = null

    private var expireToggled = false
    private var expireDelay: Long? = null

    private var lastVisibleMessagePosition = 0
    private var firstVisibleMessagePosition = 0

    private var initialized = false
    private var lastMessageLoaded = 0
    private var loadingMoreMessage = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!successfullyLoaded)
            return

        val isGroup = intent.getBooleanExtra(EXTRA_ISGROUP, false)
        if (isGroup) {
            val gIdString = intent.getStringExtra(EXTRA_CONVERSTATION_ID)
            if (gIdString == null)
                finish()
            conversationId = GroupId(gIdString).toConversationId()
        }
        else {
            val uIdLong = intent.getLongExtra(EXTRA_CONVERSTATION_ID, -1L)
            if (uIdLong == -1L)
                finish()
            conversationId = UserId(uIdLong).toConversationId()
        }

        app = AndroidApp.get(this)

        setContentView(R.layout.activity_chat)

        chatList = findViewById(R.id.chat_list) as ListView
        submitBtn = findViewById(R.id.submit_chat_btn) as ImageButton
        expireBtn = findViewById(R.id.expire_chat_btn) as ImageButton
        expireSlider = findViewById(R.id.expiration_slider) as SeekBar
        expirationDelay = findViewById(R.id.expiration_delay) as TextView
        expirationSliderContainer = findViewById(R.id.expiration_slider_container) as LinearLayout
        jumpToRecentBtn = findViewById(R.id.jump_to_recent_messages) as LinearLayout
        jumpToRecentLabel = jumpToRecentBtn.findViewById(R.id.jump_to_recent_label) as TextView
        rootView = findViewById(R.id.chat_root_view) as LinearLayout
        actionBar = findViewById(R.id.chat_toolbar) as Toolbar

        resetChatListAdapter()

        actionBar.title = ""
        setSupportActionBar(actionBar)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        setNavigationMenu()

        setupEmojicon()

        messengerService = AndroidMessengerServiceImpl(this)
        contactService = AndroidContactServiceImpl(this)
        groupService = AndroidGroupServiceImpl(this)
        configService = AndroidConfigServiceImpl(this)

        getDisplayInfo()
        createEventListeners()
    }

    private fun setupEmojicon() {
        val icons: String
        val tabs: String
        val bg: String
        val keyboard: Int
        val smiley: Int
        val currentTheme = app.appComponent.appConfigService.appearanceTheme

        if (currentTheme.isNullOrEmpty() || currentTheme == AndroidConfigServiceImpl.darkTheme) {
            icons = "#FFFFFF"
            tabs = "#222222"
            bg = "#222222"
            smiley = R.drawable.ic_tag_faces
            keyboard = R.drawable.ic_keyboard
        }
        else {
            icons = "#222222"
            tabs = "#FFFFFF"
            bg = "#FFFFFF"
            smiley = R.drawable.ic_tag_faces_black
            keyboard = R.drawable.ic_keyboard_black
        }

        chatInput = findViewById(R.id.chat_input) as EditText
        val emojiButton = findViewById(R.id.chat_emoji_button) as ImageButton
        val emojiInput = chatInput as EmojiconEditText

        val emojIcon = EmojIconActions(this, rootView, emojiInput, emojiButton, icons, tabs, bg)
        emojIcon.ShowEmojIcon()
        emojIcon.setIconsIds(keyboard, smiley)
    }

    private fun setNavigationMenu() {
        val navigationView: NavigationView
        navigationView = findViewById(R.id.chat_nav_view) as NavigationView
        if (conversationId is ConversationId.User) {
            navigationView.inflateMenu(R.menu.activity_user_chat_drawer)
        }
        else {
            navigationView.inflateMenu(R.menu.activity_group_chat_drawer)
        }

        navigationView.setNavigationItemSelectedListener(this)

        val drawerName = navigationView.getHeaderView(0).findViewById(R.id.drawer_user_name) as TextView
        val drawerEmail = navigationView.getHeaderView(0).findViewById(R.id.drawer_user_email) as TextView

        drawerEmail.text = app.accountInfo?.email
        drawerName.text = app.accountInfo?.name
    }

    fun getDisplayInfo() {
        val cId = conversationId
        if (cId is ConversationId.User) {
            contactService.getContact(cId.id) successUi {
                if (it == null)
                    finish()
                else {
                    contactInfo = it
                    actionBar.title = it.name
                }
            } failUi {
                log.error("Something failed: {}", it.message, it)
                finish()
            }
        }
        else if (cId is ConversationId.Group){
            groupService.getGroupInfo(cId.id) successUi {
                if (it != null) {
                    groupInfo = it
                    actionBar.title = it.name
                }
                else
                    finish()
            } failUi {
                log.error("Something failed: {}", it.message, it)
                finish()
            }
        }
    }

    private fun createEventListeners() {
        rootView.viewTreeObserver.addOnGlobalLayoutListener {
            val heightDiff = rootView.rootView.height - rootView.height
            if (heightDiff > dpToPx(200F))
                handleKeyboardOpen()
        }
        
        chatList.setOnScrollListener(object : OnScrollFinishListener() {
            override fun onScrollFinished() {
                if (firstVisibleMessagePosition <= 5 && initialized && !loadingMoreMessage) {
                    loadMoreMessage()
                    return
                }

                if (lastVisibleMessagePosition >= chatListAdapter.count - 1)
                    hideJumpToRecent()
                else if(lastVisibleMessagePosition < chatListAdapter.count - 2)
                    showJumpToRecent()
            }

            override fun onScroll(view: AbsListView, firstVisibleItem: Int, visibleItemCount: Int, totalItemCount: Int) {
                lastVisibleMessagePosition = chatList.lastVisiblePosition
                firstVisibleMessagePosition = chatList.firstVisiblePosition
                super.onScroll(view, firstVisibleItem, visibleItemCount, totalItemCount)
            }
        })

        jumpToRecentBtn.setOnClickListener {
            scrollToBottom()
        }

        submitBtn.setOnClickListener {
            handleNewMessageSubmit()
        }

        expireBtn.setOnClickListener {
            handleExpireMessageToggle()
        }

        expireSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                setExpireDelay(seekBar.progress.toLong())
                configService.setConvoTTLSettings(conversationId, ConvoTTLSettings(true, seekBar.progress.toLong() * 1000))
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                setExpireDelay(seekBar.progress.toLong())
            }

            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                setExpireDelay(progress.toLong())
            }
        })
    }

    private fun init() {
        setAppActivity()
        setListeners()
        displayExpireSliderOnStart()

        if (!initialized)
            fetchAndDisplayOnStart()
        else
            fetchAndDisplayUnreed()
    }

    private fun fetchAndDisplayOnStart() {
        log.debug("Fetch and display on start")
        val cId = conversationId
        messengerService.fetchMessageFor(conversationId, 0, LOAD_COUNT) successUi { messages ->
            lastMessageLoaded = messages.count()
            if (cId is ConversationId.Group) {
                groupService.getMembersInfo(cId.id) successUi { members ->
                    groupMembers = members
                    displayMessages(messages)
                }
            }
            else
                displayMessages(messages)
        } failUi {
            log.error("Something failed: {}", it.message, it)
        }
    }

    private fun fetchAndDisplayUnreed() {
        log.debug("Fetch and display unread")
        messengerService.fetchMessageFor(conversationId, 0, LOAD_COUNT) successUi { messages ->
            chatListAdapter.addMessageIfInexistent(messages)
            scrollOnNewMessage(false)
        } failUi {
            log.error("Something failed: {}", it.message, it)
        }
    }

    private fun loadMoreMessage() {
        loadingMoreMessage = true

        messengerService.fetchMessageFor(conversationId, chatListAdapter.count - 1, LOAD_COUNT) successUi { messages ->
            chatListAdapter.addMessagesToTop(messages.sortedBy { it.timestamp })

            val firstView = chatList.getChildAt(0)
            val top = firstView?.top ?: 0

            chatList.setSelectionFromTop(firstVisibleMessagePosition + messages.size - 1, top)
            loadingMoreMessage = false
        } failUi {
            log.error("Something failed: {}", it.message, it)
        }
    }

    private fun handleKeyboardOpen() {
        if (chatList.lastVisiblePosition < chatListAdapter.count - 2)
            showJumpToRecent()
    }

    private fun showJumpToRecent() {
        jumpToRecentBtn.visibility = View.VISIBLE
    }

    private fun hideJumpToRecent() {
        jumpToRecentBtn.visibility = View.GONE
        jumpToRecentLabel.visibility = View.GONE
        jumpToRecentLabel.text = ""
    }

    private fun displayExpireSliderOnStart() {
        val ttlConfig = configService.getConvoTTLSettings(conversationId)
        if (ttlConfig != null && ttlConfig.isEnabled) {
            setExpireDelay(ttlConfig.lastTTL/1000)
            showExpirationSlider()
        }
    }

    private fun handleExpireMessageToggle() {
        val ttlConfig = configService.getConvoTTLSettings(this.conversationId)
        if (ttlConfig != null)
            expireDelay = ttlConfig.lastTTL
        else
            expireDelay = 30000L

        if (expireToggled)
            hideExpirationSlider()
        else
            showExpirationSlider()
    }

    private fun showExpirationSlider() {
        var delay = expireDelay
        if (delay == null)
            delay = 5000

        expireSlider.progress = delay.toInt() / 1000
        setExpireDelay(delay / 1000)

        configService.setConvoTTLSettings(conversationId, ConvoTTLSettings(true, delay))

        expirationSliderContainer.visibility = View.VISIBLE
        expireToggled = true
    }

    private fun setExpireDelay(delay: Long) {
        expireDelay = delay * 1000
        expirationDelay.text = delay.toString()
    }

    private fun hideExpirationSlider() {
        val settingsTTl = configService.getConvoTTLSettings(conversationId)
        if (settingsTTl != null)
            configService.setConvoTTLSettings(conversationId, ConvoTTLSettings(false, settingsTTl.lastTTL))

        expirationSliderContainer.visibility = View.GONE
        expireToggled = false
    }

    private fun displayMessages(messages: List<AndroidUIMessageInfo>) {
        chatList.adapter = chatListAdapter
        chatListAdapter.addAllMessages(messages.reversed())

        initialized = true
    }

    override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenuInfo?) {
        super.onCreateContextMenu(menu, v, menuInfo)
        val messageIdNode = v.findViewById(R.id.message_id) as TextView
        contextMenuMessageId = messageIdNode.text.toString()

        val inflater = menuInflater
        inflater.inflate(R.menu.message_context_menu, menu)
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        val messageId = contextMenuMessageId
        when (item.itemId) {
            R.id.delete_single_message -> {
                if (messageId != null) {
                    deleteMessage(messageId)
                }
                return true
            }
            R.id.view_message_info -> {
                if (messageId != null) {
                    val messageInfo = chatListAdapter.getMessageInfo(messageId)
                    if (messageInfo != null)
                        startMessageInfoActivity(messageInfo)
                }
                return true
            }
            R.id.copy_message_text -> {
                if (messageId != null) {
                    val messageInfo = chatListAdapter.getMessageInfo(messageId)
                    if (messageInfo != null) {
                        copyMessageText(messageInfo.message)
                    }
                }
                return true
            }
            else -> return super.onContextItemSelected(item)
        }
    }

    private fun deleteMessage(messageId: String) {
        val dialog = android.app.AlertDialog.Builder(this)
                .setTitle(resources.getString(R.string.delete_message_title))
                .setMessage(resources.getString(R.string.delete_message_text))
                .setPositiveButton(resources.getString(R.string.ok_button), {  dialog, id ->
                    messengerService.deleteMessage(conversationId, messageId) failUi {
                        log.error("Something failed: {}", it.message, it)
                    }
                })
                .create()

        dialog.show()
    }

    private fun startMessageInfoActivity(messageInfo: AndroidUIMessageInfo) {
        val intent = Intent(baseContext, MessageInfoActivity::class.java)
        intent.putExtra(MessageInfoActivity.EXTRA_SENT_TIME, messageInfo.timestamp)
        intent.putExtra(MessageInfoActivity.EXTRA_MESSAGE_ID, messageInfo.messageId)
        intent.putExtra(MessageInfoActivity.EXTRA_IS_SENT, messageInfo.isSent)
        intent.putExtra(MessageInfoActivity.EXTRA_RECEIVED_TIME, messageInfo.receivedTimestamp)
        intent.putExtra(MessageInfoActivity.EXTRA_SPEAKER_ID, messageInfo.speakerId?.long)

        val cId = conversationId
        intent.putExtra(MessageInfoActivity.EXTRA_CONVERSTATION_ID, cId.asString())

        startActivity(intent)
    }

    private fun copyMessageText(message: String) {
        val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        val clipData = ClipData.newPlainText("", message)

        clipboardManager.primaryClip = clipData
    }

    private fun handleNewMessageSubmit() {
        var ttl = 0L
        val messageValue = chatInput.text.toString()
        if (messageValue.isEmpty())
            return

        val delay = expireDelay
        if (expireToggled && delay != null) {
            ttl = delay
            configService.setConvoTTLSettings(this.conversationId, ConvoTTLSettings(true, ttl))
        }

        messengerService.sendMessageTo(conversationId, messageValue, ttl) successUi {
            chatInput.setText("")
        } failUi {
            log.condError(isNotNetworkError(it), "${it.message}", it)
        }
    }

    private fun scrollToBottom() {
        chatList.post{
            chatList.setSelection(chatListAdapter.count - 1)
            hideJumpToRecent()
        }
    }

    private fun onNewMessage(newMessageInfo: ConversationMessage) {
        val cId = newMessageInfo.conversationId
        if (cId == conversationId) {
            handleNewMessageDisplay(newMessageInfo)
        }
    }

    private fun onContactEvent(event: ContactEvent) {
        if (event is ContactEvent.Blocked || event is ContactEvent.Removed) {
            finish()
        }
    }

    private fun onGroupEvent(event: GroupEvent) {
        if (event is GroupEvent.Blocked || event is GroupEvent.Parted) {
            finish()
        }
    }

    private fun onMessageUpdate(event: MessageUpdateEvent) {
        when (event) {
            is MessageUpdateEvent.Delivered -> { handleDeliveredMessageEvent(event) }
            is MessageUpdateEvent.Expired -> { handleExpiredMessage(event) }
            is MessageUpdateEvent.Deleted -> { handleDeletedMessage(event) }
            is MessageUpdateEvent.DeletedAll -> { handleDeletedAllMessage(event) }
            is MessageUpdateEvent.DeliveryFailed -> { handleFailedDelivery(event) }
            is MessageUpdateEvent.Expiring -> { handleMessageExpiringEvent(event) }
        }
    }

    private fun handleMessageExpiringEvent(event: MessageUpdateEvent.Expiring) {
        if (event.conversationId == conversationId)
            chatListAdapter.displayExpiringMessage(event)
    }

    private fun handleDeliveredMessageEvent(event: MessageUpdateEvent.Delivered) {
        chatListAdapter.updateMessageDelivered(event)
    }

    private fun handleFailedDelivery(event: MessageUpdateEvent.DeliveryFailed) {
        chatListAdapter.updateMessageDeliveryFailed(event)
    }

    private fun handleDeletedAllMessage(event: MessageUpdateEvent.DeletedAll) {
        val cId = event.conversationId
        if (cId == conversationId) {
            resetChatListAdapter()
        }
    }

    private fun resetChatListAdapter() {
        chatListAdapter = ChatListAdapter(this, mutableListOf<AndroidUIMessageInfo>(), this)
    }

    private fun handleDeletedMessage(event: MessageUpdateEvent.Deleted) {
        val cId = event.conversationId
        if (cId == conversationId) {
            chatListAdapter.deleteMessages(event.messageIds)
        }
    }

    private fun handleExpiredMessage(event: MessageUpdateEvent.Expired) {
        chatListAdapter.updateMessageExpired(event)
    }

    private fun handleNewMessageDisplay(newMessage: ConversationMessage) {
        chatListAdapter.add(AndroidUIMessageInfo(newMessage.conversationMessageInfo))
        scrollOnNewMessage(newMessage.conversationMessageInfo.info.isSent)
    }

    fun displayExpiringMessage(messageId: String) {
        messengerService.startMessageExpiration(conversationId, messageId) failUi {
            log.error("Something failed: {}", it.message, it)
        }
    }

    private fun scrollOnNewMessage(isSent: Boolean) {
        if (chatList.lastVisiblePosition >= chatListAdapter.count - 2)
            scrollToBottom()
        else if (jumpToRecentBtn.visibility == View.VISIBLE && !isSent) {
            val currentText = jumpToRecentLabel.text.toString()
            var currentCount: Int
            try {
                currentCount = currentText.toInt()
            } catch (e: NumberFormatException) {
                currentCount = 0
            }
            jumpToRecentLabel.text = (currentCount + 1).toString()
            jumpToRecentLabel.visibility = View.VISIBLE
        }
    }

    private fun setListeners() {
        messengerService.addNewMessageListener({ onNewMessage(it) })
        messengerService.addMessageUpdateListener({ onMessageUpdate(it) })
        contactService.addContactListener { onContactEvent(it) }
        groupService.addGroupListener { onGroupEvent(it) }
    }

    private fun clearListners() {
        messengerService.clearListeners()
        contactService.clearListeners()
        groupService.clearListeners()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.layout_menu, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.action_menu -> {
                val drawer = findViewById(R.id.chat_drawer_layout) as DrawerLayout
                drawer.openDrawer(Gravity.END)
            }
            android.R.id.home -> { finish() }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun loadContactInfo() {
        val cId = conversationId
        if (cId is ConversationId.User) {
            val intent = Intent(baseContext, ContactInfoActivity::class.java)
            intent.putExtra(ContactInfoActivity.EXTRA_USER_ID, cId.id.long)
            intent.putExtra(ContactInfoActivity.EXTRA_USER_NAME, contactInfo.name)
            intent.putExtra(ContactInfoActivity.EXTRA_USER_EMAIL, contactInfo.email)
            intent.putExtra(ContactInfoActivity.EXTRA_USER_PUBKEY, contactInfo.publicKey)
            startActivity(intent)
        }
    }

    private fun blockContact() {
        val cId = conversationId
        if (cId is ConversationId.User) {
            contactService.blockContact(cId.id) failUi {
                log.error("Something failed: {}", it.message, it)
            }
        }
    }

    private fun deleteConversation() {
        messengerService.deleteConversation(conversationId) failUi {
            log.error("Something failed: {}", it.message, it)
        }
    }

    private fun deleteContact() {
        contactService.deleteContact(contactInfo) successUi {
            if (!it)
                log.warn("Failed to delete user id : ${contactInfo.id}")
        } failUi {
            log.error("Something failed: {}", it.message, it)
        }
    }

    private fun deleteGroup() {
        val cId = conversationId
        if (cId is ConversationId.Group) {
            groupService.deleteGroup(cId.id) successUi {
                finish()
            } failUi {
                log.error("Something failed: {}", it.message, it)
            }
        }
    }

    private fun blockGroup() {
        val cId = conversationId
        if (cId is ConversationId.Group) {
            groupService.blockGroup(cId.id) failUi {
                log.error("Something failed: {}", it.message, it)
            }
        }
    }

    private fun loadGroupInfo() {
        val cId = conversationId
        if (cId is ConversationId.Group) {
            val intent = Intent(baseContext, GroupInfoActivity::class.java)
            intent.putExtra(GroupInfoActivity.EXTRA_GROUP_ID, cId.id.string)
            startActivity(intent)
        }
    }

    private fun openConfirmationDialog(title: String, message: String, callBack: () -> Unit) {
        AlertDialog.Builder(this).setTitle(title).setMessage(message)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setPositiveButton(android.R.string.yes, { dialog, id ->
                callBack()
        }).setNegativeButton(android.R.string.no, null).show()
    }

    override fun onBackPressed() {
        val drawer = findViewById(R.id.chat_drawer_layout) as DrawerLayout
        if (drawer.isDrawerOpen(GravityCompat.END)) {
            drawer.closeDrawer(GravityCompat.END)
        } else {
            super.onBackPressed()
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_block_contact -> { openConfirmationDialog(resources.getString(R.string.chat_block_contact_title), resources.getString(R.string.chat_block_contact_text), { blockContact() }) }
            R.id.menu_delete_contact -> { openConfirmationDialog(resources.getString(R.string.chat_delete_contact_title), resources.getString(R.string.chat_delete_contact_text), { deleteContact() }) }
            R.id.menu_delete_conversation -> { openConfirmationDialog(resources.getString(R.string.chat_delete_conversation_title), resources.getString(R.string.chat_delete_conversation_text), { deleteConversation() }) }
            R.id.menu_contact_info -> { loadContactInfo() }
            R.id.menu_group_info -> { loadGroupInfo() }
            R.id.menu_delete_group -> { openConfirmationDialog(resources.getString(R.string.chat_delete_group_title), resources.getString(R.string.chat_delete_group_text), { deleteGroup() })}
            R.id.menu_block_group -> { openConfirmationDialog(resources.getString(R.string.chat_block_group_title), resources.getString(R.string.chat_block_group_text), { blockGroup() })}
        }

        val drawer = findViewById(R.id.chat_drawer_layout) as DrawerLayout
        drawer.closeDrawer(GravityCompat.END)
        return true
    }

    override fun onPause() {
        super.onPause()
        clearListners()
    }

    override fun onResume() {
        super.onResume()
        init()
    }
}