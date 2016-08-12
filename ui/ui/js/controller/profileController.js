var ProfileController = function () {
    this.username = '';
    this.phoneNumber = '';
    this.name = '';
    this.publicKey = '';
    this.requestedPhone = '';
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
        $("#profileEmail").html(this.username);
        $("#profileName").html(this.name);
        $("#profilePhone").html(this.phoneNumber);
    },

    submitNameUpdate : function () {
        var name = $('#profileNameInput').val();

        if(name === '')
            return;

        slychat.showPreloader();

        accountModifictationService.updateName(name).then(function (result) {
            slychat.hidePreloader();
            if (result.successful === true) {
                $('#profileNameInput').val("");
                this.name = name;
                $("#profileName").html(name);
                slychat.closeModal();
                this.openNotification("Your name has been updated", "custom-notification", 2000);
            }
            else {
                this.openNotification(result.errorMessage, "custom-notification fail", 2000);
            }
        }.bind(this)).catch(function (e){
            slychat.hidePreloader();
            console.log(e);
            this.openNotification("An error occurred", "custom-notification fail", 2000);
        });
    },

    submitEmailUpdate : function () {
        var email = $('#profileEmailInput').val();

        if(email === '')
            return;

        slychat.showPreloader();

        accountModifictationService.updateEmail(email).then(function (result) {
            slychat.hidePreloader();
            if (result.successful === true) {
                $('#profileEmailInput').val("");
                this.username = email;
                $("#profileEmail").html(email);
                slychat.closeModal();
                this.openNotification("Your email has been updated", "custom-notification", 2000);
            }
            else {
                this.openNotification(result.errorMessage, "custom-notification fail", 2000);
            }
        }.bind(this)).catch(function (e){
            slychat.hidePreloader();
            console.log(e);
            this.openNotification("An error occurred", "custom-notification fail", 2000);
        });
    },

    requestPhoneUpdate : function () {
        var phoneValue = $("#phone").val();
        var selectedCountry = $("#countrySelect").val();
        var phone = getFormatedPhoneNumber(phoneValue, selectedCountry);

        var phoneValid = validatePhone(phoneValue, selectedCountry);
        if (phoneValid) {
            slychat.showPreloader();
            this.requestedPhone = phone;
            accountModifictationService.requestPhoneUpdate(phone).then(function (result) {
                slychat.hidePreloader();
                if (result.successful === true) {
                    slychat.closeModal();
                    this.openSmsVerificationModal();
                }
                else {
                    this.openNotification(result.errorMessage, "custom-notification fail", 2000);
                }
            }.bind(this)).catch(function (e) {
                slychat.hidePreloader();
                console.log(e);
                this.openNotification("An error occurred", "custom-notification fail", 2000);
            });
        }
    },

    submitSmsVerification : function () {
        var code = $('smsCodeInput').val();
        if (code === '')
            return;

        slychat.showPreloader();
        accountModifictationService.confirmPhoneNumber(code).then(function (result) {
            slychat.hidePreloader();
            if (result.successful === true) {
                $('smsCodeInput').val('');
                this.phone = this.requestedPhone;
                this.requestedPhone = '';
                $('#profilePhone').html(this.phone);
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

    openNameUpdateForm : function () {
        var formContent = '<li>' +
                '<div class="item-content">' +
                    '<div class="item-media"><i class="icon icon-form-name"></i></div>' +
                    '<div class="item-inner">' +
                        '<div class="item-input">' +
                            '<input id="profileNameInput" type="text" placeholder="Name" required autocorrect="off" value="' + this.name +'" style="border-bottom: 1px solid #eeeeee;"/>' +
                        '</div>' +
                    '</div>' +
                '</div>' +
            '</li>';
        this.openUpdateModal(formContent, 'submitNameUpdate');
    },

    openEmailUpdateForm : function () {
        var formContent = '<li>' +
                '<div class="item-content">' +
                    '<div class="item-media"><i class="icon icon-form-email"></i></div>' +
                    '<div class="item-inner">' +
                        '<div class="item-input">' +
                            '<input id="profileEmailInput" type="email" placeholder="E-Mail" style="border-bottom: 1px solid #eeeeee;" required autocorrect="off" autocapitalize="off" value="' + this.username +'"/>' +
                        '</div>' +
                    '</div>' +
                '</div>' +
            '</li>';

        this.openUpdateModal(formContent, 'submitEmailUpdate');
    },

    openPhoneUpdateForm : function () {
        var formContent = '<li>' +
                '<div class="item-content">' +
                    '<div class="item-media"><i class="icon icon-form-email"></i></div>' +
                    '<div class="item-inner">' +
                        '<div class="item-input">' +
                            '<select id="countrySelect" style="color: #aaaaaa;" required>' +
                                '<option selected disabled>Country</option>' +
                            '</select>' +
                        '</div>' +
                    '</div>' +
                '</div>' +
            '</li>' +
            '<li>' +
                '<div class="item-content">' +
                    '<div class="item-media"><i id="phoneInputIcon" class="icon icon-form-tel"></i><span id="phoneIntlExt" style="display: none; text-align: center; min-width: 29px; height: 29px; line-height: 29px; color: #ffffff; background-color: #8e8e93; border-radius: 5px;"></span></div>' +
                    '<div class="item-inner">' +
                        '<div class="item-input">' +
                            '<input id="phone" type="tel" placeholder="Phone Number" required/>' +
                        '</div>' +
                    '</div>' +
                '</div>' +
            '</li>';

        this.openUpdateModal(formContent, 'requestPhoneUpdate');

        updatePhoneWithIntl();

        $$('#countrySelect').on("change", function(e) {
            var ext = $("#countrySelect :selected").text().split("+")[1];
            setPhoneExt(ext);
            // TODO Validate Phone Input
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