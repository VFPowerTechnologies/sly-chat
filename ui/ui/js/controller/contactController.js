var ContactController = function () {
    this.conversations = [];
    this.sync = false;
    this.contactSyncNotification = null;
    this.contacts = [];
    this.lastContactInfoId = null;
};

ContactController.prototype  = {
    init : function () {
        this.fetchConversation();
        this.fetchAllContacts();
    },

    resetCachedConversation : function () {
        this.conversations = [];
        this.contacts = [];
        groupController.clearCache();
        contactService.getContacts().then(function (contacts) {
            messengerService.getConversations().then(function (conversations) {
                conversations.forEach(function(conversation){
                    this.conversations[conversation.contact.id] = conversation;
                }.bind(this));

                groupService.getGroupConversations().then(function (groupConversations) {
                    groupController.cacheGroupDetails(groupConversations);
                    this.createContactHtml(groupConversations, conversations);
                }.bind(this)).catch(function (e) {
                    exceptionController.handleError(e);
                });
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
        if (groupDetails === false) {
            groupService.getGroupConversations().then(function (groupConversations) {
                this.createContactHtml(groupConversations, conversations);
            }.bind(this)).catch(function (e) {
                exceptionController.handleError(e);
            });
        }
        else
            this.createContactHtml(groupDetails, conversations);
    },

    removeContactFromCache : function (contact) {
        delete this.contacts[contact.id];
        delete this.conversations[contact.id];

        $("#contact-list").find("#contactLink_" + contact.id).remove();
        $("#recentContactList").find("#recentContactLink_" + contact.id).remove();
        $("#recentChatList").find("#recentChat_" + contact.id).remove();
        $("#leftContact_" + contact.id).remove();
    },

    getActualGroupConversations : function (groupDetails) {
        var actualGroupConvo = [];
        for(var k in groupDetails) {
            if (groupDetails.hasOwnProperty(k)) {
                if (groupDetails[k].info.lastTimestamp != null)
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

            uiController.hideSplashScreen();
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
        this.createLeftContactList();
        groupController.createLeftGroupList();
        this.createRecentContactList(jointedRecentChat);

        if (firstLogin === true && jointedRecentChat.length <= 0) {
            firstLogin = false;
            slychat.popup('#contactPopup');
        }
        uiController.hideSplashScreen();
    },

    createLeftContactList : function () {
        var convo = [];
        this.conversations.forEach(function(conversation) {
            convo.push(conversation);
        });
        convo = this.orderByName(convo);

        var frag = $(document.createDocumentFragment());
        if (convo.length > 0) {
            convo.forEach(function (conversation) {
                frag.append(this.createLeftContactNode(conversation.contact, conversation.status.unreadMessageCount));
            }.bind(this));

            $("#leftContactList").html(frag);
        }
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

    createLeftContactNode : function (contact, unreadCount) {
        var newBadge = "";
        if (unreadCount > 0) {
            newBadge = '<span class="left-menu-new-badge" style="color: red; font-size: 12px; margin-left: 5px;">new</span>';
        }
        var contactBlock = $("<li id='leftContact_" + contact.id + "' class='contact-link'><a class='left-contact-link' href='#'>" + contact.name + "</a>" + newBadge + "</li>");

        contactBlock.find('.left-contact-link').click(function (e) {
            this.loadChatPage(contact);
        }.bind(this));

        contactBlock.find('.left-contact-link').on("mouseheld", function () {
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

        var messageString = conversation.status.lastMessage == null ? "Hidden Message" : conversation.status.lastMessage;

        var time = new Date(conversation.status.lastTimestamp - window.relayTimeDifference).toISOString();
        var recentDiv = $("<div id='recentChat_" + conversation.contact.id + "' class='item-link recent-contact-link row " + newClass + "'>" +
            "<div class='recent-chat-name'><span>" + conversation.contact.name + "</span></div>" +
            "<div class='right'><span><small class='last-message-time'><time class='timeago' datetime='" + time + "'>" + $.timeago(time) + "</time></small></span></div>" +
            "<div class='left'>" + this.formatLastMessage(messageString) + "</div>" +
            newBadge +
            "</div>");

        recentDiv.click(function () {
                this.loadChatPage(conversation.contact);
        }.bind(this));

        recentDiv.on("mouseheld", function () {
            vibrate(50);
            this.openConversationMenu(conversation.contact);
        }.bind(this));

        recentDiv.find(".timeago").timeago();

        return recentDiv;
    },

    createGroupRecentChatNode : function (conversation) {
        var newClass = "";
        var newBadge = "";
        if (conversation.info.unreadMessageCount > 0) {
            newClass = "new";
            newBadge = '<div class="right new-message-badge">' + conversation.info.unreadMessageCount + '</div>';
        }

        var time = new Date(conversation.info.lastTimestamp - window.relayTimeDifference).toISOString();
        var contactName = "";
        if (conversation.info.lastSpeaker !== null) {
            var contact = this.getContact(conversation.info.lastSpeaker);
            if(contact !== false)
                contactName = contact.name;
        }
        else
            contactName = "You";

        var messageString = conversation.info.lastMessage == null ? "Hidden Message" : conversation.info.lastMessage;


        var recentDiv = $("<div id='recentChat_" + conversation.group.id + "' class='item-link recent-contact-link row " + newClass + "'>" +
            "<div class='recent-chat-name'><span><span class='group-contact-name' style='display: inline;'>" + contactName + "</span> (" + conversation.group.name + ")</span></div>" +
            "<div class='right'><span><small class='last-message-time'><time class='timeago' datetime='" + time + "'>" + $.timeago(time) + "</time></small></span></div>" +
            "<div class='left'>" + this.formatLastMessage(messageString) + "</div>" +
            newBadge +
            "</div>");

        recentDiv.click(function () {
            contactController.loadChatPage(conversation.group, true, true);
        }.bind(this));

        recentDiv.on("mouseheld", function () {
            vibrate(50);
            this.openGroupConversationMenu(conversation.group.id);
        }.bind(this));

        recentDiv.find(".timeago").timeago();

        return recentDiv;
    },

    updateRecentChatNode : function (contactId) {
        if (this.conversations[contactId] === undefined)
            return;

        var conversation = this.conversations[contactId];

        var recentChatList = $("#recentChatList");
        if (recentChatList.length > 0) {
            var node = $("#recentChat_" + contactId);
            if (conversation.status.lastTimestamp != null) {
                if (node.length > 0) {
                    var messageString = conversation.status.lastMessage == null ? "Hidden Message" : conversation.status.lastMessage;

                    var time = new Date(conversation.status.lastTimestamp - window.relayTimeDifference).toISOString();
                    node.find(".last-message-time").html("<time class='timeago' datetime='" + time + "'>" + $.timeago(time) + "</time>");
                    node.find(".left").html(this.formatLastMessage(messageString));

                    recentChatList.prepend(node);
                }
                else {
                    var newChat = this.createSingleRecentChatNode(conversation);
                    if (recentChatList.find(".recent-contact-link").length > 0)
                        recentChatList.prepend(newChat);
                    else
                        recentChatList.html(newChat);
                }
            }
            else {
                node.remove();
            }
        }
    },

    updateRecentGroupChatNode : function (groupId) {
        var groupDetails = groupController.groupDetailsCache[groupId];
        if (groupDetails === undefined || groupDetails.info === undefined)
            return;

        var recentChatList = $("#recentChatList");
        if (recentChatList.length > 0) {
            var node = $("#recentChat_" + groupId);
            if (groupDetails.info.lastTimestamp != null) {
                if (node.length > 0) {
                    var messageString = groupDetails.info.lastMessage == null ? "Hidden Message" : groupDetails.info.lastMessage;

                    var time = new Date(groupDetails.info.lastTimestamp - window.relayTimeDifference).toISOString();
                    node.find(".last-message-time").html("<time class='timeago' datetime='" + time + "'>" + $.timeago(time) + "</time>");
                    node.find(".left").html(this.formatLastMessage(messageString));

                    var contactName;
                    if (groupDetails.info.lastSpeaker != null) {
                        var contact = this.getContact(groupDetails.info.lastSpeaker);
                        if (contact != false)
                            contactName = contact.name;
                    }
                    else
                        contactName = "You";

                    node.find('.group-contact-name').html(contactName);

                    recentChatList.prepend(node);
                }
                else {
                    var newChat = this.createGroupRecentChatNode(groupDetails);
                    if (recentChatList.find(".recent-contact-link").length > 0)
                        recentChatList.prepend(newChat);
                    else
                        recentChatList.html(newChat);
                }
            }
            else {
                node.remove();
            }
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
                case "UPDATE":
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
                    this.handleContactSyncNotification();
                    break;
                case "BLOCK":
                    this.handleContactBlocked(ev.userId);
                    break;
                case "UNBLOCK":
                    this.handleContactUnblocked(ev.userId);
                    break;
            }
        }.bind(this));
    },

    handleContactSyncNotification : function () {
        if (this.sync == true) {
            this.contactSyncTimer = new Date().getTime();

            setTimeout(function () {
                if (this.sync === true && new Date().getTime() - this.contactSyncTimer > 60000) {
                    if ($(".contact-sync-notification").length <= 0) {
                        this.contactSyncNotification = slychat.addNotification({
                            title: "Address book is syncing",
                            closeOnClick: true
                        });
                    }
                }
            }.bind(this), 60000);

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

    blockContact : function (contactId) {
        contactService.block(contactId).catch(function (e) {
            slychat.addNotification({
                title: "An error occured",
                hold: 2000
            });
            exceptionController.handleError(e);
        });
    },

    unblockContact : function (contactId) {
        contactService.unblock(contactId).catch(function (e) {
            slychat.addNotification({
                title: "An error occured",
                hold: 2000
            });
            exceptionController.handleError(e);
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
            if(conversation.status.lastTimestamp !== null)
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

        chatController.currentContact = {
            isGroup: group,
            id: contact.id,
            name: contact.name
        };

        if (group === false) {
            chatController.currentContact.email = contact.email;
            chatController.currentContact.publicKey = contact.publicKey;
            chatController.currentContact.phoneNumber = contact.phoneNumber;

            options.query.email = contact.email;
            options.query.publicKey = contact.publicKey;
            options.query.phoneNumber = contact.phoneNumber;
        }

        navigationController.loadPage('chat.html', pushCurrenPage, options);
    },

    loadContactInfo : function (contact) {
        var options = {
            url: "contactInfo.html",
            query: {
                contactId: contact.id
            }
        };

        navigationController.loadPage('contactInfo.html', true, options);
        slychat.closeModal();

        this.lastContactInfoId = contact.id;
    },

    getContact : function (id) {
        if(id in this.contacts)
            return this.contacts[id];
        else
            return false;
    },

    getBlockedContact : function () {
        var blocked = [];

        this.contacts.forEach(function (contact) {
            if (contact.allowedMessageLevel === "BLOCKED")
                blocked.push(contact);
        });

        return blocked;
    },

    blockedContactPageInit : function () {
        var blocked = this.getBlockedContact();

        $("#blockedContactsList").html(this.createBlockedContactsHtml(blocked));
    },

    createBlockedContactsHtml : function (contacts) {
        var frag = $(document.createDocumentFragment());

        if (contacts.length < 1)
            frag.html("No Blocked Contacts");
        else {
            contacts.forEach(function (contact) {
                frag.append(contactController.createBlockedContactNode(contact));
            });
        }

        return frag;
    },

    createBlockedContactNode : function (contact) {
        var html = $("<li id='blocked_" + contact.id + "' class='item-content'>" +
            "<div class='item-inner'>" +
            "<div class='item-title'>" + contact.email + "</div>" +
            "<div class='item-after'><a class='unblock-contact-button' type='button' style='cursor: pointer;'>Unblock</a></div>" +
            "</div>" +
            "</li>");

        html.find(".unblock-contact-button").click(function (e) {
            e.preventDefault();
            contactController.unblockContact(contact.id);
        });

        return html;
    },

    handleContactBlocked : function (contactId) {
        this.resetCachedConversation();
        slychat.addNotification({
            title: "Contact has been blocked",
            hold: 2000
        });

        if (chatController.getCurrentContactId() == contactId) {
            navigationController.loadPage("contacts.html", false);
        }
    },

    handleContactUnblocked : function (contactId) {
        this.resetCachedConversation();
        slychat.addNotification({
            title: "Contact has been unblocked",
            hold: 2000
        });

        if (navigationController.getCurrentPage() === "blockedContacts.html") {
            $("#blocked_" + contactId).remove();
        }
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
        var buttons = [];

        if (contact.publicKey == profileController.publicKey) {
            buttons.push({
                text: 'Profile',
                onClick: function () {
                    navigationController.loadPage('profile.html', true);
                }.bind(this)
            });
        }
        else {
            buttons.push({
                text: 'Contact Info',
                onClick: function () {
                    this.loadContactInfo(contact);
                }.bind(this)
            });

            if (contact.allowedMessageLevel == "BLOCKED") {
                buttons.push({
                    text: 'Unblock',
                    onClick: function () {
                        slychat.confirm("Are you sure you want to unblock " + contact.name, function () {
                            this.unblockContact(contact.id);
                        }.bind(this));
                    }.bind(this)
                });
            }
            else {
                buttons.push({
                    text: 'Block',
                    onClick: function () {
                        slychat.confirm("Are you sure you want to block " + contact.name, function () {
                            this.blockContact(contact.id);
                        }.bind(this));
                    }.bind(this)
                });
            }
        }

        buttons.push({
            text: 'Delete Conversation',
            onClick: function () {
                slychat.confirm("Are you sure you want to delete this conversation?", function () {
                    chatController.deleteConversation(contact);
                })
            }
        });

        buttons.push({
            text: 'Cancel',
            color: 'red',
            onClick: function () {
            }
        });

        slychat.actions(buttons);
    },

    openGroupConversationMenu : function (groupId) {
        var buttons = [
            {
                text: 'Group Info',
                onClick: function () {
                    groupController.loadGroupInfo(groupId);
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
        var buttons = [];
        if (contact.publicKey == profileController.publicKey) {
            buttons.push({
                text: 'Profile',
                onClick: function () {
                    navigationController.loadPage('profile.html', true);
                }.bind(this)
            });
        }
        else {
            buttons.push({
                text: 'Contact Info',
                onClick: function () {
                    this.loadContactInfo(contact);
                }.bind(this)
            });

            if (contact.allowedMessageLevel == "BLOCKED") {
                buttons.push({
                    text: 'Unblock',
                    onClick: function () {
                        slychat.confirm("Are you sure you want to unblock " + contact.name, function () {
                            this.unblockContact(contact.id);
                        }.bind(this));
                    }.bind(this)
                });
            }
            else {
                buttons.push({
                    text: 'Block',
                    onClick: function () {
                        slychat.confirm("Are you sure you want to block " + contact.name, function () {
                            this.blockContact(contact.id);
                        }.bind(this));
                    }.bind(this)
                });
            }

        }
        buttons.push({
            text: 'Delete Contact',
            onClick: function () {
                slychat.confirm("Are you sure you want to delete this conversation?", function () {
                    this.deleteContact(contact);
                }.bind(this))
            }.bind(this)
        });
        buttons.push({
            text: 'Cancel',
            color: 'red',
            onClick: function () {
            }
        });
        slychat.actions(buttons);
    },

    resetUnreadCount : function (id) {
        $('#leftContact_' + id).find(".left-menu-new-badge").remove();
        var recentDiv = $("#recentChat_" + id);
        recentDiv.find(".new-message-badge").remove();
        recentDiv.removeClass("new");
    },

    addConversationInfoUpdateListener : function () {
        messengerService.addConversationInfoUpdateListener(this.handleConversationUpdate.bind(this));
    },

    updateNewMessageBadge : function (id, count) {
        var node = $("#recentChat_" + id);
        if (count > 0) {
            node.addClass("new");

            var badge = node.find(".new-message-badge");
            if (badge.length <= 0)
                node.append('<div class="right new-message-badge">' + count + '</div>');
            else
                badge.html(count);

            chatController.leftMenuAddNewMessageBadge(id);
        }
        else {
            node.find(".new-message-badge").remove();
            node.removeClass("new");

            chatController.leftMenuRemoveNewMessageBadge(id);
        }
    },

    handleConversationUpdate : function (info) {
        var isGroup = info.groupId != null;
        if (isGroup) {
            groupController.updateConversationInfo(info);
            this.updateRecentGroupChatNode(info.groupId);
            this.updateNewMessageBadge(info.groupId, info.unreadCount);
        }
        else {
            this.updateConversationInfo(info);
            this.updateRecentChatNode(info.userId, info);
            this.updateNewMessageBadge(info.userId, info.unreadCount);
        }
    },

    updateConversationInfo : function (info) {
        if (this.conversations[info.userId] !== undefined && this.conversations[info.userId].status !== undefined) {
            if (info.lastMessageData == null) {
                this.conversations[info.userId].status = {
                    unreadMessageCount: 0,
                    lastMessage: null,
                    lastTimestamp: null,
                    online: true
                };
            }
            else {
                this.conversations[info.userId].status = {
                    unreadMessageCount: info.unreadCount,
                    lastMessage: info.lastMessageData.message,
                    lastTimestamp: info.lastMessageData.timestamp,
                    online: true
                };
            }
        }
    },

    clearCache : function () {
        this.conversations = [];
        this.sync = false;
        this.contactSyncNotification = null;
        this.contacts = [];
    }
};
