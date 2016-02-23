var ContactController = function (model) {
    this.model = model;
    this.model.setController(this);
};

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

        for (var email in conversations) {
            if(conversations.hasOwnProperty(email)) {
                contactList.innerHTML += this.createContactBlock(conversations[email].contact, conversations[email].status);
            }
        }

        this.addEventListener();
    },
    createContactBlock : function (contact, status) {
        var lastMessage;
        var timestamp;
        var availableClass;
        var newMessageClass;
        var newBadge;

        if(status.lastMessage == null){
            lastMessage = "";
            timestamp = ""
        }
        else if(status.lastMessage.message.length > 40){
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

        var contactBlock = "<div class='contact-link " + newMessageClass + "' id='contact" + contact.email + "'><div class='contact'>";
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
            links[i].addEventListener("click", (function(self, email, link){
                return function(e) {
                    e.preventDefault();
                    self.model.setCurrentContact(email);
                    KEYTAP.navigationController.loadPage("chat.html");
                };
            })(this, links[i].id.split("contact")[1], links[i]));
        }
    },
    getCurrentContact : function () {
        return this.model.getCurrentContact();
    },
    getContact : function (email) {
        return this.model.getContact(email);
    },
    addNewContact : function () {
        if(this.model.validateContact("#addContactForm") == true){
            var name = document.getElementById("name").value;
            var phone = document.getElementById("phone").value;
            var email = document.getElementById("email").value;
            var publicKey = document.getElementById("publicKey").value;

            contactService.addNewContact({
                name: name,
                email: email,
                phoneNumber: phone,
                publicKey: publicKey
            }).then(function () {
                this.model.resetContacts();
                KEYTAP.navigationController.loadPage("contacts.html");
            }.bind(this)).catch(function (e) {
                KEYTAP.exceptionController.displayDebugMessage(e);
                console.error('Unable to add contact: ' + e.message);
                $("#error").append("<li>" + e.message + "</li>");
            });
        }
    },
    fillContactInfo : function () {
        var contact = this.model.getCurrentContact();
        $("#name").val(contact.name);
        $("#phone").val(contact.phoneNumber);
        $("#email").val(contact.email);
        $("#publicKey").val(contact.publicKey);
    },
    updateContact : function () {
        if(this.model.validateContact("#updateContactForm") == true){
            var contact = this.model.getCurrentContact();
            contact.name = document.getElementById("name").value;
            contact.phoneNumber = document.getElementById("phone").value;
            contact.email = document.getElementById("email").value;
            contact.publicKey = document.getElementById("publicKey").value;

            contactService.updateContact(contact).then(function () {
                this.model.resetContacts();
                KEYTAP.navigationController.loadPage("contacts.html");
            }.bind(this)).catch(function (e) {
                KEYTAP.exceptionController.displayDebugMessage(e);
                console.error('Unable to add contact: ' + e.message);
                $("#error").append("<li>" + e.message + "</li>");
            });
        }
    },
    newContactEvent : function() {
        $("#newContactBtn").click(function (e) {
            e.preventDefault();
            this.addNewContact();
        }.bind(this));
    },
    updateContactEvent : function () {
        $("#updateContactBtn").click(function (e) {
            e.preventDefault();
            this.updateContact();
        }.bind(this));
    },
    deleteContact : function () {
        contactService.removeContact(this.model.getCurrentContact()).then(function () {
            this.model.resetContacts();
            KEYTAP.navigationController.loadPage("contacts.html");
        }.bind(this)).catch(function (e) {
            console.log(e);
        })
    },
    getConversations : function () {
        return this.model.getConversations();
    }
};