var RecentChatController = function (model) {
    this.model = model;
    this.model.setController(this);
};

RecentChatController.prototype = {
    init : function () {
        this.model.fetchRecentChat();
    },
    displayRecentChat : function (recentChat) {
        var recentChatContent = $("#recentChatContent").contents().find("#recentChatList");

        if ($.isEmptyObject(recentChat)) {
            $('ul.tabs').tabs('select_tab', 'contactContent');
            $("#recentChatContent").hide();

            recentChatContent.html("<div style='text-align: center'>No recent conversation</div>");
        }
        else {
            $('ul.tabs').tabs('select_tab', 'recentChatContent');
            $("#contactContent").hide();

            recentChatContent.html("");
            var content = "";

            var i = 0;
            for (var email in recentChat) {
                if (recentChat.hasOwnProperty(email)) {
                    content += this.createRecentChat(recentChat[email], i);
                }
                i++;
            }

            recentChatContent.append(content);

            this.addRecentChatEventListener();
        }
    },
    createRecentChat : function (recentChat, index) {
        var contactLinkClass = "contact-link ";
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

        var date = parseFormatedTimeString(recentChat.status.lastTimestamp);
        var contactBlock = "";

        contactBlock += "<div class='" + contactLinkClass + "' id='contact%" + recentChat.contact.email + "'><div class='contact'>";
        contactBlock += createAvatar(recentChat.contact.name);
        contactBlock += "<p style='display: inline-block;'>" + recentChat.contact.name + "</p>";
        contactBlock += "<p style='display: inline-block; float: right; font-size: 10px'>" + $.timeago(date) + "</p><br>";
        contactBlock += "<p style='display: inline-block; float: left; font-size: 10px; line-height: 0;'>" + lastMessage + "</p>";
        contactBlock += "</div>" + newBadge + "</div>";

        return contactBlock;
    },
    addRecentChatEventListener : function () {
        var iframe = $("#recentChatContent");
        var links = iframe.contents().find(".contact-link");

        links.bind("click", function (e) {
            e.preventDefault();
            var email = $(this).attr("id").split("contact%")[1];
            KEYTAP.contactController.loadContactPage(email);
        });
    }
};
