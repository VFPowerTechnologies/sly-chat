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
        var contactList = $("#contactContent").contents().find("#contactList");
        contactList.html("");

        var i = 0;
        for (var email in conversations) {
            if(conversations.hasOwnProperty(email)) {
                contactList.append(this.createContactBlock(conversations[email].contact, conversations[email].status, i));
            }
            i++;
        }

        this.addEventListener();
    },
    createContactBlock : function (contact, status, index) {
        var contactLinkClass = "contact-link ";
        var newBadge = "";

        if(status.unreadMessageCount > 0){
            contactLinkClass += "new-messages";
            newBadge = "<span class='pull-right label label-warning'>" + "new" + "</span>";
        }

        if(index == 0)
            contactLinkClass += " first-contact";

        var contactBlock = "<div class='" + contactLinkClass + "' id='contact%" + contact.email + "'><div class='contact'>";
        contactBlock += this.createAvatar(contact.name);
        contactBlock += "<p>" + contact.name + "</p>";
        contactBlock += "</div>" + newBadge + "</div>";

        return contactBlock;
    },
    createAvatar : function (name) {
        var img = new Image();
        img.setAttribute('data-name', name);
        img.setAttribute('class', 'avatarCircle');

        $(img).initial({
            textColor: '#fff',
            seed: 0
        });

        return img.outerHTML;
    },
    addEventListener : function () {
        var links = $("#contactContent").contents().find(".contact-link");

        links.bind("click", function (e) {
            e.preventDefault();
            var email = $(this).attr("id").split("contact%")[1];
            KEYTAP.contactController.model.setCurrentContact(email);
            KEYTAP.navigationController.loadPage("chat.html");
        });
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