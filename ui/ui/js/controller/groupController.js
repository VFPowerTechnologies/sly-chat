var GroupController = function () {
    this.groups = [];
    this.groupDetailsCache = [];

    /*
        this.groupDetailsCache = [
            groupId: {
                group: {
                    name:  'groupName',
                    id: 'groupId'
                },
                info: {
                    lastSpeaker: 'userId',
                    unreadMessageCount: 0,
                    lastMessage: 'Hey!',
                    lastTimestamp: timestamp
                },
                members: [
                    {
                        id: 'user id',
                        name: 'user name',
                        email: 'user email',
                        phoneNumber: 'user phoneNumber',
                        publicKey: 'user publicKey'
                    }
                ]
            }
        ]
     */
};

GroupController.prototype = {
    init : function () {
        this.fetchGroupDetails();
    },

    fetchGroupDetails : function () {
        if (Object.size(this.groupDetailsCache) <= 0) {
            //Fetch all group and conversation details
            groupService.getGroupConversations().then(function (groupConversations) {
                groupConversations.forEach(function (conversation) {
                    var id = conversation.group.id;
                    this.groupDetailsCache[id] = conversation;

                    // Fetch each group members
                    groupService.getMembers(id).then(function (members) {
                        this.groupDetailsCache[id].members = members;
                        this.createGroupNodeMembers(id, members);
                    }.bind(this)).catch(function (e) {
                        exceptionController.handleError(e);
                    });

                }.bind(this));

                // Create group list
                this.createGroupList();
            }.bind(this)).catch(function (e) {
                exceptionController.handleError(e);
            });
        }
    },

    fetchAndLoadGroupChat : function (groupId) {
        groupService.getGroupConversations().then(function (groupConversations) {
            groupConversations.forEach(function (conversation) {
                var id = conversation.group.id;
                this.groupDetailsCache[id] = conversation;

                // Fetch each group members
                groupService.getMembers(id).then(function (members) {
                    this.groupDetailsCache[id].members = members;
                    this.createGroupNodeMembers(id, members);
                }.bind(this)).catch(function (e) {
                    exceptionController.handleError(e);
                });
            }.bind(this));

            contactController.loadChatPage(this.groupDetailsCache[groupId].group, false, true);
            navigationController.hideSplashScreen();
            contactController.init();

            this.createGroupList();
        }.bind(this)).catch(function (e) {
            exceptionController.handleError(e);
        });
    },

    getGroupDetails: function () {
        if (Object.size(this.groupDetailsCache) <= 0)
            return false;
        else
            return this.groupDetailsCache;
    },

    createGroupList : function () {
        var frag = $(document.createDocumentFragment());
        if(Object.size(this.groupDetailsCache) > 0) {
            for(var g in this.groupDetailsCache) {
                if (this.groupDetailsCache.hasOwnProperty(g)) {
                    frag.append(this.createGroupNode(this.groupDetailsCache[g]));
                }
            }
        }
        else {
            frag.append("No groups yet");
        }

        $("#groupList").html(frag);
    },

    getGroupConversations : function () {
        if (Object.size(this.groupDetailsCache) <= 0)
            return false;
        else
            return this.groupDetailsCache;
    },

    getGroup : function (id) {
        if (id in this.groupDetailsCache)
            return this.groupDetailsCache[id].group;
        else
            return false;
    },

    getGroupMembers : function (id) {
        if (id in this.groupDetailsCache)
            return this.groupDetailsCache[id].members;
        else
            return false;
    },

    fetchGroupMessage : function (start, count, id) {
        groupService.getLastMessages(id, start, count).then(function (messagesInfo) {
            var organizedMessages = chatController.organizeGroupMessages(messagesInfo);
            chatController.displayMessage(organizedMessages, id, true);
        }).catch(function (e) {
            exceptionController.handleError(e);
        });
    },

    markGroupConversationAsRead : function (id) {
        groupService.markConversationAsRead(id).then(function () {
            this.groupDetailsCache[id].info.unreadMessageCount = 0;
        }.bind(this)).catch(function (e) {
            exceptionController.handleError(e);
        })
    },

    insertContactList : function () {
        var conversations = contactController.conversations;
        var frag = $(document.createDocumentFragment());

        conversations.forEach(function (conversation) {
            frag.append(this.createContactNode(conversation.contact));
        }.bind(this));

        $("#newGroupContactList").html(frag);
    },

    createContactNode : function (contact) {
        return $("<li><label class='label-checkbox item-content'>" +
            "<input class='new-group-contact' type='checkbox' name='" + contact.name + "' value='" + contact.id + "'>" +
            "<div class='item-media'><i class='icon icon-form-checkbox'></i></div> " +
            "<div class='item-inner'><div class='item-title'>" + contact.name + "</div></div>" +
            "</label></li>");
    },

    createGroupNode : function (group) {
        var node = $("<div id='groupNode_" + group.group.id + "' class='group-node col-50 close-popup'>" +
                "<div class='group-details'>" +
                    "<div class='avatar'>" + group.group.name.substring(0, 3) + "</div>" +
                    "<span style='text-align: center;'>" + group.group.name + "</span>" +
                "</div>" +
                "<div class='group-members'>" +
                "</div>" +
            "</div>");

        node.click(function (e) {
            contactController.loadChatPage(group.group, true, true);
        });

        return node;
    },

    createGroupNodeMembers : function (groupId, members) {
        var node = $("#groupNode_" + groupId);
        if (node.length > 0) {
            var groupMembers = "";
            members.forEach(function (member) {
                groupMembers += member.name + ", ";
            });

            if(groupMembers.length > 0)
                groupMembers = groupMembers.substring(0, groupMembers.length - 2);

            node.find(".group-members").html("<span>" + groupMembers + "</span>");
        }
    },

    addGroupEventListener : function () {
        groupService.addGroupEventListener(function (event) {
            if (event.type == "NEW") {
                console.log(event);
                // to be updated
            }
        }.bind(this));
    },

    createGroup : function () {
        var name = $("#newGroupName").val();

        if (name === undefined || name == '') {
            console.log("name is required");
            return;
        }

        var contacts = [];
        $(".new-group-contact:checked").each(function (index, contact) {
            contacts.push(contactController.getContact($(contact).val()))
        });

        if(contacts.length <= 0) {
            console.log("you must select at least 2 contacts");
            return;
        }

        groupService.createNewGroup(name, contacts).then(function (groupId) {
            groupService.getGroupConversations().then(function (conversations) {
                conversations.forEach(function (conversation) {
                    this.groups[conversation.group.id] = conversation;
                }.bind(this));

                contactController.loadChatPage(this.groups[groupId].group, false, true);

                slychat.addNotification({
                    title: "Group has been created",
                    hold: 3000
                });
            }.bind(this)).catch(function (e) {
                exceptionController.handleError(e);
            });
        }.bind(this)).catch(function (e) {
            exceptionController.handleError(e);
        });
    },

    deleteMessages : function (groupId, messageIds) {
        groupService.deleteMessagesFor(groupId, messageIds).then(function (result) {
            slychat.addNotification({
                title: "Messages have been deleted",
                hold: 3000
            });
            messageIds.forEach(function (id) {
                $("#message_" + id).remove();
            });
        }).catch(function (e) {
            exceptionController.handleError(e);
        })
    },

    deleteAllMessages : function (groupId) {
        groupService.deleteAllMessages(groupId).then(function (result) {
            slychat.addNotification({
                title: "Group conversation has been deleted",
                hold: 3000
            });

            this.groupDetailsCache[groupId].info = {
                lastSpeaker: null,
                lastMessage: null,
                unreadMessageCount: 0,
                lastTimestamp: null
            };

            mainView.router.refreshPage();
        }.bind(this)).catch(function (e) {
            exceptionController.handleError(e);
        })
    },

    showGroupInfo : function (groupId) {
        var group = this.groupDetailsCache[groupId].group;
        var members = this.groupDetailsCache[groupId].members;
        var memberList = "";

        members.forEach(function (member) {
            memberList += "<div class='member'>" +
                "<span>" + member.name + "</span>" +
                "<span>" + member.email + "</span>" +
                "</div>";
        });

        var content = "<div class='group-info'>" +
            "<p class='group-info-title'>Group Name:</p>" +
            "<p class='group-info-details'>" + group.name + "</p>" +
            "</div>" +
            "<div class='group-info'>" +
            "<p class='group-info-title'>Group Id:</p>" +
            "<p class='group-info-details'>" + group.id + "</p>" +
            "</div>" +
            '<div class="group-info">' +
            '<p class="group-info-title">Members:</p>'+
            '<div class="group-info-details"><div class="members">' + memberList + '</div></div>' +
            '</div>';

        openInfoPopup(content);
    },

    updateConversationWithNewMessage : function (groupId, messageInfo) {
        if (messageInfo.sent === true) {
            this.groupDetailsCache[groupId].info = {
                lastSpeaker: null,
                unreadMessageCount: 0,
                lastMessage: messageInfo.message,
                lastTimestamp: messageInfo.timestamp
            };
        }
        else {
            var messages = messageInfo.messages;
            this.groupDetailsCache[groupId].info = {
                lastSpeaker: messageInfo.contact,
                unreadMessageCount: this.groupDetailsCache[groupId].info.unreadMessageCount + messages.length,
                lastMessage: messages[messages.length - 1].message,
                lastTimestamp: messages[messages.length - 1].timestamp
            };
        }
    }
};