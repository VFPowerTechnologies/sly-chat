var ContactController = function () {
    this.conversations = [];
    this.sync = false;
    this.contactSyncNotification = null;
    this.contacts = [];
};

ContactController.prototype  = {
    init : function () {
        this.fetchConversation();
        this.fetchAllContacts();
    },

    resetCachedConversation : function () {
        this.conversations = [];
        this.contacts = [];
        contactService.getContacts().then(function (contacts) {
            messengerService.getConversations().then(function (conversations) {
                conversations.forEach(function(conversation){
                    this.conversations[conversation.contact.id] = conversation;
                }.bind(this));

                var groupDetails = groupController.getGroupDetails();
                if (groupDetails === false) {
                    groupService.getGroupConversations().then(function (groupConversations) {
                        this.createContactHtml(groupConversations, conversations);
                    }.bind(this)).catch(function (e) {
                        exceptionController.handleError(e);
                    });
                }
                else {
                    this.createContactHtml(groupDetails, conversations);
                }
            }.bind(this)).catch(function (e) {
                exceptionController.handleError(e);
            });

            contacts.forEach(function (contact) {
                this.contacts[contact.id] = contact;
            }.bind(this));
        }.bind(this)).catch(function (e) {
            exceptionController.handleError(e);
        });
    },

    fetchAllContacts : function () {
        contactService.getContacts().then(function (contacts) {
            contacts.forEach(function (contact) {
                this.contacts[contact.id] = contact;
            }.bind(this));
        }.bind(this)).catch(function (e) {
            exceptionController.handleError(e);
        });
    },

    fetchConversation : function () {
        messengerService.getConversations().then(function (conversations) {
            this.conversations = [];
            conversations.forEach(function(conversation){
                this.conversations[conversation.contact.id] = conversation;
            }.bind(this));

            var groupDetails = groupController.getGroupDetails();
            if (groupDetails === false) {
                groupService.getGroupConversations().then(function (groupConversations) {
                    this.createContactHtml(groupConversations, conversations);
                }.bind(this)).catch(function (e) {
                    exceptionController.handleError(e);
                });
            }
            else {
                this.createContactHtml(groupDetails, conversations);
            }
        }.bind(this)).catch(function (e) {
            exceptionController.handleError(e);
        });
    },

    addNewContactToCache : function (contact) {
        if (this.contacts[contact.id] === undefined)
            this.contacts[contact.id] = contact;

        if (this.conversations[contact.id] === undefined) {
            this.conversations[contact.id] = {
                contact: contact,
                status: {
                    isOnline: true,
                    unreadMessageCount: 0,
                    lastMessage: null,
                    lastTimestamp: null
                }
            };
        }

        var conversations = [];
        this.conversations.forEach(function (conversation) {
            conversations.push(conversation);
        });

        var groupDetails = groupController.getGroupDetails();
        if (groupDetails !== false)
            this.createContactHtml(groupDetails, conversations);
    },

    removeContactFromCache : function (contact) {
        delete this.contacts[contact.id];
        delete this.conversations[contact.id];

        $("#contact-list").find("#contactLink_" + contact.id).remove();
        $("#recentContactList").find("#recentContactLink_" + contact.id).remove();
        $("#recentChatList").find("#recentChat_" + contact.id).remove();
    },

    getActualGroupConversations : function (groupDetails) {
        var actualGroupConvo = [];
        for(var k in groupDetails) {
            if (groupDetails.hasOwnProperty(k)) {
                if (groupDetails[k].info.lastMessage != null)
                    actualGroupConvo.push(groupDetails[k]);
            }
        }

        return actualGroupConvo;
    },

    fetchAndLoadChat : function (contactId) {
        messengerService.getConversations().then(function (conversations) {
            conversations.forEach(function(conversation){
                this.conversations[conversation.contact.id] = conversation;
            }.bind(this));

            this.loadChatPage(this.conversations[contactId].contact, false);

            navigationController.hideSplashScreen();
            this.fetchConversation();
        }.bind(this)).catch(function (e) {
            exceptionController.handleError(e);
        });
    },

    createContactHtml : function (groupConversations, conversations) {
        var realGroupConvo = this.getActualGroupConversations(groupConversations);
        var realConvo = this.getActualConversation(conversations);
        var jointedRecentChat = this.createJointedRecentChat(realConvo, realGroupConvo);

        this.createRecentChatList(jointedRecentChat);
        this.createContactList();
        this.createRecentContactList(jointedRecentChat);

        if (firstLogin === true && jointedRecentChat.length <= 0) {
            firstLogin = false;
            slychat.popup('#contactPopup');
        }
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
        var contactBlock = $("<li id='contactLink_" + contact.id + "' class='contact-link close-popup'></li>");
        var contactDetails = $("<div><p>" + contact.name + "</p><span>" + contact.email + "</span></div>");

        contactBlock.append(contactDetails);

        contactBlock.click(function (e) {
            this.loadChatPage(contact);
        }.bind(this));

        contactBlock.on("mouseheld", function () {
            vibrate(50);
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

        var contactDiv = $("<div id='recentContactLink_" + contact.id + "' class='contact-link close-popup'><div class='avatar'>" +
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
            $("#recentChatList").html(this.emptyRecentChatHtml());
        }
    },

    emptyRecentChatHtml : function () {
        return "<div style='text-align: center'>No recent chats</div>";
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

        recentDiv.on("mouseheld", function () {
            vibrate(50);
            this.openGroupConversationMenu(conversation.group.id);
        }.bind(this));

        return recentDiv;
    },

    updateRecentChatNode : function (contact, messageInfo) {
        var message = messageInfo.messages[messageInfo.messages.length - 1];

        var node = $("#recentChat_" + contact.id);

        var recentChatList = $("#recentChatList");

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
            node.find(".last-message-time").html("<time class='timeago' datetime='" + time + "'>" + $.timeago(time) + "</time>");

            recentChatList.prepend(node);
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

            if (recentChatList.find(".recent-contact-link").length <= 0)
                recentChatList.html("");

            recentChatList.prepend(this.createSingleRecentChatNode(conversation));
        }
    },

    updateRecentGroupChatNode : function (contact, messageInfo) {
        var message = messageInfo.messages[messageInfo.messages.length - 1];

        var node = $("#recentChat_" + messageInfo.groupId);

        var recentChatList = $("#recentChatList");

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
            node.find(".last-message-time").html("<time class='timeago' datetime='" + time + "'>" + $.timeago(time) + "</time>");

            recentChatList.prepend(node);
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

            if (recentChatList.find(".recent-contact-link").length <= 0)
                recentChatList.html("");

            recentChatList.prepend(this.createGroupRecentChatNode(conversation));
        }
    },

    createJointedRecentChat : function (convo, groupConvo) {
        var jointed = [];

        convo.forEach(function (conversation) {
            jointed.push({
                type: "single",
                lastTimestamp: conversation.status.lastTimestamp,
                conversation: conversation
            });
        });

        groupConvo.forEach(function (conversation) {
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
                    ev.contacts.forEach(function (contact) {
                        //we don't show contacts for which no conversation exists
                        if (contact.allowedMessageLevel === 'ALL')
                            this.addNewContactToCache(contact);
                    }.bind(this));
                    break;
                case 'REMOVE':
                    ev.contacts.forEach(function (contact) {
                        this.removeContactFromCache(contact);
                    }.bind(this));
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
                this.contactSyncNotification = slychat.addNotification({
                    title: "Address book is syncing",
                    closeOnClick: true
                });
            }
        }
        else {
            if(this.contactSyncNotification !== null) {
                slychat.closeNotification(this.contactSyncNotification);
                this.contactSyncNotification = null;
                setTimeout(function () {
                    slychat.addNotification({
                        title: "Address book sync complete",
                        closeOnClick: true,
                        hold: 3000
                    });
                }.bind(this), 1000);
            }

            if (loginController.isLoggedIn())
                this.resetCachedConversation();
        }
    },

    openNotification : function (message, hold) {
        var options = {
            title: message,
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
                this.createContactSearchResult(response.contactInfo);
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

            var hiddenContent = contactBlock.find('.confirm-add-contact-hidden');

            this.addContact(contact, button, successIcon, hiddenContent);
        }.bind(this));

        return contactBlock;
    },

    addContact : function (data, button, successIcon, hiddenContent) {
        var form = $("#addContactForm");
        //remove previous error
        form.find(".error-block").html("");

        contactService.addNewContact(data).then(function (contact){
            button.hide();
            successIcon.show();
            hiddenContent.hide();

            slychat.addNotification({
                title: "Contact has been added",
                hold: 3000
            });

            this.addNewContactToCache(contact);
            this.loadChatPage(contact, false);
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
            this.removeContactFromCache(contact);
            slychat.addNotification({
                title: "Contact has been deleted",
                hold: 3000
            });
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

    openGroupConversationMenu : function (groupId) {
        var buttons = [
            {
                text: 'Group Info',
                onClick: function () {
                    groupController.showGroupInfo(groupId);
                }.bind(this)
            },
            {
                text: "Invite Contacts",
                onClick: function () {
                    groupController.openInviteUsersModal(groupId);
                }
            },
            {
                text: 'Delete Group Messages',
                onClick: function () {
                    slychat.confirm("Are you sure you want to delete all messages in this group?", function () {
                        groupController.deleteAllMessages(groupId);
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

        openInfoPopup(content, "Contact Info");
    },

    clearCache : function () {
        this.conversations = [];
        this.sync = false;
        this.contactSyncNotification = null;
        this.contacts = [];
    }
};
