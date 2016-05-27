var RecentChatController = function (model) {
    this.model = model;
    this.model.setController(this);
};

RecentChatController.prototype = {
    init : function (conversations) {
        this.model.setRecentChat(conversations);
    },
    displayRecentChat : function (recentChat) {
        var recentChatContent = $("#recentChatList");

        if ($.isEmptyObject(recentChat)) {
            $('ul.tabs').tabs('select_tab', 'contactList');

            recentChatContent.html("<div style='text-align: center'>No recent conversations</div>");
        }
        else {
            $('ul.tabs').tabs('select_tab', 'recentChatList');
            var fragment = $(document.createDocumentFragment());

            for (var id in recentChat) {
                if (recentChat.hasOwnProperty(id)) {
                    fragment.append(this.createRecentChat(recentChat[id]));
                }
            }

            recentChatContent.html(fragment);
        }
    },
    createRecentChatEvent : function () {
        $(document).on("click", "[id^='deleteConversation_']", function (e) {
            e.preventDefault();
            var contactId = $(this).attr("id").split("_")[1];
            var contact = KEYTAP.contactController.getContact(contactId);

            KEYTAP.chatController.createDeleteWholeConversationDialog(contact);
        });
    },
    createRecentChat : function (recentChat) {
        var contactLinkClass = "recent-contact-link ";
        var newBadge = "";

        if(recentChat.status.unreadMessageCount > 0){
            contactLinkClass += "new-messages";
            newBadge = "<span class='pull-right label label-warning' style='line-height: 0.8'>new</span>";
        }

        var lastMessage;

        if (recentChat.status.lastMessage.length > 40)
            lastMessage = recentChat.status.lastMessage.substring(0, 40) + " ...";
        else
            lastMessage = recentChat.status.lastMessage;

        var contactBlock = "";

        var lockIcon = "<i class='fa fa-lock' style='float: right; color: green;'></i>";

        var contactBlockDiv = $("<div class='" + contactLinkClass + "' id='recent_" + recentChat.contact.id + "'></div>");
        contactBlock += "<div class='contact'>";
        contactBlock += createAvatar(recentChat.contact.name);
        contactBlock += "<p style='display: inline-block;'>" + recentChat.contact.name + "</p>";
        contactBlock += "<p class='recentTimestamp' style='display: inline-block; float: right; font-size: 10px'>" + $.timeago(recentChat.status.lastTimestamp) + "</p><br>";
        contactBlock += "<p class='recentMessage' style='display: inline-block; float: left; font-size: 10px; line-height: 10px;'>" + createTextNode(lastMessage) + "</p>";
        contactBlock += "</div>" + newBadge + lockIcon;

        contactBlockDiv.html(contactBlock);

        contactBlockDiv.on("click", function (e) {
            e.preventDefault();
            KEYTAP.contactController.setCurrentContact(recentChat.contact.id);
            KEYTAP.navigationController.loadPage("chat.html", true);
        });

        contactBlockDiv.on("mouseheld", function (e) {
            e.preventDefault();
            vibrate(50);
            var contextMenu = KEYTAP.recentChatController.openRecentChatContextLikeMenu(recentChat.contact.id);
            contextMenu.open();
        });

        return contactBlockDiv;
    },
    /**
     * Opens the recent chat menu on recent chat node long press.
     *
     * @param contactId
     * @returns {*}
     */
    openRecentChatContextLikeMenu : function (contactId) {
        var html = "<div class='contextLikeMenu'>" +
            "<ul>" +
            "<li><a id='contactDetails_" + contactId + "' href='#'>Contact Details</a></li>" +
            "<li role='separator' class='divider'></li>" +
            "<li><a id='deleteConversation_" + contactId + "' href='#'>Delete Conversation</a></li>" +
            "</ul>" +
            "</div>";

        return createContextLikeMenu(html, true);
    }
};
