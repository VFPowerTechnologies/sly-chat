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
    setCurrentContact : function (email) {
        this.model.setCurrentContact(email);
    },
    getContact : function (email) {
        return this.model.getContact(email);
    },
    addNewContact : function () {
        $("#newContactBtn").prop("disabled", true);
        if(this.model.validateContact("#addContactForm") == true){
            var input = document.getElementById("username").value;
            var phone = null;
            var username = null;
            if (validateEmail(input)){
                username = input;
            }
            else{
                phone = input;
            }

            contactService.fetchNewContactInfo(username, phone).then(function (response) {
                if(response.successful == false){
                    $("#error").append("<li>" + response.errorMessage + "</li>");
                    $("#newContactBtn").prop("disabled", false);
                }
                else{
                    this.createConfirmContactForm(response.contactDetails);
                }
            }.bind(this)).catch(function (e) {
                KEYTAP.exceptionController.displayDebugMessage(e);
                console.error('Unable to add contact: ' + e.message);
                $("#newContactBtn").prop("disabled", false);
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
            $("#error").html("");
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
    },
    createConfirmContactForm : function (contactDetails) {
        var form = document.createElement("form");
        form.id = "addContactForm";
        form.method = "post";

        var nameLabel = document.createElement("label");
        nameLabel.for = "name";
        nameLabel.innerHTML = "Name";

        var nameInput = document.createElement("INPUT");
        nameInput.id = "name";
        nameInput.type = "text";
        nameInput.value = contactDetails.name;
        nameInput.className = "center-align";
        nameInput.readOnly = true;

        var publicKeyLabel = document.createElement("label");
        publicKeyLabel.for = "publicKey";
        publicKeyLabel.innerHTML = "Public Key";

        var publicKeyInput = document.createElement("INPUT");
        publicKeyInput.id = "publicKey";
        publicKeyInput.type = "text";
        publicKeyInput.value = contactDetails.publicKey;
        publicKeyInput.className = "center-align";
        publicKeyInput.readOnly = true;

        var phoneInput = document.createElement("INPUT");
        phoneInput.id = "phoneNumber";
        phoneInput.type = "hidden";
        phoneInput.value = contactDetails.phoneNumber;

        var emailInput = document.createElement("INPUT");
        emailInput.id = "email";
        emailInput.type = "hidden";
        emailInput.value = contactDetails.email

        var navbar = document.createElement("div");
        navbar.className = "navbar-btn center-align";

        var cancelBtn = document.createElement("button");
        cancelBtn.className = "btn-sm red";
        cancelBtn.id = "cancelBtn";
        cancelBtn.type = "submit";
        cancelBtn.innerHTML = "Cancel";
        cancelBtn.style.border = "none";
        cancelBtn.style.color = "white";
        cancelBtn.style.marginRight = "5px";

        var confirmBtn = document.createElement("button");
        confirmBtn.className = "btn-sm primary-color";
        confirmBtn.id = "confirmBtn";
        confirmBtn.type = "submit";
        confirmBtn.innerHTML = "Confirm";
        confirmBtn.style.border = "none";
        confirmBtn.style.color = "white";

        form.appendChild(nameLabel);
        form.appendChild(nameInput);
        form.appendChild(publicKeyLabel);
        form.appendChild(publicKeyInput);
        form.appendChild(emailInput);
        form.appendChild(phoneInput);

        navbar.appendChild(cancelBtn);
        navbar.appendChild(confirmBtn);

        form.appendChild(navbar);

        $("#addContactForm").remove();
        document.getElementById("contactFormContainer").appendChild(form);

        $("#confirmBtn").on("click", function (e) {
            e.preventDefault();
            contactService.addNewContact({
                "name" : $("#name").val(),
                "phoneNumber" : $("#phoneNumber").val(),
                "email" : $("#email").val(),
                "publicKey" : $("#publicKey").val()

            }).then(function () {
                this.model.resetContacts();
                KEYTAP.navigationController.loadPage("contacts.html");
            }.bind(this)).catch(function (e) {
                $("#newContactBtn").prop("disabled", false);
                KEYTAP.exceptionController.displayDebugMessage(e);
                console.error('Unable to add contact: ' + e.message);
                $("#error").append("<li>" + e.message + "</li>");
            });
        }.bind(this));

        $("#cancelBtn").on("click", function (e) {
            e.preventDefault();
            KEYTAP.navigationController.loadPage("addContact.html");
        });
    }
};