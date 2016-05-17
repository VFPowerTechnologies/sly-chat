var ContactController = function (model) {
    this.model = model;
    this.model.setController(this);
    this.syncing = false;
    this.notify = null;
};

ContactController.prototype = {
    init : function () {
        this.model.fetchConversation();
    },
    addContactPageEvent : function () {
        $(document).on('click', "#deleteContactModalClose", function(e) {
            e.preventDefault();
            BootstrapDialog.closeAll();
        });

        $(document).on("click", "[id^='deleteConfirm_']", function(e){
            var buttonId = e.currentTarget.id;
            var contactId = buttonId.split("_")[1];
            this.deleteContact(contactId);
            BootstrapDialog.closeAll();
        }.bind(this));
    },
    displayContacts : function (conversations) {
        var contactList = $("#contactList");
        var fragment = $(document.createDocumentFragment());

        for (var id in conversations) {
            if(conversations.hasOwnProperty(id)) {
                fragment.append(this.createContactBlock(conversations[id].contact, conversations[id].status));
            }
        }

        contactList.html(fragment);
    },
    addContactListSyncListener : function () {
        contactService.addContactListSyncListener(function (sync) {
            this.syncing = sync;
            if(sync == true) {
                this.showContactSyncingNotification();
            }else{
                this.closeNotification();
                if(window.location.href.indexOf("contacts.html") > -1) {
                    this.model.resetContacts();
                    this.model.fetchConversation();
                }
            }
        }.bind(this));
    },
    showContactSyncingNotification : function () {
        this.notify = $.notify({
            icon: "icon-pull-left fa fa-info-circle",
            message: " Contact List is syncing"
        }, {
            type: "warning",
            delay: 0,
            allow_dismiss: false,
            allow_duplicates: false,
            offset: {
                y: 66,
                x: 20
            }
        });
    },
    closeNotification : function () {
        if(this.notify != null) {
            this.notify.update("type", "success");
            this.notify.update("message", "Sync is completed");
            this.notify.update("icon", "fa fa-check-circle");

            setTimeout(function () {
                this.notify.close();
            }.bind(this), 3000);
        }
    },
    createContactBlock : function (contact, status) {
        var contactLinkClass = "contact-link ";
        var newBadge = "";

        if(status.unreadMessageCount > 0){
            contactLinkClass += "new-messages";
            newBadge = "<span class='pull-right label label-warning' style='line-height: 0.8'>" + "new" + "</span>";
        }

        var contactBlockDiv = $("<div class='" + contactLinkClass + "' id='contact_" + contact.id + "'></div>");
        var contactBlockHtml = "<div class='contact'>" + createAvatar(contact.name) + "<p style='display: inline-block;'>" +
            contact.name + "</p>" +
            "</div>" + newBadge;
        contactBlockDiv.html(contactBlockHtml);

        contactBlockDiv.click(function (e) {
            e.preventDefault();
            var id = $(this).attr("id").split("contact_")[1];
            KEYTAP.contactController.setCurrentContact(id);
            KEYTAP.navigationController.loadPage("chat.html", true);
        });

        contactBlockDiv.on("mouseheld", function (e) {
            e.preventDefault();
            vibrate(50);
            var contextMenu = KEYTAP.contactController.openContactContextLikeMenu(contact.id);
            contextMenu.open();
        });

        return contactBlockDiv;
    },
    openContactContextLikeMenu : function (contactId) {
        var html = "<div class='contextLikeMenu'>" +
            "<ul>" +
                "<li><a id='contactDetails_" + contactId + "' href='#'>Contact Details</a></li>" +
                "<li role='separator' class='divider'></li>" +
                "<li><a id='deleteContact_" + contactId + "' href='#'>Delete Contact</a></li>" +
            "</ul>" +
        "</div>";

        return createContextLikeMenu(html, true);
    },
    displayContactDetailsModal : function (id) {
        var contact = this.getContact(id);

        var html = "<div id='contactDetailsCloseDiv'><a href='#' onclick='BootstrapDialog.closeAll();'><i class='fa fa-close fa-2x'></i></a></div>" +
            "<div class='contact-details'>" +
            "<h6>Name:</h6>" +
            "<p>" + contact.name + "</p>" +
            "<h6>Email:</h6>" +
            "<p>" + contact.email + "</p>" +
            "<h6>Public Key:</h6>" +
            "<div style='border: 1px solid #212121;'" +
                "<p style='max-width: 100%;'>" + formatPublicKey(contact.publicKey) + "</p>" +
            "</div>" +
            "</div>";

        var contactDetailsModal = new BootstrapDialog();
        contactDetailsModal.setCssClass("statusModal whiteModal fullModal");
        contactDetailsModal.setClosable(true);
        contactDetailsModal.setMessage(html);
        contactDetailsModal.open();
    },
    loadContactPage : function (id, pushCurrentPage) {
        this.model.fetchConversationForChat(id, pushCurrentPage);
    },
    getCurrentContact : function () {
        return this.model.getCurrentContact();
    },
    setCurrentContact : function (id) {
        this.model.setCurrentContact(id);
    },
    getContact : function (id) {
        return this.model.getContact(id);
    },
    addNewContact : function () {
        var newContactBtn = $("#newContactBtn");
        newContactBtn.prop("disabled", true);
        if(validateForm("#addContactForm") == true && this.syncing == false){
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
        if(validateForm("#updateContactForm") == true){
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
    deleteContact : function (id) {
        contactService.removeContact(this.model.getContact(id)).then(function () {
            this.model.resetContacts();
            KEYTAP.navigationController.loadPage("contacts.html", false);
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

        var id = document.createElement("INPUT");
        phoneInput.id = "user-id";
        phoneInput.type = "hidden";
        phoneInput.value = contactDetails.id;

        var emailInput = document.createElement("INPUT");
        emailInput.id = "email";
        emailInput.type = "hidden";
        emailInput.value = contactDetails.email;

        var navbar = document.createElement("div");
        navbar.className = "navbar-btn center-align";

        var cancelBtn = document.createElement("button");
        cancelBtn.className = "btn-sm transparentBtn";
        cancelBtn.id = "cancelBtn";
        cancelBtn.type = "submit";
        cancelBtn.innerHTML = "Cancel";
        cancelBtn.style.fontSize = "18px";
        cancelBtn.style.color = "#eeeeee";

        var confirmBtn = document.createElement("button");
        confirmBtn.className = "btn-sm transparentBtn";
        confirmBtn.id = "confirmBtn";
        confirmBtn.type = "submit";
        confirmBtn.innerHTML = "Confirm";
        confirmBtn.style.fontSize = "18px";
        confirmBtn.style.color = "#eeeeee";

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
                "id" : parseInt($("#user-id").val(), 10),
                "name" : $("#name").val(),
                "phoneNumber" : $("#phoneNumber").val(),
                "email" : $("#email").val(),
                "publicKey" : $("#publicKey").val()

            }).then(function () {
                this.model.resetContacts();
                KEYTAP.navigationController.loadPage("contacts.html", false);
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
    displayDeleteContactModal: function(id) {
        var html = "<div class='contextLikeModalContent'>" +
            "<h6 class='contextLikeModal-title'>Delete Contact?</h6>" +
            "<p class='contextLikeModal-content'>Are you sure you want to delete " + this.getContact(id).email + "?</p>" +
            "<div class='contextLikeModal-nav'>" +
                "<button id='deleteContactModalClose' class='btn btn-sm transparentBtn'>Cancel</button>" +
                "<button id='deleteConfirm_" + id + "' class='btn btn-sm transparentBtn'>Confirm</button>" +
            "</div>" +
        "</div>";

        var modal = createContextLikeMenu(html, false);
        modal.open();
    },
    clearCache : function () {
        this.model.clearCache();
    }
};
