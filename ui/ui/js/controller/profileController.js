var ProfileController = function (model) {
    this.model = model;
    this.modal = this.createSmsVerificationModal();
};

ProfileController.prototype = {
    init : function() {
        var userInfo = this.getUserInfo();

        $("#profileAvatar").html("<a id='takeProfilePictureBtn' href='#'>" + createAvatar(userInfo.name, "#fff", "#212121") + "</a>");

        $("#profileNameDisplay").html(userInfo.name);

        $("#profileEmailDisplay").html(userInfo.username);

        $("#profilePubKeyBtn").click(function (e) {
            e.preventDefault();
            var pubKey = this.model.publicKey;
            this.openPublicKeyInfoDialog(pubKey);
        }.bind(this));

        $("#submitEmailUpdateBtn").click(this.updateEmail.bind(this));
        $("#submitUpdatePhoneBtn").click(this.requestPhoneUpdate.bind(this));
        $("#submitUpdateName").click(this.updateName.bind(this));
    },
    getUserInfo: function () {
        return {
            "username" : this.model.username,
            "phoneNumber" : this.model.phoneNumber,
            "name" : this.model.name,
            "publicKey" : this.model.publicKey
        }
    },
    openPublicKeyInfoDialog : function (publicKey) {
        var html = "<span id='pubkeyDialog'>" + formatPublicKey(publicKey) + "<br><small class='pull-right'>Click to copy to clipboard.</small></span>";
        var pubKeyDialog = createContextLikeMenu(html, true);
        pubKeyDialog.open();

        $("#pubkeyDialog").click(function (e) {
            e.preventDefault();
            windowService.copyTextToClipboard(publicKey).then(function () {
                BootstrapDialog.closeAll();
                this.displayNotification("Public Key has been copied to clipboard", "success");
            }.bind(this)).catch(function (e) {
                KEYTAP.exceptionController.displayDebugMessage(e);
                console.log("An error occured while copying public key to clipboard");
            });
        }.bind(this));
    },
    setUserInfo: function (userInfo, publicKey) {
        this.model.setUserInfo(userInfo.email, userInfo["phone-number"], userInfo.name, publicKey);
    },
    clearCache : function () {
        this.model.setUserInfo('', '', '')
    },
    requestPhoneUpdate : function (e) {
        e.preventDefault();
        var formValid = validateForm("#updatePhoneForm");
        var phoneValid = this.validatePhone();
        var button = $("#submitUpdatePhoneBtn");

        button.prop("disabled", true);
        $("#updatePhoneFormDiv .formError").html("");

        if(formValid == true && phoneValid == true) {
            var phone = getFormatedPhoneNumber($("#phone").val(), $("#countrySelect").val());

            accountModifictationService.requestPhoneUpdate(phone).then(function (result) {
                if (result.successful === true) {
                    button.prop("disabled", false);
                    this.openSmsVerificationModal();
                }
                else {
                    $("#updatePhoneFormDiv .formError").html("<li>" + result.errorMessage + "</li>");
                    button.prop("disabled", false);
                    this.displayNotification("Phone number update has failed", "danger");
                }
            }.bind(this)).catch(function (e) {
                $("#updatePhoneFormDiv .formError").html("<li>An error occured</li>");
                KEYTAP.exceptionController.displayDebugMessage(e);
                button.prop("disabled", false);
                this.displayNotification("Phone number update has failed", "danger");
            });
        }
        else {
            button.prop("disabled", false);
        }
    },
    updateEmail : function (e) {
        e.preventDefault();
        var button = $("#submitEmailUpdateBtn");
        button.prop("disabled", true);
        $("#updateEmailFormDiv .formError").html("");
        if(validateForm("#updateEmailForm")) {
            var email = $("#profileEmail").val();

            accountModifictationService.updateEmail(email).then(function (result) {
                if (result.successful === true) {
                    $("#profileEmail").val("");
                    this.model.setUsername(email);
                    $("#profileEmailDisplay").html(email);
                    button.prop("disabled", false);
                    this.displayNotification("Your email has been updated", "success");
                }
                else {
                    $("#updateEmailFormDiv .formError").html("<li>" + result.errorMessage + "</li>");
                    button.prop("disabled", false);
                    this.displayNotification("Email update has failed", "danger");
                }
            }.bind(this)).catch(function (e){
                $("#updateEmailFormDiv .formError").html("<li>An error occured</li>");
                KEYTAP.exceptionController.displayDebugMessage(e);
                button.prop("disabled", false);
                this.displayNotification("Email update has failed", "danger");
            });
        }
        else {
            button.prop("disabled", false);
        }
    },
    updateName : function (e) {
        e.preventDefault();
        var button = $("#submitUpdateName");
        button.prop("disabled", true);
        $("#updateNameDiv .formError").html("");
        if(validateForm("#updateNameForm")) {
            var name = $("#profileName").val();

            accountModifictationService.updateName(name).then(function (result) {
                if (result.successful === true) {
                    $("#profileName").val("");
                    this.model.setName(name);
                    $("#profileNameDisplay").html(name);
                    button.prop("disabled", false);
                    this.displayNotification("Your name has been updated", "success");
                }
                else {
                    $("#updateNameDiv .formError").html("<li>" + result.errorMessage + "</li>");
                    button.prop("disabled", false);
                    this.displayNotification("Name update has failed", "danger");
                }
            }.bind(this)).catch(function (e){
                $("#updateNameDiv .formError").html("<li>An error occured</li>");
                KEYTAP.exceptionController.displayDebugMessage(e);
                button.prop("disabled", false);
                this.displayNotification("Name update has failed", "danger");
            });
        }
        else {
            button.prop("disabled", false);
        }
    },
    confirmPhone : function () {
        var code = $("#smsCode").val();
        if(code != null && code != '') {
            accountModifictationService.confirmPhoneNumber(code).then(function (result) {
                if (result.successful === true) {
                    $("#phone").val("");
                    this.model.setPhoneNumber(result.accountInfo['phone-number']);
                    this.modal.close();
                    this.displayNotification("Your phone number has been updated", "success");
                }
                else {
                    $("#verification-error").html("<li>" + result.errorMessage + "</li>");
                    this.displayNotification("Phone number update has failed", "danger");
                }
            }.bind(this)).catch(function (e) {
                $("#verification-error").html("<li>An error occured</li>");
                KEYTAP.exceptionController.displayDebugMessage(e);
                this.displayNotification("Phone number update has failed", "danger");
            })
        }
    },
    createSmsVerificationModal: function () {
        var html = '<div class="valign-wrapper row form-wrapper" style="background-color: #fff; padding: 0; min-height: 100%;">' +
            '<div class="valign col s12" style="padding: 0;">' +
                '<div class="container" style="margin: 0; padding: 0;">' +
                    '<ul id="verification-error" style="color: red;">' +
                    '</ul>' +
                    '<h6 style="text-align: center; color: #9e9e9e;">You should receive a sms verification code shortly</h6>' +
                    '<form id="verificationForm" method="post">' +
                        '<div class="group-form col s12">' +
                            '<i class="mdi mdi-lock"></i>' +
                            '<input id="smsCode" type="text" required autocapitalize="off" placeholder="Verification code" style="border: 1px solid #eeeeee; color: #212121;">' +
                        '</div>' +
                        '<input type="hidden" id="email">' +
                    '</form>' +
                    '<button class="waves-effect waves-light btn-lg" style="width: 40%; background-color: red; margin: 10px 5px 10px 5px; padding: 10px 8px;" onclick="BootstrapDialog.closeAll();">Cancel</button>' +
                    '<button class="waves-effect waves-light btn-lg secondary-color" style="width: 40%; margin: 10px 5px 10px 5px; padding: 10px 8px;" onclick="KEYTAP.profileController.confirmPhone();">Confirm</button>' +
                    '<div style="text-align: center">' +
                        '<span>' +
                            'Didn\'t receive your verification code? <br>' +
                            '<a id="resendVerificationCode" class="secondary-color-text" href="#">Resend</a><br>' +
                        '</span>' +
                    '</div>' +
                '</div>' +
            '</div>' +
        '</div>';

        var modalContent = $(document.createElement("div"));
        modalContent.addClass("valign-wrapper");
        modalContent.addClass("row");

        var container = $(document.createElement("div"));
        container.addClass("valign");
        container.html(html);

        modalContent.append(container);

        var htmlContent = $("<div>").append(html).html();

        var bd = new BootstrapDialog();
        bd.setCssClass("statusModal whiteModal mediumModal");
        bd.setClosable(false);
        bd.setMessage(htmlContent);

        return bd;
    },
    openSmsVerificationModal : function () {
        this.modal.open();
    },
    displayNotification : function (message, type) {
        var notify = $.notify({
            icon: "icon-pull-left fa fa-info-circle",
            message: message
        }, {
            type: type,
            delay: 3000,
            allow_dismiss: false,
            offset: {
                y: 66,
                x: 20
            }
        });
    },
    validatePhone : function () {
        var phoneInput = $("#phone");
        var phoneValue = phoneInput.val();
        var valid = validatePhone(phoneValue, $("#countrySelect").val());
        var invalidDiv = $(".invalidPhone");

        if(phoneValue == "")
            invalidDiv.remove();

        if(!valid) {
            if(phoneValue != "") {
                phoneInput.addClass("invalid");
                if (!invalidDiv.length) {
                    phoneInput.after("<div class='pull-right invalidPhone filled' style='color: red;'><p>Phone Number seems invalid.</p></div>");
                }
            }
        }
        else {
            phoneInput.removeClass("invalid");
            invalidDiv.remove();
        }

        return valid;
    }
};