var ContactController = function () {
    this.conversations = [];
    this.sync = false;
    this.contactSyncNotification = null;
    this.recentGroupChat = [];
    this.recentChat = [];
    this.contacts = [];
};

ContactController.prototype  = {
    init : function () {
        this.fetchConversation();
        this.fetchAllContacts();
    },

    resetCachedConversation : function () {
        this.conversations = [];
        this.fetchConversation();
    },

    fetchAllContacts : function () {
        contactService.getContacts().then(function (contacts) {
            contacts.forEach(function (contact) {
                this.contacts[contact.id] = contact;
            }.bind(this));
        }.bind(this)).catch(function (e) {
            exceptionController.handleError(e);
        })
    },

    fetchConversation : function () {
        messengerService.getConversations().then(function (conversations) {
            groupService.getGroupConversations().then(function (groupConversations) {
                this.storeGroupsConversations(groupConversations);
                this.storeConversations(conversations);
            }.bind(this)).catch(function (e) {
                exceptionController.handleError(e);
            });
        }.bind(this)).catch(function (e) {
            console.log(e);
        });
    },

    storeGroupsConversations : function (conversations) {
        this.recentGroupChat = [];
        groupController.groups = [];
        conversations.forEach(function (conversation) {
            groupController.groups[conversation.group.id] = conversation;
            if(conversation.info.lastMessage != null)
                this.recentGroupChat.push(conversation);

            groupController.createGroupList(conversations);
        }.bind(this));
    },

    fetchAndLoadChat : function (contactId) {
        messengerService.getConversations().then(function (conversations) {
            conversations.forEach(function(conversation){
                this.conversations[conversation.contact.id] = conversation;
            }.bind(this));

            this.loadChatPage(this.conversations[contactId].contact, false);

            navigationController.hideSplashScreen();

            this.storeConversations(conversations);
        }.bind(this)).catch(function (e) {
            console.log(e);
        });
    },

    storeConversations : function (conversations) {
        this.conversations = [];
        conversations.forEach(function(conversation){
            this.conversations[conversation.contact.id] = conversation;
        }.bind(this));

        var jointedRecentChat = this.createJointedRecentChat();

        this.createRecentChatList(jointedRecentChat);
        this.createContactList();
        this.createRecentContactList(jointedRecentChat);

        navigationController.hideSplashScreen();
    },

    createContactList : function () {
        var convo = [];
        this.conversations.forEach(function(conversation) {
            convo.push(conversation);
        });
        convo = this.orderByName(convo);

        var frag = $(document.createDocumentFragment());
        if (convo.length > 0) {
            convo.forEach(function (conversation) {
                frag.append(this.createContactNode(conversation.contact));
            }.bind(this));

            $("#contact-list").html(frag);
        }
        else {
            $("#contact-list").html("Add a contact to start using the app");
        }
    },

    createContactNode : function (contact) {
        var contactBlock = $("<li class='contact-link close-popup'></li>");
        var contactDetails = $("<div><p>" + contact.name + "</p><span>" + contact.email + "</span></div>");

        contactBlock.append(contactDetails);

        contactBlock.click(function (e) {
            // if(contactBlock.hasClass("noClick"))
            //     contactBlock.removeClass("noClick");
            // else
                this.loadChatPage(contact);
        }.bind(this));

        contactBlock.on("mouseheld", function () {
            vibrate(50);
            // contactBlock.addClass("noClick");
            this.openContactMenu(contact);
        }.bind(this));

        return contactBlock;
    },

    createRecentContactList : function (conversations) {
        var frag = $(document.createDocumentFragment());

        for(var i = 0; i < 4; i++) {
            if(i in conversations) {
                if (conversations[i].type == 'single')
                    frag.append(this.createRecentContactNode(conversations[i].conversation))
            }
        }

        $("#recentContactList").html(frag);
    },

    createRecentContactNode : function (conversation) {
        var contact = conversation.contact;

        var contactDiv = $("<div class='contact-link close-popup'><div class='avatar'>" +
            contact.name.charAt(0).toUpperCase() + "</div>" +
            "<div>" + contact.name + "</div></div>");

        $(contactDiv).click(function () {
            this.loadChatPage(contact);
        }.bind(this));

        return contactDiv;
    },

    createRecentChatList : function (jointedRecentChat) {
        var frag =  $(document.createDocumentFragment());

        if(jointedRecentChat.length > 0) {
            jointedRecentChat.forEach(function (conversation) {
                if(conversation.type == 'single')
                    frag.append(this.createSingleRecentChatNode(conversation.conversation));
                else
                    frag.append(this.createGroupRecentChatNode(conversation.conversation));
            }.bind(this));
            $("#recentChatList").html(frag);
        }
        else {
            $("#recentChatList").html("<div>No recent chat</div>");
        }
    },

    createSingleRecentChatNode : function (conversation) {
        var newClass = "";
        var newBadge = "";
        if (conversation.status.unreadMessageCount > 0) {
            newClass = "new";
            newBadge = '<div class="right new-message-badge">' + conversation.status.unreadMessageCount + '</div>';
        }

        var time = new Date(conversation.status.lastTimestamp).toISOString();
        var recentDiv = $("<div id='recentChat_" + conversation.contact.id + "' class='item-link recent-contact-link row " + newClass + "'>" +
            "<div class='recent-chat-name'><span>" + conversation.contact.name + "</span></div>" +
            "<div class='right'><span><small class='last-message-time'><time class='timeago' datetime='" + time + "'>" + $.timeago(time) + "</time></small></span></div>" +
            "<div class='left'>" + this.formatLastMessage(conversation.status.lastMessage) + "</div>" +
            newBadge +
            "</div>");

        recentDiv.click(function () {
                this.loadChatPage(conversation.contact);
        }.bind(this));

        recentDiv.on("mouseheld", function () {
            vibrate(50);
            this.openConversationMenu(conversation.contact);
        }.bind(this));

        return recentDiv;
    },

    createGroupRecentChatNode : function (conversation) {
        var newClass = "";
        var newBadge = "";
        if (conversation.info.unreadMessageCount > 0) {
            newClass = "new";
            newBadge = '<div class="right new-message-badge">' + conversation.info.unreadMessageCount + '</div>';
        }

        var time = new Date(conversation.info.lastTimestamp).toISOString();
        var contactName = "";
        if (conversation.info.lastSpeaker !== null) {
            var contact = this.getContact(conversation.info.lastSpeaker);
            if(contact !== false)
                contactName = contact.name;
        }
        else
            contactName = "You";

        var recentDiv = $("<div id='recentChat_" + conversation.group.id + "' class='item-link recent-contact-link row " + newClass + "'>" +
            "<div class='recent-chat-name'><span><span class='group-contact-name' style='display: inline;'>" + contactName + "</span> (" + conversation.group.name + ")</span></div>" +
            "<div class='right'><span><small class='last-message-time'><time class='timeago' datetime='" + time + "'>" + $.timeago(time) + "</time></small></span></div>" +
            "<div class='left'>" + this.formatLastMessage(conversation.info.lastMessage) + "</div>" +
            newBadge +
            "</div>");

        recentDiv.click(function () {
            contactController.loadChatPage(conversation.group, true, true);
        }.bind(this));

        // recentDiv.on("mouseheld", function () {
        //     vibrate(50);
        //     this.openConversationMenu(conversation.contact);
        // }.bind(this));

        return recentDiv;
    },

    updateRecentChatNode : function (contact, messageInfo) {
        var message = messageInfo.messages[messageInfo.messages.length - 1];

        var node = $("#recentChat_" + contact.id);

        if (node.length > 0) {
            var time = new Date(message.receivedTimestamp).toISOString();
            node.addClass("new");
            var badge = node.find(".new-message-badge");
            if(badge.length <= 0) {
                node.append('<div class="right new-message-badge">1</div>');
            }
            else {
                var newAmount = badge.html();
                badge.html(parseInt(newAmount) + 1);
            }
            node.find(".left").html(this.formatLastMessage(message.message));
            node.find(".last-message-time").html("<time class='timeago' datetime='" + time + "'>" + $.timeago(time) + "</time>")
        }
        else {
            var conversation = {
                contact: contact,
                status: {
                    lastTimestamp: message.receivedTimestamp,
                    lastMessage: message.message,
                    unreadMessageCount: 1
                }
            };

            $("#recentChatList").prepend(this.createSingleRecentChatNode(conversation));
        }
    },

    updateRecentGroupChatNode : function (contact, messageInfo) {
        var message = messageInfo.messages[messageInfo.messages.length - 1];

        var node = $("#recentChat_" + messageInfo.groupId);

        if (node.length > 0) {
            var time = new Date(message.receivedTimestamp).toISOString();
            node.addClass("new");
            var badge = node.find(".new-message-badge");
            if(badge.length <= 0) {
                node.append('<div class="right new-message-badge">1</div>');
            }
            else {
                var newAmount = badge.html();
                badge.html(parseInt(newAmount) + 1);
            }
            node.find(".group-contact-name").html(contact.name);
            node.find(".left").html(this.formatLastMessage(message.message));
            node.find(".last-message-time").html("<time class='timeago' datetime='" + time + "'>" + $.timeago(time) + "</time>")
        }
        else {
            var conversation = {
                contact: contact,
                group: groupController.getGroup(messageInfo.groupId),
                info: {
                    lastSpeaker: messageInfo.contact,
                    lastTimestamp: message.receivedTimestamp,
                    lastMessage: message.message,
                    unreadMessageCount: 1
                }
            };

            $("#recentChatList").prepend(this.createGroupRecentChatNode(conversation));
        }
    },

    createJointedRecentChat : function () {
        var jointed = [];
        this.recentChat = [];
        var actualConversation = this.getActualConversation(this.conversations);
        actualConversation.forEach(function (conversation) {
            this.recentChat.push(conversation);
        }.bind(this));

        this.recentChat.forEach(function (conversation) {
            jointed.push({
                type: "single",
                lastTimestamp: conversation.status.lastTimestamp,
                conversation: conversation
            });
        });

        this.recentGroupChat.forEach(function (conversation) {
            jointed.push({
                type: 'group',
                lastTimestamp: conversation.info.lastTimestamp,
                conversation: conversation
            })
        });

        return this.orderByRecentChat(jointed);
    },

    formatLastMessage : function (message) {
        if(message.length > 40)
            return message.substring(0, 40) + "...";
        else
            return message;
    },

    addContactEventListener : function () {
        contactService.addContactEventListener(function (ev) {
            switch(ev.type) {
                case "ADD":
                case 'REMOVE':
                    this.resetCachedConversation();
                    break;
                case "SYNC":
                    this.sync = ev.running;
                    this.handleContactSyncNotification(ev.running);
                    break;
            }
        }.bind(this));
    },

    handleContactSyncNotification : function (running) {
        if (running == true) {
            if($(".contact-sync-notification").length <= 0) {
                this.openNotification("Contact list is syncing");
            }
        }
        else {
            if(this.contactSyncNotification !== null) {
                slychat.closeNotification(this.contactSyncNotification);
                this.openNotification("Contact list sync complete", 3000);
                this.contactSyncNotification = null;
            }

            if (loginController.isLoggedIn())
                this.resetCachedConversation();
        }
    },

    openNotification : function (message, hold) {
        var options = {
            custom: '<div class="item-content">' +
            '<div class="item-text">' + message + '</div>' +
            '</div>',
            additionalClass: "custom-notification",
            closeOnClick: true
        };

        if(typeof hold !== "undefined") {
            options.hold = hold;
        }

        this.contactSyncNotification = slychat.addNotification(options);
    },

    newContactSearch : function () {
        var form = $("#addContactForm");
        //remove previous error
        form.find(".error-block").html("");

        var input = $$("#new-contact-username").val().replace(/\s+/g, '');
        if (input === '')
            return;

        slychat.showPreloader();

        var phone = null;
        var username = null;
        if (validateEmail(input)){
            username = input;
        }
        else{
            phone = input;
        }

        contactService.fetchNewContactInfo(username, phone).then(function (response) {
            slychat.hidePreloader();
            if (response.successful == false) {
                form.find(".error-block").html("<li>" + response.errorMessage +"</li>");
            }
            else {
                this.createContactSearchResult(response.contactDetails);
            }
        }.bind(this)).catch(function (e) {
            slychat.hidePreloader();
            form.find(".error-block").html("<li>An error occurred</li>");
            console.error('Unable to add contact: ' + e.message);
            console.error(e);
        });
    },

    createContactSearchResult : function (contact) {
        var contactNode = this.createNewContactNode(contact);
        $('#newContactSearchResult').append(contactNode);
    },

    createNewContactNode : function (contact) {
        var button = null;
        var successIcon = null;

        var contactBlock = $("<li class='new-contact-link'></li>");
        var avatar = $("<div class='avatar'>" + contact.name.charAt(0).toUpperCase() + "</div>");
        var details = $("<div class='details'><p>" + contact.name + "</p><p>" + contact.email + "</p></div>");
        var buttonDiv = $("<div class='pull-right color-green confirm-add-contact-hidden new-contact-button'></div>");

        if(contact.id in this.conversations) {
            button = $("<a class='button confirm-add-contact-btn icon-only color-green' style='display: none;'>Confirm</a>");
            successIcon = $('<div class="contact-added-successfully" style="border: 1px solid #4CD964; border-radius: 50%; text-align: center; width: 35px; height: 35px;"><i class="fa fa-check" style="font-size: 25px;"></i></div>');
        }
        else {
            button = $("<a class='button confirm-add-contact-btn icon-only color-green'>Confirm</a>");
            successIcon = $('<div class="contact-added-successfully" style="display: none; border: 1px solid #4CD964; border-radius: 50%; text-align: center; width: 35px; height: 35px;"><i class="fa fa-check" style="font-size: 25px;"></i></div>');
        }
        var pubKey = $("<div class='confirm-add-contact-hidden pubkey'><div>" + formatPublicKey(contact.publicKey) + "</div></div>");

        contactBlock.append(avatar);
        contactBlock.append(details);
        buttonDiv.append(successIcon);
        buttonDiv.append(button);
        contactBlock.append(buttonDiv);
        contactBlock.append(pubKey);

        $(contactBlock).click( function (e) {
            var hiddenContent = $(this).find('.confirm-add-contact-hidden');
            if(hiddenContent.css("display") === 'none')
                hiddenContent.show();
            else
                hiddenContent.hide();
        });

        $(button).click(function (e) {
            e.preventDefault();
            e.stopPropagation();

            var data = {
                id: contact.id,
                name: contact.name,
                email: contact.email,
                phoneNumber: contact.phoneNumber,
                publicKey: contact.publicKey
            };

            this.addContact(data, button, successIcon);
        }.bind(this));

        return contactBlock;
    },

    addContact : function (data, button, successIcon) {
        var form = $("#addContactForm");
        //remove previous error
        form.find(".error-block").html("");

        contactService.addNewContact(data).then(function (result){
            button.hide();
            successIcon.show();
            this.resetCachedConversation();
        }.bind(this)).catch(function (e) {
            form.find(".error-block").html("<li>An error occurred</li>");
            console.error('Unable to add contact: ' + e.message);
        });
    },

    orderByName : function (convo) {
        convo.sort(function(a, b) {
            var emailA = a.contact.email.toLowerCase();
            var emailB = b.contact.email.toLowerCase();

            if(emailA < emailB)
                return -1;
            if(emailA > emailB)
                return 1;

            return 0;
        });

        return convo;
    },

    getActualConversation : function (conversations) {
        var actualConversation = [];

        conversations.forEach(function (conversation) {
            if(conversation.status.lastMessage != null)
                actualConversation.push(conversation);
        });

        return actualConversation;
    },

    orderByRecentChat : function (actualConversations) {
        actualConversations.sort(function(a, b) {
            var dateA = a.lastTimestamp;
            var dateB = b.lastTimestamp;

            if(dateA > dateB) {
                return -1;
            }
            if(dateA < dateB) {
                return 1;
            }

            return 0;
        });

        return actualConversations;
    },

    loadChatPage : function (contact, pushCurrenPage, group) {
        if (pushCurrenPage === undefined)
            pushCurrenPage = true;

        if (group === undefined)
            group = false;

        var options = {
            url: "chat.html",
            group: group,
            query: {
                name: contact.name,
                id: contact.id
            }
        };

        if (group === false) {
            options.query.email = contact.email;
            options.query.publicKey = contact.publicKey;
            options.query.phoneNumber = contact.phoneNumber;
        }

        navigationController.loadPage('chat.html', pushCurrenPage, options);
    },

    getContact : function (id) {
        if(id in this.contacts)
            return this.contacts[id];
        else
            return false;
    },

    deleteContact : function (contact) {
        contactService.removeContact(contact).then(function () {
            this.resetCachedConversation();
        }.bind(this)).catch(function (e) {
            // TODO handle errors
            console.log(e);
        })
    },

    openConversationMenu : function (contact) {
        var buttons = [
            {
                text: 'Contact Info',
                onClick: function () {
                    this.showContactInfo(contact);
                }.bind(this)
            },
            {
                text: 'Delete Conversation',
                onClick: function () {
                    slychat.confirm("Are you sure you want to delete this conversation?", function () {
                        // TODO update confirm style
                        chatController.deleteConversation(contact);
                    })
                }
            },
            {
                text: 'Cancel',
                color: 'red',
                onClick: function () {
                }
            }
        ];
        slychat.actions(buttons);
    },

    openContactMenu : function (contact) {
        var buttons = [
            {
                text: 'Contact Info',
                onClick: function () {
                    this.showContactInfo(contact);
                }.bind(this)
            },
            {
                text: 'Delete Contact',
                onClick: function () {
                    slychat.confirm("Are you sure you want to delete this conversation?", function () {
                        // TODO update confirm style
                        this.deleteContact(contact);
                    }.bind(this))
                }.bind(this)
            },
            {
                text: 'Cancel',
                color: 'red',
                onClick: function () {
                }
            }
        ];
        slychat.actions(buttons);
    },

    showContactInfo : function (contact) {
        var content = "<div class='contact-info'>" +
                "<p class='contact-info-title'>Name:</p>" +
                "<p class='contact-info-details'>" + contact.name + "</p>" +
            "</div>" +
            "<div class='contact-info'>" +
                "<p class='contact-info-title'>Email:</p>" +
                "<p class='contact-info-details'>" + contact.email + "</p>" +
            "</div>" +
            "<div class='contact-info'>" +
                "<p class='contact-info-title'>Public Key:</p>" +
                "<p class='contact-info-details'>" + formatPublicKey(contact.publicKey) + "</p>" +
            "</div>";

        openInfoPopup(content);
    }
};