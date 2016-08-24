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

    fetchGroup : function (groupId) {
        groupService.getInfo(groupId).then(function (info) {
            if (info !== null) {
                groupService.getMembers(groupId).then(function (members) {
                    var groupDetails = {
                        group: info,
                        members: members,
                        info: {
                            lastSpeaker: null,
                            unreadMessageCount: 0,
                            lastMessage: null,
                            lastTimestamp: null
                        }
                    };
                    this.groupDetailsCache[groupId] = groupDetails;

                    if ($("#groupNode_" + groupId).length <= 0) {
                        var groupNode = this.createGroupNode(groupDetails);
                        if($("#groupList").html() == "No groups yet") {
                            $("#groupList").html(groupNode);
                        }
                        else {
                            $("#groupList").append(groupNode);
                        }
                    }

                    this.createGroupNodeMembers(groupId, members);
                }.bind(this)).catch(function (e) {
                    exceptionController.handleError(e);
                });
            }
        }.bind(this)).catch(function (e) {
            exceptionController.handleError(e);
        });
    },

    fetchMembers : function (groupId) {
        groupService.getMembers(groupId).then(function (members) {
            console.log("fetching member");
            this.groupDetailsCache[groupId].members = members;
        }.bind(this)).catch(function (e) {
            exceptionController.handleError(e);
        })
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

    createLeftGroupList : function () {
        var frag = $(document.createDocumentFragment());
        if(Object.size(this.groupDetailsCache) > 0) {
            for(var g in this.groupDetailsCache) {
                if (this.groupDetailsCache.hasOwnProperty(g)) {
                    frag.append(this.createLeftGroupNode(this.groupDetailsCache[g]));
                }
            }
            $("#leftGroupList").html(frag);
        }
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
        var node = $("<div id='groupNode_" + group.group.id + "' class='group-node col-50 tablet-25 close-popup'>" +
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

        node.on("mouseheld", function () {
            vibrate(50);
            this.openGroupNodeMenu(group.group.id);
        }.bind(this));

        return node;
    },

    createLeftGroupNode : function (group) {
        var newBadge = "";
        if (group.info.unreadMessageCount > 0) {
            newBadge = '<span class="left-menu-new-badge" style="color: red; font-size: 12px; margin-left: 5px;">new</span>';
        }

        var node = $("<li id='leftContact_" + group.group.id + "'><a href='#'>" + group.group.name + "</a>" + newBadge + "</li>");

        node.click(function (e) {
            contactController.loadChatPage(group.group, true, true);
        });

        node.on("mouseheld", function () {
            vibrate(50);
            this.openGroupNodeMenu(group.group.id);
        }.bind(this));

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
            switch(event.type) {
                case "NEW":
                    this.newGroupCreatedEvent(event);
                    break;

                case "PARTED":
                    if(this.groupDetailsCache[event.groupId] !== undefined) {
                        groupService.getMembers(event.groupId).then(function (members) {
                            this.groupDetailsCache[event.groupId].members = members;
                            this.createGroupNodeMembers(event.groupId, members);
                        }.bind(this)).catch(function (e) {
                            exceptionController.handleError(e);
                        });
                    }
                    break;

                case "JOINED":
                    if(this.groupDetailsCache[event.groupId] !== undefined) {
                        groupService.getMembers(event.groupId).then(function (members) {
                            this.groupDetailsCache[event.groupId].members = members;
                            this.createGroupNodeMembers(event.groupId, members);
                        }.bind(this)).catch(function (e) {
                            exceptionController.handleError(e);
                        });
                    }
                    break;
            }

        }.bind(this));
    },

    newGroupCreatedEvent : function (event) {
        if(this.groupDetailsCache[event.groupId] === undefined) {
            this.fetchGroup(event.groupId);
        }
    },

    createGroup : function () {
        var name = $("#newGroupName").val();

        if (name === undefined || name == '') {
            console.log("name is required");
            return;
        }

        var contacts = [];
        $(".new-group-contact:checked").each(function (index, contact) {
            contacts.push(contactController.getContact($(contact).val()));
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

    openInviteUsersModal : function (groupId) {
        var contactList = "";

        var members = [];

        this.getGroupMembers(groupId).forEach(function (member) {
            members[member.id] = member;
        });

        var conversations = contactController.conversations;
        conversations.forEach(function (conversation) {
            var contact = conversation.contact;
            if(!(contact.id in members)) {
                contactList += "<li><label class='label-checkbox item-content'>" +
                    "<input class='new-group-contact' type='checkbox' name='" + contact.name + "' value='" + contact.id + "'>" +
                    "<div class='item-media'><i class='icon icon-form-checkbox'></i></div> " +
                    "<div class='item-inner'><div class='item-title'>" + contact.name + "</div></div>" +
                    "</label></li>";
            }
        });


        var content = "<div><a id='submitInviteContactButton' href='#' class='button button-big button-raised button-fill'>Invite</a>" +
            "<div class='list-block'>" +
            "<div class='content-block-title'>Contacts:</div>" +
            "<ul id='inviteContactList'>" + contactList + "</ul>" +
            "<input id='inviteContactGroupId' type='hidden' value='" + groupId + "'>" +
            "</div>";

        var navbar = '' +
            '<div class="navbar top-navbar">' +
            '<div class="navbar-inner">' +
            '<a href="#" class="link close-popup close-popup-btn icon-only"> <i class="icon icon-back" style="margin-left: 10px;"></i></a> Invite Contacts' +
            '</div>' +
            '</div>';

        var popupHTML = '' +
            '<div class="popup info-popup tablet-fullscreen">' +
            '<div class="view navbar-fixed" data-page>' +
            '<div class="pages">' +
            '<div data-page class="page">' +
            navbar +
            '<div class="page-content">'+
            '<div class="content-block">' +
            content +
            '</div>' +
            '</div>' +
            '</div>' +
            '</div>' +
            '</div>' +
            '</div>';

        slychat.popup(popupHTML);
    },

    inviteUsersToGroup : function (groupId, userIds) {
        groupService.inviteUsers(groupId, userIds).then(function () {
            slychat.closeModal();
            slychat.addNotification({
                title: "Contacts have been invited",
                hold: 3000
            });
            this.fetchMembers(groupId);
        }.bind(this)).catch(function (e) {
            exceptionController.handleError(e);
        })
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

    deleteGroupFromCache : function (groupId) {
        delete this.groupDetailsCache[groupId];
        $("#groupNode_" + groupId).remove();
        $("recentChat_" + conversation.group.id).remove();
    },

    leaveGroup : function (groupId) {
        groupService.part(groupId).then(function (success) {
            if(success === true) {
                slychat.addNotification({
                    title: "You left the group successfully",
                    hold: 3000
                });
                this.deleteGroupFromCache(groupId);
            }
            else {
                slychat.addNotification({
                    title: "Could not leave the group",
                    hold: 3000
                });
            }
        }.bind(this)).catch(function (e) {
            exceptionController.handleError(e);
        });
    },

    blockGroup : function (groupId) {
        groupService.block(groupId).then(function () {
            this.deleteGroupFromCache(groupId);
            slychat.addNotification({
                title: "Group has been blocked successfully",
                hold: 3000
            });
        }.bind(this)).catch(function (e) {
            exceptionController.handleError(e);
        });
    },

    unblockGroup : function (groupId) {
        groupService.unblock(groupId).then(function () {
            this.fetchGroup(groupId);
            slychat.addNotification({
                title: "Group has been unblocked successfully",
                hold: 3000
            });
        }.bind(this)).catch(function (e) {
            exceptionController.handleError(e);
        });
    },

    openGroupNodeMenu : function (groupId) {
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
                    this.openInviteUsersModal(groupId);
                }.bind(this)
            },
            {
                text: 'Leave Group',
                onClick: function () {
                    slychat.confirm("Are you sure you want to leave the group?", function () {
                        this.leaveGroup(groupId);
                    }.bind(this));
                }.bind(this)
            },
            {
                text: 'Block Group',
                onClick: function () {
                    slychat.confirm("Are you sure you want to block this group? </br> You won't receive any more messages.", function () {
                        this.blockGroup(groupId);
                    }.bind(this));
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

        openInfoPopup(content, "Group Info");
    },

    updateConversationWithNewMessage : function (groupId, messageInfo) {
        var lastMessageInfo;
        if (messageInfo.sent === true) {
            lastMessageInfo = {
                lastSpeaker: null,
                unreadMessageCount: 0,
                lastMessage: messageInfo.message,
                lastTimestamp: messageInfo.timestamp
            };
        }
        else {
            var messages = messageInfo.messages;
            var unreadCount;
            if (this.groupDetailsCache[groupId] !== undefined && this.groupDetailsCache[groupId].info !== undefined) {
                unreadCount = this.groupDetailsCache[groupId].info.unreadMessageCount + messages.length;
            }
            lastMessageInfo = {
                lastSpeaker: messageInfo.contact,
                unreadMessageCount: unreadCount,
                lastMessage: messages[messages.length - 1].message,
                lastTimestamp: messages[messages.length - 1].timestamp
            };
        }

        if (this.groupDetailsCache[groupId] !== undefined) {
            this.groupDetailsCache[groupId].info = lastMessageInfo;
        }
        else {
            groupService.getInfo(groupId).then(function (info) {
                this.groupDetailsCache[groupId] = {
                    group: info,
                    info: lastMessageInfo
                }
            }.bind(this)).catch(function (e) {
                exceptionController.handleError(e);
            });
        }
    },

    clearCache : function () {
        this.groups = [];
        this.groupDetailsCache = [];
    }
};