var GroupController = function () {
    this.groups = [];
};

GroupController.prototype = {
    init : function () {

    },

    getGroup : function (id) {
        if (id in this.groups)
            return this.groups[id].group;
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
            console.log(event);
        });
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

        groupService.createNewGroup(name, contacts).then(function () {
            // go to the new group chat page.
            // just going back for now.
            navigationController.goBack();
            slychat.addNotification({
                title: "Group has been created",
                hold: 3000
            });
        }).catch(function (e) {
            exceptionController.handleError(e);
        });
    }
};