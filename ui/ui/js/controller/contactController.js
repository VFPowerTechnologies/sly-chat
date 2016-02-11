var ContactController = function (model) {
    this.model = model;
    this.model.setController(this);
}

ContactController.prototype = {
    init : function () {
        var conversations = this.model.getConversations();
        if(conversations.length <= 0){
            this.model.fetchConversation();
        }
        else{
            this.displayContacts(conversations);
        }

    },
    displayContacts : function (conversations) {
        contactList = document.getElementById("contactList");
        contactList.innerHTML = "";

        var index;
        for (index = 0; index < conversations.length; index++) {
            contactList.innerHTML += this.createContactBlock(conversations[index].contact, conversations[index].status);
        }
        this.addEventListener();
    },
    createContactBlock : function (contact, status) {
        var lastMessage, timestamp, availableClass, newMessageClass, newBadge;
        if(status.lastMessage == null){
            lastMessage = "";
            timestamp = ""
        }
        else if(lastMessage.message.length > 40){
            lastMessage = status.lastmessage.message.substring(0, 40) + "...";
            timestamp = status.lastMessage.timestamp;
        }
        else{
            lastMessage = status.lastMessage.message;
            timestamp = status.lastMessage.timestamp;
        }

        if(status.online == true){
            availableClass = "dot green";
        }
        else{
            availableClass = "dot red";
        }

        if(status.unreadMessageCount > 0){
            newMessageClass = "new-messages";
            newBadge = "<span class='pull-right label label-warning'>" + "new" + "</span>";
        }
        else{
            newMessageClass = "";
            newBadge = "";
        }

        var contactBlock = "<div class='contact-link " + newMessageClass + "' id='contact_" + contact.id + "'><div class='contact'>";
        contactBlock += this.createAvatar(contact.name);
        contactBlock += "<span class='" + availableClass + "'></span>";
        contactBlock += "<p>" + contact.name + "</p>";
        contactBlock += "<span class='last_message'>" + lastMessage + "</span>";
        contactBlock += "<span class='time'>" + timestamp + "</span>";
        contactBlock += "</div>" + newBadge + "</div>";

        return contactBlock;
    },
    createAvatar : function (name) {
        var img = new Image();
        img.setAttribute('data-name', name);
        img.setAttribute('class', 'avatarCircle');

        $(img).initial({
            textColor: '#000000',
            seed: 0
        });

        return img.outerHTML;
    },
    addEventListener : function () {
        var links = document.getElementsByClassName("contact-link");
        for(var i = 0; i < links.length; i++){
            links[i].addEventListener("click", (function(self, id, link){
                return function(e) {
                    e.preventDefault();
                    self.model.setCurrentContact(id);
                    loadPage("chat.html");
                };
            })(this, links[i].id.split("_")[1], links[i]));
        }
    },
    getCurrentContact : function () {
        return this.model.getCurrentContact();
    },
    getContact : function (id) {
        return this.model.getContact(id);
    },
    addNewContact : function () {
        if(this.model.validateNewContact() == true){
            var name = document.getElementById("name").value;
            var phone = document.getElementById("phone").value;
            var email = document.getElementById("email").value;

            contactService.addNewContact({
                name: name,
                email: email,
                phoneNumber: phone
            }).then(function () {
                this.model.resetContacts();
                loadPage("contacts.html");
            }.bind(this)).catch(function (e) {
                console.error('Unable to add contact: ' + e);
            });
        }
    },
    newContactEvent : function() {
        $("#newContactBtn").click(function (e) {
            e.preventDefault();
            this.addNewContact();
        }.bind(this));
    }
}