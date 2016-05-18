var ChatController = function (model, contactController) {
    this.model = model;
    this.model.setController(this);
    this.contactController = contactController;
    this.currentMessagePosition = 0;
    this.fetchingNumber = 100;
    this.lastMessage = null;
    this.messageMenu = this.createMessagesMenu();
    this.selectedMessage = [];
    this.selectMode = false;
};

ChatController.prototype = {
    init : function () {
        var contact = this.contactController.getCurrentContact();
        this.model.fetchMessage(this.currentMessagePosition, this.fetchingNumber, contact);
        this.model.markConversationAsRead(contact);

        this.selectedMessage = [];
        this.selectMode = false;

        $("#newMessageSubmitBtn").click(function (){
            $("#newMessageInput").focus();
        });

        $("#newMessageForm").submit(function () {
            $("#newMessageInput").trigger("click");
            this.submitNewMessage();
            return false;
        }.bind(this));

        $('#newMessageInput').on("keypress", function(e) {
            if  (e.keyCode === 13 && !e.ctrlKey) {
                e.preventDefault();
                $("#newMessageForm").submit();
            }
        });
    },
    displayMessage : function (messages, contact) {
        var messageNode = $("#messages");

        var fragment = $(document.createDocumentFragment());
        this.lastMessage = null;
        for(var k in messages) {
            if(messages.hasOwnProperty(k)) {
                fragment.append(this.createMessageNode(messages[k], contact.name));
            }
        }
        messageNode.html(fragment);
        this.scrollTop();
    },
    /**
     * Create all event relative to messages manipulation options.
     */
    createMessageNodeEvent : function () {
        $(document).on("click", "[id^='message_']", function (e) {
            e.preventDefault();
            if(KEYTAP.chatController.selectMode == true) {
                if ($(this).hasClass("noclick")) {
                    $(this).removeClass("noclick");
                }
                else {
                    if ($(this).hasClass("message_selected")) {
                        KEYTAP.chatController.deselectMessage($(this));
                    }
                    else {
                        KEYTAP.chatController.selectMessage($(this));
                    }
                }
            }
            else {
                $(this).find(".message-details").toggle();
            }
        });

        $(document).on("click", "#copyMessage", function (e) {
            e.preventDefault();
            if(this.selectedMessage.length == 1) {
                var messageId = this.selectedMessage[0];
                var messageText = this.model.cachedConversation[this.contactController.getCurrentContact().id][messageId].message;
                if(typeof messageText !== "undefined" || messageText !== "" || messageText != null) {
                    windowService.copyTextToClipboard(messageText).then(function () {
                        this.messageMenu.close();
                        $.notify({
                            icon: "icon-pull-left fa fa-info-circle",
                            message: "Message has been copied to clipboard"
                        }, {
                            type: "success",
                            delay: 3000,
                            allow_dismiss: false,
                            offset: {
                                y: 66,
                                x: 20
                            }
                        });
                    }.bind(this)).catch(function (e) {
                        KEYTAP.exceptionController.displayDebugMessage(e);
                        console.log("An error occured, could not copy message to clipboard.");
                    })
                }
            }
        }.bind(this));

        $(document).on("click", "#deleteMessage", function (e) {
            e.preventDefault();
            this.messageMenu.close();
            this.createDeleteMessageConfirmDialog(false);
        }.bind(this));

        $(document).on("click", "#confirmDeleteMessage", function (e) {
            e.preventDefault();
            if(this.selectedMessage.length >= 1) {
                this.deleteMessage(this.contactController.getCurrentContact(), this.selectedMessage);
            }
            this.selectedMessage = [];
            BootstrapDialog.closeAll();
        }.bind(this));

        $(document).on("click", "#cancelCloseModal", function (e) {
            e.preventDefault();
            this.selectedMessage = [];
            this.selectMode = false;
            $(".message_selected").each(function (index, item) {
                this.deselectMessage($(item));
            }.bind(this));
            BootstrapDialog.closeAll();
        }.bind(this));

        $(document).on("click", "#deleteMultipleMessage", function (e) {
            e.preventDefault();
            this.selectMode = true;
            this.selectedMessage.forEach(function (id) {
                this.selectMessage($("#message_" + id));
            }.bind(this));
            this.messageMenu.close();
            $("#main").prepend(this.createMultipleDeleteMenu());
        }.bind(this));

        $(document).on("click", "#cancelMultipleDeleteButton", function (e) {
            e.preventDefault();
            this.selectMode = false;
            $(".message_selected").each(function (index, item) {
                this.deselectMessage($(item));
            }.bind(this));
            $("#multipleDeleteMenu").remove();
            this.selectedMessage = [];
        }.bind(this));

        $(document).on("click", "#confirmMultipleDeleteButton", function (e) {
            e.preventDefault();
            var messagesSelected = $(".message_selected");
            if(messagesSelected.length >= 1) {
                this.selectedMessage = [];
                messagesSelected.each(function (index, item) {
                    this.selectedMessage.push($(item).attr("id").split("_")[1]);
                }.bind(this));

                $("#multipleDeleteMenu").remove();
                this.createDeleteMessageConfirmDialog(true);
            }
        }.bind(this));

        $(document).on("click", "#confirmDeleteConversation", function (e) {
            e.preventDefault();
            BootstrapDialog.closeAll();
            this.deleteConversation(KEYTAP.contactController.getCurrentContact().id);
        }.bind(this));

        $(document).on("click", "[id^='deleteContact_']", function (e) {
            e.preventDefault();
            var id = $(this).attr("id").split("_")[1];
            BootstrapDialog.closeAll();
            KEYTAP.contactController.displayDeleteContactModal(id);
        });

        $(document).on("click", "[id^='contactDetails_']", function (e) {
            e.preventDefault();
            var id = $(this).attr("id").split("_")[1];
            BootstrapDialog.closeAll();
            KEYTAP.contactController.displayContactDetailsModal(id);
        });
    },
    /**
     * Select the element and add styling to the node.
     *
     * @param messageElement Jquery element.
     */
    selectMessage : function (messageElement) {
        messageElement.addClass("message_selected");
        messageElement.prepend("<div class='check'><i class='fa fa-check fa-3x'></i>");
    },
    /**
     * Deselect message and remove selected style.
     *
     * @param messageElement Jquery element.
     */
    deselectMessage : function (messageElement) {
        messageElement.removeClass("message_selected");
        messageElement.children(".check").remove();
    },
    /**
     * Create a context like menu for messages manipulation options.
     *
     * @returns {BootstrapDialog}
     */
    createMessagesMenu : function () {
        var html = "<div class='contextLikeMenu' id='messageContextMenu'>" +
            "<ul>" +
                "<li><a id='copyMessage' href='#'>Copy Message Text</a></li>" +
                "<li role='separator' class='divider'></li>" +
                "<li><a id='deleteMessage' href='#'>Delete Message</a></li>" +
                "<li role='separator' class='divider'></li>" +
                "<li><a id='deleteMultipleMessage' href='#'>Delete Multiple Messages</a></li>" +
            "</ul>" +
        "</div>";

        return createContextLikeMenu(html, true);
    },
    /**
     * Create a modal to confirm deletion of the message(s).
     *
     * @param multiple Bool multiple message delete.
     */
    createDeleteMessageConfirmDialog : function (multiple) {
        var title = "";
        if(multiple)
            title = "Delete the selected messages?";
        else
            title = "Delete this message?";

        var html = "<div class='contextLikeModalContent'>" +
            "<h6 class='contextLikeModal-title'>" + title + "</h6>" +
            "<p class='contextLikeModal-content'>Are you sure?<br> This action cannot be undone.</p>" +
            "<div class='contextLikeModal-nav'>" +
                "<button id='cancelCloseModal' class='btn btn-sm transparentBtn'>Cancel</button>" +
                "<button id='confirmDeleteMessage' class='btn btn-sm transparentBtn'>Confirm</button>" +
            "</div>" +
        "</div>";


        var modal = createContextLikeMenu(html, false);
        modal.open();
    },
    /**
     * Create a modal to confirm deletion of whole conversation.
     *
     * @param contact Contact
     */
    createDeleteWholeConversationDialog : function (contact) {
        var html = "<div class='contextLikeModalContent'>" +
            "<h6 class='contextLikeModal-title'>Delete Conversation?</h6>" +
            "<p class='contextLikeModal-content'>Are you sure you want to delete your conversation with " + contact.name + "</p>" +
            "<div class='contextLikeModal-nav'>" +
                "<button id='cancelCloseModal' class='btn btn-sm transparentBtn'>Cancel</button>" +
                "<button id='confirmDeleteConversation' class='btn btn-sm transparentBtn'>Confirm</button>" +
            "</div>" +
        "</div>";

        var modal = createContextLikeMenu(html, false);
        modal.open();
    },
    /**
     * Create top menu for multiple messages delete.
     *
     * @returns {string}
     */
    createMultipleDeleteMenu : function () {
        return "<div id='multipleDeleteMenu'>" +
            "<button id='cancelMultipleDeleteButton' class='btn btn-sm'>Cancel</button>" +
            "<button id='confirmMultipleDeleteButton' class='btn btn-sm'>Delete</button>" +
        "</div>";
    },
    /**
     * Create event to open messages links into a new window.
     */
    createChatLinkEvent : function () {
        $(document).on("click", ".chatLink", function (e) {
            e.preventDefault();
            e.stopPropagation();
            KEYTAP.navigationController.loadMessageLink(this.href);
        });
    },
    /**
     * Responsible to create each message node in chat.
     *
     * @param message Array of message details.
     * @param contactName contact name.
     * @returns {*|jQuery|HTMLElement}
     */
    createMessageNode : function (message, contactName) {
        if(message.sent == true)
            contactName = KEYTAP.profileController.getUserInfo().name;

        var fromClass = "";

        var node = $(document.createElement("li"));
        node.attr("id", "message_" + message.id);

        if(message.timestamp != null)
            message.time = message.timestamp;
        else
            message.time = null;

        if(this.lastMessage != null) {
            if(this.lastMessage.sent == true && message.sent == true) {
                if(message.time == null || this.lastMessage.time == null)
                    fromClass = "message-right following-message";
                else if(this.compareTime(this.lastMessage.time, message.time) == true)
                    fromClass = "message-right following-message";
                else {
                    fromClass = "message-right first-message";
                    node.append(createAvatar(contactName));
                }
            }
            else if(this.lastMessage.sent == true && message.sent == false) {
                fromClass = "message-left first-message";
                node.append(createAvatar(contactName));
            }
            else if(this.lastMessage.sent == false && message.sent == true) {
                fromClass = "message-right first-message";
                node.append(createAvatar(contactName));
            }
            else if(this.lastMessage.sent == false && message.sent == false) {
                if(this.compareTime(this.lastMessage.time, message.time) == true)
                    fromClass = "message-left following-message";
                else {
                    fromClass = "message-left first-message";
                    node.append(createAvatar(contactName));
                }
            }
        }
        else {
            if(message.sent == true) {
                fromClass = "message-right first-message";
                node.append(createAvatar(contactName));
            }
            else {
                fromClass = "message-left first-message";
                node.append(createAvatar(contactName));
            }
        }

        node.addClass(fromClass);

        var messageDiv = $(document.createElement("div"));
        messageDiv.addClass("message ");

        messageDiv.append("<p>" + formatTextForHTML(createTextNode(message.message)) + "</p>");

        var timespan = $(document.createElement("span"));
        timespan.addClass("timespan");

        if(message.sent && message.receivedTimestamp == 0){
            timespan.html("Delivering...");
        }
        else {
            timespan.html($.timeago(new Date(message.timestamp).toISOString()));
        }

        var messageDetailsTime = $(document.createElement("p"));
        messageDetailsTime.addClass("message-details");
        messageDetailsTime.html("Received at " + new Date(message.timestamp).toLocaleString() + ".");

        var messageDetailsSecure = $(document.createElement("p"));
        messageDetailsSecure.addClass("message-details");
        messageDetailsSecure.html("<i class='fa fa-lock' style='color: green; margin-right: 3px;'></i> This Message is secure.");

        messageDiv.append(timespan);
        messageDiv.prepend(messageDetailsTime);
        messageDiv.append(messageDetailsSecure);
        node.append(messageDiv);

        this.lastMessage = message;

        //create the mouse hold event to open message menu
        node.on("mouseheld", function (e) {
            if(KEYTAP.chatController.selectMode == false) {
                vibrate(50);
                KEYTAP.chatController.selectedMessage = [$(this).attr("id").split("_")[1]];
                KEYTAP.chatController.messageMenu.open();
            }
        });

        return node;
    },
    /**
     * Responsible for sending new message to the backend.
     */
    submitNewMessage : function () {
        var message = $('#newMessageInput').val();
        if(message != ""){
            var currentContact = this.contactController.getCurrentContact();

            messengerService.sendMessageTo(currentContact, message).then(function (messageDetails) {
                this.model.pushNewMessage(currentContact.id, messageDetails);
                var input = $('#newMessageInput');
                input.val("");
                $("#messages").append(this.createMessageNode(messageDetails, KEYTAP.profileController.getUserInfo().name));
                this.scrollTop();
                input.click();
            }.bind(this)).catch(function (e) {
                KEYTAP.exceptionController.displayDebugMessage(e);
                console.log(e);
            });
        }
    },
    compareTime : function (t1, t2) {
        if(t1 > t2)
            return false;
        else if(t2 - t1 > 300000)
            return false;
        else if(t2 - t1 < 300000)
            return true;

        return false;
    },
    addMessageUpdateListener : function () {
        messengerService.addMessageStatusUpdateListener(function (messageInfo) {
            messageInfo.messages.forEach(function (message) {
                this.model.updateMessage(messageInfo.contact, message);
                var messageDiv = $("#message_" + message.id);

                if(messageDiv.length && message.sent == true){
                    messageDiv.find(".timespan").html($.timeago(new Date(message.timestamp).toISOString()));
                }
            }.bind(this));
        }.bind(this));
    },
    addNewMessageListener : function () {
        messengerService.addNewMessageListener(this.handleNewMessageDisplay.bind(this));
    },
    handleNewMessageDisplay : function(messageInfo) {
        if(messageInfo.messages.length <= 0)
            return;

        var messages = messageInfo.messages;
        var contactId = messageInfo.contact;

        messages.forEach(function (message) {
            this.model.pushNewMessage(contactId, message);
        }.bind(this));

        //Get the contact that sent the message
        var cachedContact = this.contactController.getContact(contactId);
        if(!cachedContact) {
            console.error("No cached contact for " + contactId);
            return;
        }
        var contactName = cachedContact.name;

        this.updateChatPageNewMessage(messages, contactName, contactId);
        this.updateContactPageNewMessage(contactId);
        this.updateRecentChatNewMessage(messages, contactId);
    },
    updateRecentChatNewMessage : function (messages, contactId) {
        var recentContent = $("#recentChatList");
        if(recentContent.length){
            var recentBlock = $("#recent_" + contactId);
            if(recentBlock.length) {
                vibrate(100);
                var contactDiv = recentBlock.find(".contact");
                if (!recentBlock.hasClass("new-messages")) {
                    recentBlock.addClass("new-messages");
                    contactDiv.after("<span class='pull-right label label-warning' style='line-height: 0.8'>" + "new" + "</span>");
                }

                contactDiv.find(".recentTimestamp").html($.timeago(new Date(messages[messages.length - 1].timestamp).toISOString()));
                contactDiv.find(".recentMessage").html(messages[messages.length - 1].message);
            }
        }
    },
    updateContactPageNewMessage : function (contactId) {
        var contactContent = $("#contactList");
        if(contactContent.length){
            vibrate(100);
            var contactBlock = $("#contact_" + contactId);
            if(contactBlock.length) {
                if (!contactBlock.hasClass("new-messages")) {
                    var contactDiv = contactBlock.find(".contact");
                    contactBlock.addClass("new-messages");
                    contactDiv.after("<span class='pull-right label label-warning' style='line-height: 0.8'>" + "new" + "</span>");
                }
            }
        }
    },
    /**
     * Update the chat page with new messages.
     * If the current page is chat.
     *
     * @param messages Array of message details.
     * @param contactName String of contact name.
     * @param contactId Contact id.
     */
    updateChatPageNewMessage : function (messages, contactName, contactId) {
        var currentPageId = $("#currentPageChatId");
        if(currentPageId.length && currentPageId.html() == contactId){
            var messageDiv = $("#messages");

            if(messageDiv.length){
                vibrate(100);
                //for the common case
                if(messages.length == 1) {
                    var message = messages[0];
                    messageDiv.append(this.createMessageNode(message, contactName));
                }
                else {
                    var fragment = $(document.createDocumentFragment());
                    messages.forEach(function (message) {
                        fragment.append(this.createMessageNode(message, contactName));
                    }, this);
                    messageDiv.append(fragment);
                }
                this.scrollTop();
                this.model.markConversationAsRead(this.contactController.getCurrentContact());
            }
        }
    },
    /**
     * Scroll the chat page to the last message.
     */
    scrollTop : function () {
        var lastMessage = $("ul#messages li:last");

        if(typeof lastMessage != "undefined" && typeof lastMessage.offset() != "undefined")
            $("#content").scrollTop(lastMessage.offset().top + $(".chat").height());
    },
    /**
     * Deletes the whole conversation.
     *
     * @param id contactId
     */
    deleteConversation : function (id) {
        messengerService.deleteAllMessagesFor(KEYTAP.contactController.getContact(id)).then(function () {
            this.clearCache();
            BootstrapDialog.closeAll();
            KEYTAP.navigationController.loadPage("chat.html", false);
            this.openNotification("The conversation has been deleted successfully.", "success");
        }.bind(this)).catch(function (e) {
            BootstrapDialog.closeAll();
            KEYTAP.exceptionController.displayDebugMessage(e);
            this.openNotification("The conversation could not be deleted.", "warning");
            console.log("couldn't delete the conversation ");
            console.log(e);
        });
    },
    /**
     * Deletes all the given messages from the given contact's conversation.
     *
     * @param contact Contact .
     * @param messageIds Array of message IDs to delete.
     */
    deleteMessage : function (contact, messageIds) {
        messengerService.deleteMessagesFor(contact, messageIds).then(function () {
            this.clearCache();
            KEYTAP.navigationController.loadPage("chat.html", false);
            this.openNotification("The message(s) has been deleted successfully.", "success");
        }.bind(this)).catch(function (e) {
            KEYTAP.exceptionController.displayDebugMessage(e);
            console.log("Could not delete messages for contact id " + contactId);
            this.openNotification("The message(s) could not be deleted.", "warning");
            console.log(e);
        })
    },
    /**
     * Open a new top notification.
     *
     * @param message String.
     * @param type String.
     */
    openNotification : function (message, type) {
        $.notify({
            icon: "icon-pull-left fa fa-info-circle",
            message: message
        }, {
            type: type,
            delay: 2000,
            allow_dismiss: false,
            offset: {
                y: 66,
                x: 20
            }
        });
    },
    /**
     * Clear the ui side message cache.
     */
    clearCache : function () {
        this.model.clearCache();
    }
};
