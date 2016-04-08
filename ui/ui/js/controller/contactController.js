var ContactController = function (model) {
    this.model = model;
    this.model.setController(this);
    this.syncing = false;
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
        var contactList = $("#contactList");
        var fragment = $(document.createDocumentFragment());

        var i = 0;
        for (var email in conversations) {
            if(conversations.hasOwnProperty(email)) {
                fragment.append(this.createContactBlock(conversations[email].contact, conversations[email].status, i));
            }
            i++;
        }

        contactList.html(fragment);

        this.addEventListener();
    },
    addContactListSyncListener : function () {
        contactService.addContactListSyncListener(function (sync) {
            this.syncing = sync;
            if(sync == false && window.location.href.indexOf("contacts.html") > -1){
                this.model.resetContacts();
                this.model.fetchConversation();
            }
        }.bind(this));
    },
    createContactBlock : function (contact, status, index) {
        var contactLinkClass = "contact-link ";
        var newBadge = "";

        if(status.unreadMessageCount > 0){
            contactLinkClass += "new-messages";
            newBadge = "<span class='pull-right label label-warning' style='line-height: 0.8'>" + "new" + "</span>";
        }

        if(index == 0)
            contactLinkClass += " first-contact";

        var contactBlock = "<div class='" + contactLinkClass + "' id='contact%" + contact.email + "'><div class='contact'>";
        contactBlock += createAvatar(contact.name);
        contactBlock += "<p style='display: inline-block;'>" + contact.name + "</p>";
        contactBlock += this.createContactDropDown(contact.email);
        contactBlock += "</div>" + newBadge + "</div>";

        return contactBlock;
    },
    createContactDropDown: function (email) {
        var dropDown = '<div class="dropdown pull-right">';
        dropDown += '<a class="dropdown-toggle contact-dropDown" data-toggle="dropdown" aria-haspopup="true" aria-expanded="true" href="#" style="text-decoration: none;">';
        dropDown += '&nbsp;<i class="fa fa-ellipsis-v"></i>&nbsp;</a>';
        dropDown += '<ul class="contact-dropdown-menu dropdown-menu dropdown-menu-right" aria-labelledby="dropdownMenu">';
        dropDown += '<li style="text-align: center;"><a href="#" id="deleteContact_' + email + '">Delete Contact</a></li>';
        dropDown += '</ul></div>';

        return dropDown;
    },
    loadContactPage : function (email, pushCurrentPage) {
        this.model.fetchConversationForChat(email, pushCurrentPage);
    },
    addEventListener : function () {
        var links = $(".contact-link");

        links.bind("click", function (e) {
            e.preventDefault();
            var email = $(this).attr("id").split("contact%")[1];
            KEYTAP.contactController.loadContactPage(email, true);
        });

        $(".contact-dropDown").bind("click", function(e) {
            $(".contact-dropdown-menu").hide();
            $(this).next(".dropdown-menu").toggle();
            e.stopPropagation();
        });

        $("html body").click(function(){
            $(".contact-dropdown-menu").hide();
        });

        $("[id^='deleteContact_']").bind("click", function(e){
            e.stopPropagation();
            e.preventDefault();
            $(".contact-dropdown-menu").hide();
            if(KEYTAP.connectionController.networkAvailable == true && this.syncing == false) {
                var email = e.currentTarget.id.split("_")[1];
                this.displayDeleteContactModal(email);
            }
        }.bind(this));
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
        var newContactBtn = $("#newContactBtn");
        newContactBtn.prop("disabled", true);
        if(this.model.validateContact("#addContactForm") == true && this.syncing == false){
            var input = $("#username").val().replace(/\s+/g, '');
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
                $("#error").html("<li>" + e.message + "</li>");
            });
        }
        else{
            newContactBtn.prop("disabled", false);
        }
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
    deleteContact : function (email) {
        contactService.removeContact(this.model.getContact(email)).then(function () {
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
        nameLabel.innerHTML = "Name:";

        var nameInput = document.createElement("INPUT");
        nameInput.id = "name";
        nameInput.type = "text";
        nameInput.value = contactDetails.name;
        nameInput.className = "center-align";
        nameInput.readOnly = true;

        var publicKeyLabel = document.createElement("label");
        publicKeyLabel.for = "publicKey";
        publicKeyLabel.innerHTML = "Public Key:";

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
        emailInput.value = contactDetails.email;

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
        confirmBtn.className = "btn-sm secondary-color";
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
                $("#error").html("<li>" + e.message + "</li>");
            });
        }.bind(this));

        $("#cancelBtn").on("click", function (e) {
            e.preventDefault();
            KEYTAP.navigationController.loadPage("addContact.html", false);
        });
    },
    displayDeleteContactModal: function(email) {
        var modalHtml = '<div id="deleteContactModal" class="modal">';
        modalHtml += '<div style="border-bottom: 1px solid black;"><h6 style="margin-left: 5px;">Please confirm</h6></div>';
        modalHtml += '<div class="modalContent row" style="margin-top: 10px; height: 60%;">';
        modalHtml += '<div style="text-align: center;">';
        modalHtml += '<h6>Are you sure You want to delete this contact?</h6><br>';
        modalHtml += '<h6>' + email + '</h6>';
        modalHtml += '</div>';
        modalHtml += '</div>';
        modalHtml += '<div style="bottom: 0; text-align: center;">';
        modalHtml += '<button id="deleteContactModalClose" class="btn btn-info">Cancel</button>';
        modalHtml += '<button id="deleteConfirm_' + email + '" class="btn red" style="margin-left: 5px;">Delete</button>';
        modalHtml += '</div>';
        modalHtml += '</div>';

        $("html body").append(modalHtml);

        var modal = $("#deleteContactModal");
        modal.openModal({
            dismissible: false
        });

        $("#deleteContactModalClose").click(function(e) {
            e.preventDefault();
            modal.closeModal();
            modal.remove();
        });

        $("[id^='deleteConfirm_']").click(function(e){
            var buttonId = e.currentTarget.id;
            var contactEmail = buttonId.split("_")[1];
            this.deleteContact(contactEmail);
            modal.closeModal();
            modal.remove();
        }.bind(this));

    }
};
