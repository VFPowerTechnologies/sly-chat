var ProfileController = function () {
    this.username = '';
    this.phoneNumber = '';
    this.name = '';
    this.publicKey = '';
    this.requestedPhone = '';
};

ProfileController.ids = {
    emailDisplay : '#profileEmail',
    nameDisplay : '#profileName',
    phoneDisplay : '#profilePhone',
    pubKeyDisplay : '#profilePubKey',
    updateProfileForm : '#updateProfileForm',
    updatePhoneForm : '#updatePhoneForm',
    updateProfileNameInput : '#profileUpdateNameInput',
    updateProfileEmailInput : '#profileUpdateEmailInput',
    saveProfileUpdateBtn : '#saveProfileUpdateBtn',
    countryInput : '#countrySelect',
    phoneInput : '#phone',
    requestPhoneUpdateBtn : '#requestPhoneUpdateBtn',
    smsCodeInput : '#smsCodeInput'
};

ProfileController.prototype = {
    setUserInfo : function (userInfo, publicKey) {
        this.username = userInfo.email;
        this.phoneNumber = userInfo['phone-number'];
        this.name = userInfo.name;
        this.publicKey = publicKey;
    },

    resetProfileInfo : function () {
        this.username = '';
        this.phoneNumber = '';
        this.name = '';
        this.publicKey = '';
    },

    displayInfo : function () {
        $(ProfileController.ids.emailDisplay).html(this.username);
        $(ProfileController.ids.nameDisplay).html(this.name);
        $(ProfileController.ids.phoneDisplay).html(this.phoneNumber);
        $(ProfileController.ids.pubKeyDisplay).html(formatPublicKey(this.publicKey));
    },

    openProfileEditPopup : function () {
        var content = '<div style="max-width: 800px; margin: 0 auto;"><form id="updateProfileForm">' +
            '<div class="list-block">' +
            '<ul><li><div class="item-content"><div class="item-media"><i class="icon icon-form-name"></i></div>' +
            '<div class="item-inner"><div class="item-input"><input id="profileUpdateNameInput" type="text" placeholder="Name" required autocorrect="off" value="' + this.name + '"/> </div>' +
            '</div></div></li><li><div class="item-content">' +
            '<div class="item-media"><i class="icon icon-form-email"></i></div>' +
            '<div class="item-inner"><div class="item-input">' +
            '<input id="profileUpdateEmailInput" type="email" placeholder="E-mail" required autocorrect="off" autocapitalize="off" value="' + this.username + '" />' +
            '</div></div></div></li></ul></div>' +
            '<div class="list-block list-error-block"><ul class="error-block"></ul></div>' +
            '<div class="content-block">' +
            '<input id="saveProfileUpdateBtn" type="submit" value="Save" class="button button-big button-fill" style="width: 100px; margin: 0 auto; display: block;"/>' +
            '</div></form>' +
            '<form id="updatePhoneForm"><div class="content-block-title">Phone Update Request</div><div class="list-block"><ul>' +
            '<li>' +
            '<div class="item-content"><div class="item-media"><i class="icon icon-form-comment"></i></div>' +
            '<div class="item-inner"><div class="item-input">' +
            '<select id="countrySelect" style="color: #aaaaaa;" required>' +
            '<option selected disabled>Country</option></select></div></div></div></li><li>' +
            '<div class="item-content">' +
            '<div class="item-media"><i id="phoneInputIcon" class="icon icon-form-tel"></i><span id="phoneIntlExt" style="display: none; text-align: center; min-width: 29px; height: 29px; line-height: 29px; color: #ffffff; background-color: #8e8e93; border-radius: 5px;"></span></div>' +
            '<div class="item-inner"><div class="item-input">' +
            '<input id="phone" type="tel" placeholder="Phone Number" required value="' + this.phoneNumber + '"/>' +
            '</div></div></div></li>' +
            '</ul></div>' +
            '<div class="list-block list-error-block"><ul class="error-block"></ul></div>' +
            '<div class="content-block">' +
            '<input id="requestPhoneUpdateBtn" type="submit" value="Update" class="button button-big button-fill" style="width: 100px; margin: 0 auto; display: block;"/>' +
            '</div></form></div>';

        openInfoPopup(content, "Update Account Info");

        updatePhoneWithIntl();

        $(ProfileController.ids.saveProfileUpdateBtn).click(function (e){
            e.preventDefault();
            this.submitProfileUpdateForm();
        }.bind(this));

        $(ProfileController.ids.requestPhoneUpdateBtn).click(function (e){
            e.preventDefault();
            this.requestPhoneUpdate();
        }.bind(this));

        $$(ProfileController.ids.countryInput).on("change", function(e) {
            var ext = $("#countrySelect :selected").text().split("+")[1];
            setPhoneExt(ext);
            // TODO Validate Phone Input
        });
    },

    submitProfileUpdateForm : function () {
        var valid = slychat.validateForm($(ProfileController.ids.updateProfileForm));
        if (valid) {
            var total = 0;
            var newName = $(ProfileController.ids.updateProfileNameInput).val();
            var newEmail = $(ProfileController.ids.updateProfileEmailInput).val();
            if (this.name !== newName) {
                total += 1;
                accountModifictationService.updateName(newName).then(function (result) {
                    if (result.successful === true) {
                        total--;
                        this.name = newName;
                        $(ProfileController.ids.nameDisplay).html(this.name);
                        if (total == 0) {
                            slychat.closeModal();
                            this.openNotification("Updates has been saved", "custom-notification", 2000);
                        }
                    }
                    else
                        this.openNotification(result.errorMessage, "custom-notification fail", 2000);
                }.bind(this)).catch(function (e){
                    exceptionController.handleError(e);
                    this.openNotification("An error occurred", "custom-notification fail", 2000);
                });
            }

            if (this.username !== newEmail) {
                total += 1;
                accountModifictationService.updateEmail(newEmail).then(function (result) {
                    if (result.successful === true) {
                        total--;
                        this.username = newEmail;
                        $(ProfileController.ids.emailDisplay).html(newEmail);
                        if (total == 0) {
                            slychat.closeModal();
                            this.openNotification("Updates has been saved", "custom-notification", 2000);
                        }
                    }
                    else {
                        this.openNotification(result.errorMessage, "custom-notification fail", 2000);
                    }
                }.bind(this)).catch(function (e){
                    exceptionController.handleError(e);
                    this.openNotification("An error occurred", "custom-notification fail", 2000);
                });
            }
        }
    },

    requestPhoneUpdate : function () {
        var phoneValue = $(ProfileController.ids.phoneInput).val();
        var selectedCountry = $(ProfileController.ids.countryInput).val();
        var phone = getFormatedPhoneNumber(phoneValue, selectedCountry);

        var phoneValid = validatePhone(phoneValue, selectedCountry);
        if (phoneValid && this.phoneNumber !== phone) {
            this.requestedPhone = phone;
            accountModifictationService.requestPhoneUpdate(phone).then(function (result) {
                if (result.successful === true) {
                    slychat.closeModal();
                    this.openNotification("We sent you a verification sms", "custom-notification", 2000);
                    this.openSmsVerificationModal();
                }
                else {
                    this.openNotification(result.errorMessage, "custom-notification fail", 2000);
                }
            }.bind(this)).catch(function (e) {
                exceptionController.handleError(e);
                this.openNotification("An error occurred", "custom-notification fail", 2000);
            });
        }
    },

    submitSmsVerification : function () {
        var code = $(ProfileController.ids.smsCodeInput).val();
        if (code === '')
            return;

        slychat.showPreloader();
        accountModifictationService.confirmPhoneNumber(code).then(function (result) {
            slychat.hidePreloader();
            if (result.successful === true) {
                $('smsCodeInput').val('');
                this.phoneNumber = this.requestedPhone;
                this.requestedPhone = '';
                $(ProfileController.ids.phoneDisplay).html(this.phoneNumber);
                slychat.closeModal();
                this.openNotification("Your phone number has been updated", "custom-notification", 2000);
            }
            else {
                this.openNotification(result.errorMessage, "custom-notification fail", 2000);
            }
        }.bind(this)).catch(function (e) {
            slychat.hidePreloader();
            this.openNotification("An error occurred", "custom-notification fail", 2000);
            console.log(e);
        });
    },

    openSmsVerificationModal : function () {
        var formContent = '<li>' +
                '<div class="item-content">' +
                    '<div class="item-media"><i class="icon icon-form-email"></i></div>' +
                    '<div class="item-inner">' +
                        '<div class="item-input">' +
                            '<input id="smsCodeInput" type="text" style="border-bottom: 1px solid #eeeeee;" placeholder="Sms Code"/>' +
                        '</div>' +
                    '</div>' +
                '</div>' +
            '</li>';

        this.openUpdateModal(formContent, 'submitSmsVerification');
    },

    openUpdateModal : function (formContent, method) {
        if($(".profile-update-modal.modal-in").length > 0)
            slychat.closeModal();
        else {
            slychat.pickerModal(
                '<div class="picker-modal profile-update-modal">' +
                    '<div class="picker-modal-inner">' +
                        '<div class="content-block">' +
                            '<div class="list-block">' +
                                '<ul>' +
                                    formContent +
                                '</ul>' +
                            '</div>' +
                            '<div class="content-block">' +
                                '<div class="row">' +
                                    '<div class="col-100 button-container">' +
                                        '<button class="button button-big button-fill color-red" onclick="slychat.closeModal()">Cancel</button>' +
                                        '<button id="submitUpdateBtn" class="button button-big button-fill" onclick="profileController.' + method + '()">Submit</button>' +
                                    '</div>' +
                                '</div>' +
                            '</div>' +
                        '</div>' +
                    '</div>' +
                '</div>'
            );
        }
    },

    openNotification : function(message, classes, hold) {
        var options = {
            title: message,
            hold: hold,
            closeOnClick: true
        };

        slychat.addNotification(options);
    }
};