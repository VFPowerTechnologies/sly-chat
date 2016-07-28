var GroupController = function () {
    this.groups = [];
};

GroupController.prototype = {
    init : function () {

    },

    fetchGroupMessage : function (start, count, id) {
        groupService.getLastMessages(id, start, count).then(function (result) {
            console.log(result);
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
    },

    createGroupNode : function (group) {
        var node = $("<div class='group-node col-50 close-popup'><span style='text-align: center;'>" + group.group.name + "</span></div>");

        node.click(function (e) {
            contactController.loadChatPage(group.group, true, true);
        });

        return node;
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