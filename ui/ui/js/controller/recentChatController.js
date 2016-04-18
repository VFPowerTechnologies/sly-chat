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
            var fragment = $(document.createDocumentFragment());

            var i = 0;
            for (var email in recentChat) {
                if (recentChat.hasOwnProperty(email)) {
                    fragment.append(this.createRecentChat(recentChat[email], i));
                }
                i++;
            }

            recentChatContent.html(fragment);
            this.addRecentChatEventListener();
        }
    },
    createRecentChat : function (recentChat, index) {
        var contactLinkClass = "recent-contact-link ";
        var newBadge = "";

        if(recentChat.status.unreadMessageCount > 0){
            contactLinkClass += "new-messages";
            newBadge = "<span class='pull-right label label-warning' style='line-height: 0.8'>new</span>";
        }

        if(index == 0)
            contactLinkClass += " first-contact";

        var lastMessage;

        if (recentChat.status.lastMessage.length > 40)
            lastMessage = recentChat.status.lastMessage.substring(0, 40) + "...";
        else
            lastMessage = recentChat.status.lastMessage;

        var contactBlock = "";

        contactBlock += "<div class='" + contactLinkClass + "' id='recent%" + recentChat.contact.id + "'><div class='contact'>";
        contactBlock += createAvatar(recentChat.contact.name);
        contactBlock += "<p style='display: inline-block;'>" + recentChat.contact.name + "</p>";
        contactBlock += "<p class='recentTimestamp' style='display: inline-block; float: right; font-size: 10px'>" + $.timeago(recentChat.status.lastTimestamp) + "</p><br>";
        contactBlock += "<p class='recentMessage' style='display: inline-block; float: left; font-size: 10px; line-height: 0;'>" + lastMessage + "</p>";
        contactBlock += "</div>" + newBadge + "</div>";

        return contactBlock;
    },
    addRecentChatEventListener : function () {
        $(".recent-contact-link").bind("click", function (e) {
            e.preventDefault();
            var email = $(this).attr("id").split("recent%")[1];
            KEYTAP.contactController.setCurrentContact(email);
            KEYTAP.navigationController.loadPage("chat.html", true);
        });
    }
};
