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
            recentChatContent.html("<div style='text-align: center'>No recent conversation</div>");
        }
        else {
            recentChatContent.html("");

            var i = 0;
            for (var email in recentChat) {
                if (recentChat.hasOwnProperty(email)) {
                    recentChatContent.append(this.createRecentChat(recentChat[email], i));
                }
                i++;
            }

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

        var contactBlock = "<div class='" + contactLinkClass + "' id='contact%" + recentChat.contact.email + "'><div class='contact'>";
        contactBlock += createAvatar(recentChat.contact.name);
        contactBlock += "<p style='display: inline-block;'>" + recentChat.contact.name + "</p>";
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
