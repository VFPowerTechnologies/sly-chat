var GroupController = function () {
    this.groups = [];
    this.groupDetailsCache = [];
    this.lastGroupId = null;
    this.events = [];
    this.eventsSize = this.events.length;
};

GroupController.prototype = {
    init : function () {
        this.fetchGroupDetails();
    },

    refreshCache : function () {
        groupController.fetchGroupDetails();
    },

    fetchGroupDetails : function () {
        if (Object.size(this.groupDetailsCache) <= 0) {
            groupService.getGroupConversations().then(function (groupConversations) {
                this.cacheGroupDetails(groupConversations);
                this.createGroupList();
                contactController.createContactHtml();
            }.bind(this)).catch(function (e) {
                exceptionController.handleError(e);
            });
        }
        else {
            this.createGroupList();
            contactController.createContactHtml();
        }
    },

    cacheGroupDetails : function (groupConversations) {
        groupConversations.forEach(function (conversation) {
            var groupDetails = new GroupDetails(conversation);
            var id = groupDetails.group.id;
            this.groupDetailsCache[id] = groupDetails;

            this.fetchMembers(id);
        }.bind(this));
    },

    fetchAndLoadGroupChat : function (groupId) {
        groupService.getInfo(groupId).then(function (group) {
            if (group !== null) {
                var g = new Group(group);
                var isGroup = true;
                contactController.loadChatPage(g, false, isGroup);
                uiController.hideSplashScreen();
            }
            else {
                navigationController.loadPage("contacts.html", false);
            }

            this.refreshCache();
            contactController.refreshCache();
        }.bind(this)).catch(function (e) {
            exceptionController.handleError(e);
        });
    },

    fetchGroup : function (groupId) {
        groupService.getInfo(groupId).then(function (info) {
            if (info !== null) {
                groupService.getMembers(groupId).then(function (members) {
                    var groupDetails = new GroupDetails({
                        group: info,
                        members: members,
                        info: {
                            lastSpeaker: null,
                            unreadMessageCount: 0,
                            lastMessage: null,
                            lastTimestamp: null
                        }
                    });
                    this.groupDetailsCache[groupId] = groupDetails;

                    if ($("#groupNode_" + groupId).length <= 0) {
                        if($("#groupList").html() == "No groups yet") {
                            $("#groupList").html(this.createGroupNode(groupDetails));
                        }
                        else {
                            $("#groupList").append(this.createGroupNode(groupDetails));
                        }
                    }

                    $("#leftGroupList").append(this.createLeftGroupNode(groupDetails));

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
            this.groupDetailsCache[groupId].setMembers(members);
            this.createGroupNodeMembers(groupId, members);
        }.bind(this)).catch(function (e) {
            exceptionController.handleError(e);
        })
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
        }
        else {
            frag.append($("<div style='text-align: center; color: #fff;'><a href='#' onclick='navigationController.loadPage(\"createGroup.html\", true)'>Create a group</a></div>"));
        }

        $("#leftGroupList").html(frag);
    },

    getGroupConversations : function () {
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
            chatController.displayMessage(organizedMessages, id);
        }).catch(function (e) {
            exceptionController.handleError(e);
        });
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

    createGroupNode : function (groupDetails) {
        var node = $("<div id='groupNode_" + groupDetails.group.id + "' class='group-node col-50 tablet-25 close-popup'>" +
                "<div class='group-details'>" +
                    "<div class='avatar'>" + groupDetails.group.name.substring(0, 3) + "</div>" +
                    "<span style='text-align: center;'>" + groupDetails.group.name + "</span>" +
                "</div>" +
                "<div class='group-members'>" +
                "</div>" +
            "</div>");

        if (groupDetails.members.length > 0) {
            node.find('.group-members').html(this.createGroupMembers(groupDetails.members));
        }

        node.click(function (e) {
            contactController.loadChatPage(groupDetails.group, true, true);
        });

        node.on("mouseheld", function () {
            vibrate(50);
            this.openGroupNodeMenu(groupDetails.group.id);
        }.bind(this));

        return node;
    },

    createLeftGroupNode : function (groupDetails) {
        var newBadge = "";
        if (groupDetails.groupInfo.unreadMessageCount > 0) {
            newBadge = '<span class="left-menu-new-badge" style="color: red; font-size: 12px; margin-left: 5px;">new</span>';
        }

        var node = $("<li id='leftContact_" + groupDetails.group.id + "'><a class='left-contact-link' href='#'>" + groupDetails.group.name + "</a>" + newBadge + "</li>");

        node.find('.left-contact-link').click(function (e) {
            contactController.loadChatPage(groupDetails.group, true, true);
        });

        node.find('.left-contact-link').on("mouseheld", function () {
            vibrate(50);
            this.openGroupNodeMenu(groupDetails.group.id);
        }.bind(this));

        return node;
    },

    createGroupMembers : function (members) {
        var groupMembers = "";
        members.forEach(function (member) {
            groupMembers += member.name + ", ";
        });

        if(groupMembers.length > 0)
            groupMembers = groupMembers.substring(0, groupMembers.length - 2);

        return "<span>" + groupMembers + "</span>";
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
        groupService.addGroupEventListener(this.handleGroupEvents.bind(this));
    },

    handleGroupEvents : function (event) {
        this.events.push(event);
        this.eventsSize = this.events.length;

        setTimeout(function () {
            // if there was no more group event in the last second run all event
            if (this.events.length == this.eventsSize) {
                this.events = this.events.filter(function (event) {
                    switch(event.type) {
                        case "PARTED":
                            this.handlePartedEvent(event.groupId);
                            break;

                        case "JOINED":
                            this.handleJoinedEvent(event.groupInfo, event.members);
                            break;

                        case "MEMBERSHIP":
                            this.handleMembershipEvent(event.groupId, event.newMembers, event.partedMembers);
                            break;

                        case "BLOCKED":
                            this.handleBlockedGroup(event.groupId);
                            break;
                    }
                }.bind(this));
                this.eventsSize = this.events.length;
                this.refreshCache();
            }
        }.bind(this), 500);
    },

    handleMembershipEvent : function (groupId, newMembers, partedMembers) {
        if (this.groupDetailsCache[groupId] !== undefined) {
            newMembers.forEach(function (memberId) {
                this.groupDetailsCache[groupId].addMemberFromId(memberId);
            }.bind(this));

            partedMembers.forEach(function (memberId) {
                this.groupDetailsCache[groupId].removeMember(memberId);
            }.bind(this));
        }
    },

    handleJoinedEvent : function (groupInfo, memberIds) {
        var groupDetails = new GroupDetails({
            group: groupInfo,
            info: {
                lastSpeaker: null,
                unreadMessageCount: 0,
                lastMessage: null,
                lastTimestamp: null
            }
        });

        memberIds.forEach(function (memberId) {
            groupDetails.addMemberFromId(memberId);
        });

        this.groupDetailsCache[groupInfo.id] = groupDetails;
    },

    handlePartedEvent : function (groupId) {
        delete this.groupDetailsCache[groupId];
    },

    handleBlockedGroup : function (groupId) {
        delete this.groupDetailsCache[groupId];
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

        var conversations = contactController.conversations;
        conversations.forEach(function (conversation) {
            var contact = conversation.contact;
            contactList += "<li><label class='label-checkbox item-content'>" +
                "<input class='new-group-contact' type='checkbox' name='" + contact.name + "' value='" + contact.id + "'>" +
                "<div class='item-media'><i class='icon icon-form-checkbox'></i></div> " +
                "<div class='item-inner'><div class='item-title'>" + contact.name + "</div></div>" +
                "</label></li>";
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

            this.groupDetailsCache[groupId].resetGroupInfo();

            if (chatController.getCurrentContactId() == groupId)
                navigationController.loadPage("contacts.html", false);
            else
                mainView.router.refreshPage();
        }.bind(this)).catch(function (e) {
            exceptionController.handleError(e);
        })
    },

    deleteGroupFromCache : function (groupId) {
        delete this.groupDetailsCache[groupId];
        $("#groupNode_" + groupId).remove();
        $("#recentChat_" + groupId).remove();
        $("#leftContact_" + groupId).remove();
    },

    leaveGroup : function (groupId) {
        groupService.part(groupId).then(function (success) {
            if(success === true) {
                slychat.addNotification({
                    title: "You left the group successfully",
                    hold: 3000
                });
                this.deleteGroupFromCache(groupId);
                if (chatController.getCurrentContactId() == groupId)
                    navigationController.loadPage("contacts.html", false);
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
            if (chatController.getCurrentContactId() == groupId)
                navigationController.loadPage("contacts.html", false);
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

    createGroupInfoMemberList : function (members) {
        var memberNode = $("#groupInfoMemberList");

        if (memberNode.length <= 0)
            return;

        var frag = $(document.createDocumentFragment());

        members.forEach(function (member) {
            var contact = contactController.getContact(member.id);
            frag.append(this.createGroupInfoMemberNode(contact));
        }.bind(this));

        memberNode.html(frag);
    },

    createGroupInfoMemberNode : function (member) {
        var blocked = '';
        if (member.allowedMessageLevel == "BLOCKED")
            blocked = "<span class='member-blocked'>(blocked)</span>";

        var node = $("<li id='member_" + member.id + "'><a href='#' class='link item-content'><div class='item-inner' style='display: block;'>" +
            "<div class='member-name'>" + member.name + blocked + "</div>" +
            "<div class='group-info-member-hidden'>" + member.email + "</div>" +
            "</div></a></li>");

        node.click(function(e) {
            e.preventDefault();
            node.find(".group-info-member-hidden").toggle();
        });

        node.on('mouseheld', function(e) {
            vibrate(50);
            this.openGroupMemberMenu(member);
        }.bind(this));

        return node;
    },

    loadGroupInfo : function (groupId) {
        var options = {
            url: "groupInfo.html",
            query: {
                groupId : groupId
            }
        };

        navigationController.loadPage('groupInfo.html', true, options);
        slychat.closeModal();
        this.lastGroupId = groupId;
    },

    openGroupMemberMenu : function (member) {
        var block;
        if (member.allowedMessageLevel == "BLOCKED") {
            block = {
                    text: 'Unblock',
                    onClick: function () {
                        slychat.confirm("Are you sure you want to unblock " + member.name + "?", function () {
                            contactService.unblock(member.id).then(function () {
                                contactController.contacts[member.id].allowedMessageLevel = "GROUP_ONLY";
                                this.createGroupInfoMemberList(this.getGroupMembers($("#groupIdHidden").html()));
                                slychat.addNotification({
                                    title: "Contact has been unblocked",
                                    hold: 2000
                                });
                            }.bind(this)).catch(function (e) {
                                slychat.addNotification({
                                    title: "An error occured",
                                    hold: 2000
                                });
                                exceptionController.handleError(e);
                            });
                        }.bind(this));
                    }.bind(this)
                };
        }
        else {
            block = {
                text: 'Block',
                onClick: function () {
                    slychat.confirm("Are you sure you want to block " + member.name + "?", function () {
                        contactService.block(member.id).then(function () {
                            contactController.contacts[member.id].allowedMessageLevel = "BLOCKED";
                            this.createGroupInfoMemberList(this.getGroupMembers($("#groupIdHidden").html()));
                            slychat.addNotification({
                                title: "Contact has been blocked",
                                hold: 2000
                            });
                        }.bind(this)).catch(function (e) {
                            slychat.addNotification({
                                title: "An error occured",
                                hold: 2000
                            });
                            exceptionController.handleError(e);
                        });
                    }.bind(this));
                }.bind(this)
            };
        }
        var buttons = [
            {
                text: 'Contact info',
                onClick: function () {
                    contactController.loadContactInfo(contactController.getContact(member.id));
                }.bind(this)
            },
            block,
            {
                text: 'Cancel',
                color: 'red',
                onClick: function () {
                }
            }
        ];

        slychat.actions(buttons);
    },

    openGroupNodeMenu : function (groupId) {
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

    updateConversationInfo : function (info) {
        if (this.groupDetailsCache[info.groupId] !== undefined && this.groupDetailsCache[info.groupId].groupInfo !== undefined) {
            if (info.lastMessageData == null) {
                this.groupDetailsCache[info.groupId].resetGroupInfo();
            }
            else {
                this.groupDetailsCache[info.groupId].setGroupInfo({
                    lastSpeaker: info.lastMessageData.speakerId,
                    unreadMessageCount: info.unreadCount,
                    lastMessage: info.lastMessageData.message,
                    lastTimestamp: info.lastMessageData.timestamp
                });
            }
        }
    },

    clearCache : function () {
        this.groups = [];
        this.groupDetailsCache = [];
    }
};
