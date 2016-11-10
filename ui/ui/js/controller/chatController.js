var ChatController = function () {
    this.lastMessage = null;
    this.currentContact = null;
    this.lastMessageTtl = 0;
};

ChatController.prototype = {
    init : function () {
        this.addMessageUpdateListener();
        this.addNewMessageListener();
    },

    addMessageUpdateListener : function () {
        messengerService.addMessageStatusUpdateListener(function (event) {
            switch (event.type) {
                case 'DELIVERED':
                    var messageBlock = $("#message_" + event.messageId);
                    if(messageBlock.length){
                        var time = new Date(event.deliveredTimestamp - window.relayTimeDifference).toISOString();
                        messageBlock.find(".timespan").html("<time class='timeago' datetime='" + time + "' title='" + $.timeago(time) + "'>" + $.timeago(time) + "</time>");
                    }
                    break;

                case 'EXPIRING':
                    this.startExpiringMessageCountdown(event);
                    break;

                case 'EXPIRED':
                    this.destroyExpiringMessage(event.userId, event.groupId, event.messageId);
                    break;

                case 'DELETED':
                    this.handleDeletedMessage(event.userId, event.groupId, event.messageIds);
                    break;

                case 'DELETED_ALL':
                    this.handleDeletedMessage(event.userId, event.groupId);
                    break;
            }
        }.bind(this));
    },

    addNewMessageListener : function () {
        messengerService.addNewMessageListener(this.handleNewMessageDisplay.bind(this));
    },

    clearCache : function () {
        this.lastMessage = null;
    },

    createMessageNode : function (messageInfo, contact) {
        var message;
        var isGroup;
        if (messageInfo.info === undefined) {
            message = messageInfo;
            isGroup = false;
        }
        else {
            message = messageInfo.info;
            isGroup = true;
        }

        var classes = "";

        if(this.lastMessage == null)
            classes += " firstMessage";
        else {
            if ((message.sent && this.lastMessage.sent) || (!message.sent && !this.lastMessage.sent))
                classes += " followingMessage";
            else
                classes += " firstMessage";
        }


        if (message.sent === true)
            classes += " messageSent";
        else
            classes += " messageReceived";

        var timespan = "";
        if(message.sent && message.receivedTimestamp == 0){
            timespan = "Delivering...";
        }
        else {
            var time = new Date(message.timestamp - window.relayTimeDifference).toISOString();
            timespan = "<time class='timeago' datetime='" + time + "' title='" + $.timeago(time) + "'>" + $.timeago(time) + "</time>";
        }

        this.lastMessage = message;

        var contactName = "";
        if(isGroup && messageInfo.speaker !== null) {
            var groupContact = contactController.getContact(messageInfo.speaker);
            if(groupContact !== false)
                contactName = "<p style='font-size: 10px; color: #9e9e9e;'>" + groupContact.name + "</p>";
        }

        var expired = this.handleAutoDeleteMessage(message, contact, messageInfo.speaker);
        var messageCore;
        if (expired === true) {
            messageCore = $("<div class='message'>" + contactName +
                "<p>" + formatTextForHTML(createTextNode("Message has expired.")) + "</p>" +
                "</div>");
        }
        else if (expired instanceof jQuery) {
            messageCore = $("<div class='message message-hidden'>" +
                "</div>");
            messageCore.html(expired);
        }
        else {
            messageCore = $("<div class='message'>" + contactName +
                "<p>" + formatTextForHTML(createTextNode(message.message)) + "</p>" +
                "<span class='timespan'>" + timespan + "</span>" +
                "</div>");
            if (message.expiresAt > 0) {
                var secondsLeft = parseInt((new Date(message.expiresAt).getTime() - new Date().getTime()) / 1000);
                messageCore.append("<span class='expiring-timer'><span class='current-count'>" + secondsLeft + "</span> seconds</span>");

                function countdown () {
                    setTimeout(function () {
                        if (secondsLeft >= 1) {
                            secondsLeft -= 1;
                            messageCore.find('.current-count').html(secondsLeft);
                            countdown();
                        }
                    }, 1000);
                }
                countdown();
            }
        }

        var messageNode = $("<li id='message_" + message.id + "' class='" + classes + "'></li>");
        messageNode.html(messageCore);

        if (!(expired instanceof jQuery)) {
            messageNode.on("mouseheld", function () {
                vibrate(50);
                if (isGroup)
                    this.openGroupMessageMenu(messageInfo, contact);
                else
                    this.openMessageMenu(message);
            }.bind(this));
            messageNode.find(".timeago").timeago();
        }

        return messageNode;
    },

    handleAutoDeleteMessage : function (message, contact, speaker) {
        if (message.expired === true) {
            return true;
        }

        if (message.ttl == 0) {
            return false;
        }

        if (message.expiresAt == 0 && message.sent !== true) {
            return this.createShowExpiringMessageButton(message, contact, speaker);
        }

        return false;
    },

    handleDeletedMessage : function (userId, groupId, messageIds) {
        var id = userId == null ? groupId : userId;

        if (this.getCurrentContactId() == id) {
            if (messageIds === undefined) {
                navigationController.loadPage("contacts.html", false);
            }
            else {
                messageIds.forEach(function (messageId) {
                    $("#message_" + messageId).remove();
                });
            }
        }
    },

    createShowExpiringMessageButton : function (message, contact, speaker) {
        var button = $('<div style="width: auto; height: auto; font-size: 12px; text-align: center;"><i class="fa fa-bomb fa-2x" style="font-size: 35px;"></i>Tap to view message</div>');

        button.on('click', function (e) {
            e.preventDefault();
            e.stopPropagation();
            vibrate(100);
            var isGroup = contact.email === undefined;

            if (isGroup) {
                groupService.startMessageExpiration(contact, message.id).then(function () {
                    this.displayExpiringMessage(contact, message, speaker);
                }.bind(this)).catch(function (e) {
                    exceptionController.handleError(e);
                });
            }
            else {
                messengerService.startMessageExpiration(contact.id, message.id).then(function () {
                    this.displayExpiringMessage(contact, message, speaker);
                }.bind(this)).catch(function (e) {
                    exceptionController.handleError(e);
                });
            }

        }.bind(this));

        return button;
    },

    displayExpiringMessage : function (contact, message, speaker) {
        var isGroup = contact.email === undefined;
        var messageNode = $("#message_" + message.id);
        if (messageNode.length > 0) {

            var time = new Date(message.timestamp - window.relayTimeDifference).toISOString();
            var timespan = "<time class='timeago' datetime='" + time + "' title='" + $.timeago(time) + "'>" + $.timeago(time) + "</time>";
            var currentCount = parseInt(message.ttl / 1000);

            var contactName = "";
            if(isGroup && speaker !== undefined && speaker !== null) {
                var groupContact = contactController.getContact(speaker);
                if(groupContact !== false)
                    contactName = "<p style='font-size: 10px; color: #9e9e9e;'>" + groupContact.name + "</p>";
            }

            var messageHtml = $(contactName + "<p>" + formatTextForHTML(createTextNode(message.message)) + "</p>" +
                "<span class='timespan'>" + timespan + "</span>" +
                "<span class='expiring-timer'><span class='current-count'>" + currentCount + "</span> seconds</span>");

            function countdown () {
                setTimeout(function () {
                    if (currentCount >= 1) {
                        currentCount -= 1;
                        messageHtml.find('.current-count').html(currentCount);
                        countdown();
                    }
                }, 1000);
            }
            countdown();

            messageNode.on("mouseheld", function () {
                vibrate(50);
                this.openMessageMenu(message);
            }.bind(this));
            messageNode.find(".timeago").timeago();

            var messageCore = messageNode.find('.message');
            messageCore.html(messageHtml);
            messageCore.removeClass('message-hidden');
        }
    },

    destroyExpiringMessage : function (userId, groupId, messageId) {
        var contactId = $("#contact-id").html();
        var messageNode = $("#message_" + messageId);

        if (messageNode.length > 0 && (groupId == contactId || userId == contactId)) {
            messageNode.find('.message').removeClass("message-hidden").html("<p>" + formatTextForHTML(createTextNode("Message has expired.")) + "</p>");
        }
    },

    startExpiringMessageCountdown : function (event) {
        var ttl = event.ttl;
        var contactId = $("#contact-id").html();
        var messageNode = $("#message_" + event.messageId);

        if (messageNode.length > 0 && messageNode.find('.expiring-timer').length <= 0 && (event.groupId == contactId || event.userId == contactId)) {
            var currentCount = parseInt(ttl/1000);
            var timer = $("<span class='expiring-timer'><span class='current-count'>" + currentCount + "</span> seconds</span>");
            messageNode.find('.message').append(timer);

            function countdown () {
                setTimeout(function () {
                    if (currentCount >= 1) {
                        currentCount -= 1;
                        timer.find('.current-count').html(currentCount);
                        countdown();
                    }
                }, 1000);
            }
            countdown();
        }
    },

    deleteConversation : function (contact) {
        messengerService.deleteAllMessagesFor(contact.id).then(function () {
            contactController.resetCachedConversation();
            if(this.getCurrentContactId() == contact.id)
                navigationController.loadPage("contacts.html", false);
        }.bind(this)).catch(function (e) {
            exceptionController.handleError(e);
        }.bind(this));
    },

    deleteMessage : function (id, contact) {
        var messageIds = [];
        if(id.constructor !== Array)
            messageIds.push(id);
        else
            messageIds = id;

        messengerService.deleteMessagesFor(contact.id, messageIds).then(function () {
            messageIds.forEach(function (id) {
                $("#message_" + id).remove();
            });
        }.bind(this)).catch(function (e) {
            console.log("Could not delete messages for contact id " + contact.id);
            // TODO handle errors
        })
    },

    displayMessage : function (messages, contact, isGroup) {
        this.lastMessage = null;
        var frag = $(document.createDocumentFragment());

        for(var k in messages) {
            if (messages.hasOwnProperty(k)) {
                frag.append(this.createMessageNode(messages[k], contact));
            }
        }

        $("#chat-content").html(frag);
        this.scrollTop();
    },

    fetchMessageFor : function (start, count, contact) {
        messengerService.getLastMessagesFor(contact.id, start, count).then(function (messages) {
            var organizedMessages = this.organizeMessages(messages);
            this.displayMessage(organizedMessages, contact);

        }.bind(this)).catch(function (e) {
            console.error("Unable to fetch messages: " + e);
        });
    },

    getCurrentContactId : function () {
        if (navigationController.getCurrentPage() === "chat.html") {
            return $("#contact-id").html();
        }
        return false;
    },

    handleNewMessageDisplay : function(messageInfo) {
        if(messageInfo.messages.length <= 0)
            return;

        var messages = messageInfo.messages;
        var contactId = messageInfo.contact;

        //Get the contact that sent the message
        var cachedContact = contactController.getContact(contactId);

        if (messageInfo.groupId === null) {
            if(!cachedContact) {
                console.log("No cached contact for " + contactId);
                return;
            }

            this.updateChatPageNewMessage(messages, cachedContact.name, contactId);
        }
        else {
            this.updateGroupChatPageNewMessage(messageInfo);
        }

        $(".timeago").timeago();
    },

    leftMenuAddNewMessageBadge : function (id) {
        var node = $("#leftContact_" + id);
        if (this.getCurrentContactId() != id) {
            if (node.find(".left-menu-new-badge").length <= 0)
                node.append('<span class="left-menu-new-badge" style="color: red; font-size: 12px; margin-left: 5px;">new</span>');
        }
    },

    leftMenuRemoveNewMessageBadge : function (id) {
        $("#leftContact_" + id).find(".left-menu-new-badge").remove();
    },

    openGroupMessageMenu : function (message, groupId) {
        var contact = contactController.getContact($('#contact-id').html());

        var buttons = [
            {
                text: 'Message Info',
                onClick: function () {
                    this.showGroupMessageInfo(message, groupId);
                }.bind(this)
            },
            {
                text: 'Delete message',
                onClick: function () {
                    slychat.confirm("Are you sure you want to delete this message?", function () {
                        groupController.deleteMessages(groupId, [message.info.id]);
                    }.bind(this))
                }.bind(this)
            },
            {
                text: 'Copy Text',
                onClick: function () {
                    copyToClipboard(message.info.message);
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

    openGroupPageMenu : function (groupId) {
        var group = groupController.getGroup(groupId);
        var buttons = [
            {
                text: 'Group Info',
                onClick: function () {
                    groupController.loadGroupInfo(groupId);
                }
            },
            {
                text: 'Delete messages',
                onClick: function () {
                    slychat.confirm("Are you sure you want to delete all messages?", function () {
                        groupController.deleteAllMessages(groupId);
                    }.bind(this))
                }
            },
            {
                text: "Invite Contacts",
                onClick: function () {
                    groupController.openInviteUsersModal(groupId);
                }
            },
            {
                text: 'Leave Group',
                onClick: function () {
                    slychat.confirm("Are you sure you want to leave the group?", function () {
                        groupController.leaveGroup(groupId);
                    });
                }
            },
            {
                text: 'Block Group',
                onClick: function () {
                    slychat.confirm("Are you sure you want to block this group? </br> You won't receive any more messages.", function () {
                        groupController.blockGroup(groupId);
                    }.bind(this));
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

    openContactPageMenu : function (contactId) {
        var contact = contactController.getContact(contactId);
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
                    contactController.loadContactInfo(contact);
                }.bind(this)
            });

            if (contact.allowedMessageLevel == "BLOCKED") {
                buttons.push({
                    text: 'Unblock',
                    onClick: function () {
                        slychat.confirm("Are you sure you want to unblock " + contact.name, function () {
                            contactController.unblockContact(contact.id);
                        }.bind(this));
                    }.bind(this)
                });
            }
            else {
                buttons.push({
                    text: 'Block',
                    onClick: function () {
                        slychat.confirm("Are you sure you want to block " + contact.name, function () {
                            contactController.blockContact(contact.id);
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
        },
        {
            text: 'Cancel',
            color: 'red',
            onClick: function () {
            }
        });

        slychat.actions(buttons);
    },

    openMessageMenu : function (message) {
        var contact = contactController.getContact($('#contact-id').html());

        var buttons = [
            {
                text: 'Message Info',
                onClick: function () {
                    this.showMessageInfo(message, contact);
                }.bind(this)
            },
            {
                text: 'Delete message',
                onClick: function () {
                    slychat.confirm("Are you sure you want to delete this message?", function () {
                        // TODO update confirm style
                        this.deleteMessage(message.id, contact);
                    }.bind(this))
                }.bind(this)
            },
            {
                text: 'Copy Text',
                onClick: function () {
                    copyToClipboard(message.message);
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

    organizeGroupMessages : function (messages) {
        messages.reverse();

        var organizedMessages = [];
        messages.forEach(function (message) {
            organizedMessages[message.info.id] = message;
        });

        return organizedMessages;
    },

    organizeMessages : function (messages) {
        messages.reverse();

        var organizedMessages = [];
        messages.forEach(function (message) {
            organizedMessages[message.id] = message;
        });

        return organizedMessages;
    },

    scrollTop : function () {
        var offset = $("#chat-content").height();
        $("[data-page='chat'] #chatPageContent").scrollTop(offset);
    },

    showGroupMessageInfo : function (message, groupId) {
        var contactDiv = "";
        var receivedTime = "";
        var groupName = "";
        var group = groupController.getGroup(groupId);
        var members = groupController.getGroupMembers(groupId);

        var memberList = "";

        members.forEach(function (member) {
            memberList += "<div class='member'>" +
                "<span>" + member.name + "</span>" +
                "<span>" + member.email + "</span>" +
                "</div>";
        });

        if (message.speaker === null){
            contactDiv = "<div class='message-info'>" +
                "<p class='message-info-title'>Sent To Group:</p>" +
                "<p class='message-info-details'>" + group.name + "</p>" +
                "</div>";
        }
        else {
            var contact = contactController.getContact(message.speaker);

            contactDiv = "<div class='message-info'>" +
                "<p class='message-info-title'>Contact Name:</p>" +
                "<p class='message-info-details'>" + contact.name + "</p>" +
                "</div>" +
                "<div class='message-info'>" +
                "<p class='message-info-title'>Contact Email:</p>" +
                "<p class='message-info-details'>" + contact.email + "</p>" +
                "</div>" +
                "<div class='message-info'>" +
                "<p class='message-info-title'>Contact Public Key:</p>" +
                "<p class='message-info-details'>" + formatPublicKey(contact.publicKey) + "</p>" +
                "</div>";

            receivedTime = "<div class='message-info'>" +
                "<p class='message-info-title'>Message Received Time:</p>" +
                "<p class='message-info-details'>" + message.info.receivedTimestamp + "</p>" +
                "</div>";

            groupName = "<div class='message-info'>" +
                "<p class='message-info-title'>Group Name:</p>" +
                "<p class='message-info-details'>" + group.name + "</p>" +
                "</div>";
        }

        var content = contactDiv +
            '<div class="message-info">' +
            '<p class="message-info-title">Message id:</p>'+
            '<p class="message-info-details">' + message.info.id + '</p>' +
            '</div>'+
            '<div class="message-info">' +
            '<p class="message-info-title">Sent Time:</p>'+
            '<p class="message-info-details">' + message.info.timestamp + '</p>' +
            '</div>' +
            receivedTime +
            '<div class="message-info">' +
            '<p class="message-info-title">Message is encrypted <i class="fa fa-check-square color-green"></i></p>' +
            '</div>' +
            "<div class='group-info'>" +
            "<p class='group-info-title'>Group Id:</p>" +
            "<p class='group-info-details'>" + group.id + "</p>" +
            "</div>" +
            groupName +
            '<div class="group-info">' +
            '<p class="group-info-title">Group Members:</p>'+
            '<div class="group-info-details"><div class="members">' + memberList + '</div></div>' +
            '</div>';

        openInfoPopup(content, "Message Info");
    },

    showMessageInfo : function (message, contact) {
        var contactDiv = "";
        var receivedTime = "";

        if (message.sent === true){
            contactDiv = "<div class='message-info'>" +
                "<p class='message-info-title'>Sent To:</p>" +
                "<p class='message-info-details'>" + contact.name + "</p>" +
                "</div>";
        }
        else {
            contactDiv = "<div class='message-info'>" +
                "<p class='message-info-title'>Contact Name:</p>" +
                "<p class='message-info-details'>" + contact.name + "</p>" +
                "</div>" +
                "<div class='message-info'>" +
                "<p class='message-info-title'>Contact Email:</p>" +
                "<p class='message-info-details'>" + contact.email + "</p>" +
                "</div>";

            receivedTime = "<div class='message-info'>" +
                "<p class='message-info-title'>Message Received Time:</p>" +
                "<p class='message-info-details'>" + message.receivedTimestamp + "</p>" +
                "</div>";
        }

        var content = contactDiv +
            "<div class='message-info'>" +
            "<p class='message-info-title'>Contact Public Key:</p>" +
            "<p class='message-info-details'>" + formatPublicKey(contact.publicKey) + "</p>" +
            "</div>" +
            '<div class="message-info">' +
            '<p class="message-info-title">Message id:</p>'+
            '<p class="message-info-details">' + message.id + '</p>' +
            '</div>'+
            '<div class="message-info">' +
            '<p class="message-info-title">Sent Time:</p>'+
            '<p class="message-info-details">' + message.timestamp + '</p>' +
            '</div>' +
            receivedTime +
            '<div class="message-info">' +
            '<p class="message-info-title">Message is encrypted <i class="fa fa-check-square color-green"></i></p>' +
            '</div>';

        openInfoPopup(content, "Message Info");
    },

    handleSubmitMessage : function (contact) {
        var ttl = 0, mainView = $("#mainView");
        if (mainView.hasClass('expire-message-toggled')) {
            var slider = document.getElementById("delaySlider");
            if (slider !== null) {
                ttl = parseInt(slider.noUiSlider.get()) * 1000;
                if (ttl !== 0) {
                    this.lastMessageTtl = ttl;
                    configService.setLastMessageTtl(ttl);
                }
            }
        }

        var newMessageInput = $("#newMessageInput"), message;
        if (isDesktop) {
            message = newMessageInput.val();
        }
        else {
            var area = newMessageInput.emojioneArea();
            if (area.length <= 0 || typeof area[0].emojioneArea === "undefined")
                message = newMessageInput.val();
            else
                message = area[0].emojioneArea.getText();
        }

        if (message !== "") {
            this.submitNewMessage(contact, message, ttl);
        }
    },

    submitNewMessage : function (contact, message, ttl) {
        if (ttl === undefined)
            ttl = 0;

        if (contact.email === undefined) {
            messengerService.sendGroupMessageTo(contact.id, message, ttl).then(this.handleSubmitMessageSuccess()).catch(function (e) {
                exceptionController.handleError(e);
            })
        }
        else {
            messengerService.sendMessageTo(contact.id, message, ttl).then(this.handleSubmitMessageSuccess()).catch(function (e) {
                console.log(e);
            });
        }
    },

    handleSubmitMessageSuccess : function () {
        var input = $("#newMessageInput");
        if (isDesktop) {
            input.val("");
            input.click();
        }
        else {
            var area = input.emojioneArea();
            if (area.length <= 0 || typeof area[0].emojioneArea === "undefined")
                input.val("");
            else {
                area[0].emojioneArea.setText("");
                area[0].emojioneArea.setFocus();
            }
        }
        this.scrollTop();
    },

    updateChatPageNewMessage : function (messages, contactName, contactId) {
        if(contactId == this.getCurrentContactId()){
            var messageDiv = $("#chat-content");

            if(messageDiv.length){
                var contact = contactController.getContact(contactId);
                vibrate(100);

                var fragment = $(document.createDocumentFragment());
                messages.forEach(function (message) {
                    fragment.append(this.createMessageNode(message, contact));
                }, this);

                messageDiv.append(fragment);
                this.scrollTop();
            }
        }
    },

    updateGroupChatPageNewMessage : function (messagesInfo) {
        var messages = messagesInfo.messages;
        var currentPageContactId = $("#contact-id");
        if(navigationController.getCurrentPage() == "chat.html" && currentPageContactId.length && currentPageContactId.html() == messagesInfo.groupId){
            var messageDiv = $("#chat-content");

            if(messageDiv.length){
                vibrate(100);
                var fragment = $(document.createDocumentFragment());

                messages.forEach(function (message) {
                    var messageInfo = {
                        info: message,
                        speaker: messagesInfo.contact
                    };
                    fragment.append(this.createMessageNode(messageInfo, messagesInfo.groupId));
                }, this);

                messageDiv.append(fragment);

                this.scrollTop();
            }
        }
    },

    toggleExpiringMessageDisplay : function () {
        var mainView = $("#mainView");
        var bottomToolbar = $(".bottom-chat-toolbar");
        var editor = $(".emojionearea-editor");

        if (mainView.hasClass("expire-message-toggled")) {
            mainView.removeClass("expire-message-toggled");
            bottomToolbar.removeClass("expiring-message-toolbar");
            bottomToolbar.find("#delaySliderContainer").remove();
            editor.attr("placeholder", "Type your secure message");
        }
        else {
            mainView.addClass("expire-message-toggled");
            bottomToolbar.addClass("expiring-message-toolbar");
            this.createExpireDelaySlider(bottomToolbar);
            editor.attr("placeholder", "Type your expiring secure message");
        }
    },

    createExpireDelaySlider : function (toolbar) {
        var sliderContainer = $('<div id="delaySliderContainer">' +
            '<div id="delaySlider" style="margin: 0 10px;"></div>' +
            '<div style="color: #a9a9a9; font-size: 10px; float: right; padding-right: 5px;">' +
            '<span>Self Destruct: <span id="delayDisplay">10</span> seconds</span></div></div>');

        toolbar.prepend(sliderContainer);

        var slider = document.getElementById('delaySlider');

        noUiSlider.create(slider, {
            start: [Math.floor(this.lastMessageTtl / 1000)],
            step: 1,
            range: {
                min: [1],
                max: [120]
            },
            format: wNumb({
                decimals: 0
            })
        });

        $("#delayDisplay").html(slider.noUiSlider.get());

        slider.noUiSlider.on('slide', function () {
            $("#delayDisplay").html(slider.noUiSlider.get());
        })
    }
};
