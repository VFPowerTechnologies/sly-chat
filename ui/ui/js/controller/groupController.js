var GroupController = function () {
    this.groups = [];
};

GroupController.prototype = {
    init : function () {

    },

    resetGroups : function () {
        this.groups = [];
        groupService.getGroupConversations().then(function (conversations) {
            conversations.forEach(function (conversation) {
                this.groups[conversation.group.id] = conversation;
                groupController.createGroupList(conversations);
            }.bind(this));
        }.bind(this)).catch(function (e) {
            exceptionController.handleError(e);
        });
    },

    getGroup : function (id) {
        if (id in this.groups)
            return this.groups[id].group;
        else
            return false;
    },

    getGroupMembers : function (id) {
        if (id in this.groups)
            return this.groups[id].members;
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
        groupService.markConversationAsRead(id).catch(function (e) {
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

    createGroupList : function (groups) {
        var frag = $(document.createDocumentFragment());

        if (groups.length > 0) {
            groups.forEach(function (group) {
                frag.append(this.createGroupNode(group));
            }.bind(this));
        }
        else {
            frag.append("No groups yet");
        }

        $("#groupList").html(frag);

        groups.forEach(function (group) {
            groupService.getMembers(group.group.id).then(function (members) {
                groupController.groups[group.group.id].members = members;
                this.createGroupNodeMembers(group.group.id, members);
            }.bind(this)).catch(function (e) {
                exceptionController.handleError(e);
            });
        }.bind(this));
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
                this.resetGroups();
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
            mainView.router.refreshPage();
        }).catch(function (e) {
            exceptionController.handleError(e);
        })
    },

    showGroupInfo : function (groupId) {
        var group = this.groups[groupId].group;
        var members = this.groups[groupId].members;
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
    }
};