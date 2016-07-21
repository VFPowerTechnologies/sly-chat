var ContactController = function () {
    this.conversations = [];
    this.recentContact = [];
    this.recentChatNodes = [];
    this.sync = false;
    this.contactSyncNotification = null;
};

ContactController.prototype  = {
    init : function () {
        this.fetchConversation();
    },

    resetCachedConversation : function () {
        this.conversations = [];
        this.fetchConversation();
    },

    fetchConversation : function () {
        if (this.conversations.length <= 0) {
            messengerService.getConversations().then(function (conversations) {
                this.storeConversations(conversations);
            }.bind(this)).catch(function (e) {
                console.log(e);
            });
        }
    },

    fetchAndLoadChat : function (contactId) {
        messengerService.getConversations().then(function (conversations) {
            var forContact = this.orderByName(conversations);

            forContact.forEach(function(conversation){
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
        var forContact = this.orderByName(conversations);
        this.recentContact = this.orderByRecentChat(conversations);
        var forRecentContact = [];

        this.createRecentChatList();

        forContact.forEach(function(conversation){
            this.conversations[conversation.contact.id] = conversation;
        }.bind(this));

        this.createContactList();

        for(var i = 0; i < 4; i++) {
            if(i in this.recentContact) {
                forRecentContact.push(this.recentContact[i]);
            }
        }

        this.createRecentContactList(forRecentContact);

        navigationController.hideSplashScreen();
    },

    createContactList : function () {
        var frag = $(document.createDocumentFragment());
        this.conversations.forEach(function (conversation) {
            frag.append(this.createContactNode(conversation.contact));
        }.bind(this));

        $("#contact-list").html(frag);
    },

    createContactNode : function (contact) {
        var contactBlock = $("<li class='contact-link close-popup'></li>");
        var contactDetails = $("<div><p>" + contact.name + "</p><span>" + contact.email + "</span></div>");

        contactBlock.append(contactDetails);

        contactBlock.click(function (e) {
            this.loadChatPage(contact);
        }.bind(this));

        $$(contactBlock).on("taphold", function () {
            vibrate(50);
            this.openContactMenu(contact);
        }.bind(this));

        return contactBlock;
    },

    createRecentContactList : function (conversations) {
        var frag = $(document.createDocumentFragment());
        conversations.forEach(function (conversation) {
            frag.append(this.createRecentContactNode(conversation))
        }.bind(this));

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

    createRecentChatList : function () {
        this.recentChatNodes = [];
        this.recentContact.forEach(function (conversation) {
            this.recentChatNodes[conversation.contact.id] = this.createRecentChatNode(conversation);
        }.bind(this));

        this.insertRecentChat();
    },

    createRecentChatNode : function (conversation) {
        var time = new Date(conversation.status.lastTimestamp).toISOString();
        var recentDiv = $("<div class='item-link recent-contact-link row'>" +
            "<div class='col-100 recent-chat-name'><span>" + conversation.contact.name + "</span></div>" +
            "<div class='left'><span>" + this.formatLastMessage(conversation.status.lastMessage) + "</span></div>" +
            "<div class='right'><span><small class='last-message-time'><time class='timeago' datetime='" + time + "'>" + $.timeago(time) + "</time></small></span></div>" +
            "</div>");

        recentDiv.click(function () {
            this.loadChatPage(conversation.contact);
        }.bind(this));

        $$(recentDiv).on("taphold", function () {
            vibrate(50);
            this.openConversationMenu(conversation.contact);
        }.bind(this));

        return recentDiv;
    },

    updateRecentChatNode : function (contact, messageInfo) {
        var message = messageInfo.messages[messageInfo.messages.length - 1];

        if (contact.id in this.recentChatNodes) {
            var time = new Date(message.receivedTimestamp).toISOString();
            var node = this.recentChatNodes[contact.id];
            node.find(".left").html(this.formatLastMessage(message.message));
            node.find(".last-message-time").html("<time class='timeago' datetime='" + time + "'>" + $.timeago(time) + "</time>")
        }
        else {
            var conversation = {
                contact: contact,
                status: {
                    lastTimestamp: message.receivedTimestamp,
                    lastMessage: message.message
                }
            };

            this.recentChatNodes[contact.id] = this.createRecentChatNode(conversation);
        }

        this.insertRecentChat();
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

    orderByName : function (conversations) {
        conversations.sort(function(a, b) {
            var emailA = a.contact.email.toLowerCase();
            var emailB = b.contact.email.toLowerCase();

            if(emailA < emailB)
                return -1;
            if(emailA > emailB)
                return 1;

            return 0;
        });

        return conversations;
    },

    orderByRecentChat : function (conversations) {
        var actualConversation = [];

        conversations.forEach(function (conversation) {
            if(conversation.status.lastMessage != null)
                actualConversation.push(conversation);
        });

        actualConversation.sort(function(a, b) {
            var dateA = parseFormatedTimeString(a.status.lastTimestamp);
            var dateB = parseFormatedTimeString(b.status.lastTimestamp);

            if(dateA.getTime() > dateB.getTime())
                return -1;
            if(dateA.getTime() < dateB.getTime())
                return 1;

            return 0;
        });

        return actualConversation;
    },

    insertRecentChat : function () {
        if(this.recentChatNodes.length > 0) {
            var frag = $(document.createDocumentFragment());

            this.recentChatNodes.forEach(function (element) {
                frag.append(element);
            });

            $("#recentChatList").html(frag);
        }
        else {
            $("#recentChatList").html("<div>No recent chat</div>");
        }
    },

    loadChatPage : function (contact, pushCurrenPage) {;
        if (pushCurrenPage === undefined)
            pushCurrenPage = true;

        var options = {
            url: "chat.html",
            query: {
                name: contact.name,
                email: contact.email,
                id: contact.id,
                publicKey: contact.publicKey,
                phoneNumber: contact.phoneNumber
            }
        };

        navigationController.loadPage('chat.html', pushCurrenPage, options);
    },

    getContact : function (id) {
        if (id in this.conversations)
            return this.conversations[id].contact;
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