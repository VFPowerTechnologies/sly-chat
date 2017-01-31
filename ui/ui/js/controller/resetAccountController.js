var ResetAccountController = function () {
    this.emailFreed = false;
    this.phoneFreed = false;
};

ResetAccountController.ids = {
    resetForm: "#resetForm",
    backToLogin: "#backToLogin",
    submitReset: "#submitReset",
    username: "#username",
    errorBlock: ".error-block",
    smsConfirmationForm: "#smsConfirmationForm",
    emailConfirmationForm: "#emailConfirmationForm",
    submitSmsCode: "#submitSmsVerification",
    submitEmailCode: "#submitEmailVerification",
    smsCode: "#smsCode",
    emailCode: "#emailCode"
};

ResetAccountController.prototype = {
    onPageLoad: function () {
        this.setEventListeners()
    },

    onConfirmPageLoad: function (data) {
        if (data.showEmailForm == true) {
            $(ResetAccountController.ids.emailConfirmationForm).show();
            this.emailFreed = false;
        }
        else {
            this.emailFreed = true;
        }

        if (data.showPhoneForm == true) {
            $(ResetAccountController.ids.smsConfirmationForm).show();
            this.phoneFreed = false;
        }
        else {
            this.phoneFreed = true;
        }

        $(ResetAccountController.ids.submitSmsCode).click(function (e) {
            e.preventDefault();
            this.submitSmsCode(data.username);
        }.bind(this));

        $(ResetAccountController.ids.submitEmailCode).click(function (e) {
            e.preventDefault();
            this.submitEmailCode(data.username);
        }.bind(this));

        $(ResetAccountController.ids.backToLogin).click(function (e) {
            navigationController.loadPage("login.html", false);
        })
    },

    setEventListeners: function () {
        $(ResetAccountController.ids.backToLogin).click(function (e){
            e.preventDefault();
            navigationController.loadPage("login.html", false)
        });

        $(ResetAccountController.ids.submitReset).click(function (e) {
            e.preventDefault();
            slychat.showPreloader();
            var errorBlock = $(ResetAccountController.ids.errorBlock);
            errorBlock.html("");
            var valid = slychat.validateForm($(ResetAccountController.ids.resetForm));

            if(!valid) {
                slychat.hidePreloader();
                return;
            }

            var username = $(ResetAccountController.ids.username).val();

            resetAccountService.resetAccount(username).then(function (result) {
                slychat.hidePreloader();
                if (result.successful) {
                    this.startVerification(username, result);
                }
                else {
                    errorBlock.append("<li>" + result.errorMessage + "</li>")
                }
            }.bind(this)).catch(function (e) {
                slychat.hidePreloader();
                errorBlock.append("<li>An error occured</li>")
                exceptionController.handleError(e);
            })
        }.bind(this));
    },

    startVerification: function (username, result) {
        var options = {
            url: "accountResetConfirm.html",
            query: {
                username: username,
                showEmailForm: result.emailIsReleased == false,
                showPhoneForm: result.phoneNumberIsReleased == false
            }
        };

        navigationController.loadPage("accountResetConfirm.html", true, options)
    },

    submitSmsCode: function (username) {
        var button = $(ResetAccountController.ids.submitSmsCode);
        button.prop("disabled", true);

        var errorBlock = $(ResetAccountController.ids.smsConfirmationForm).find(ResetAccountController.ids.errorBlock);
        errorBlock.html("");

        var valid = slychat.validateForm($(ResetAccountController.ids.smsConfirmationForm));
        if(!valid) {
            button.prop("disabled", false);
            return;
        }
        var smsCode = $(ResetAccountController.ids.smsCode).val();

        resetAccountService.submitPhoneNumberConfirmationCode(username, smsCode).then(function (result) {
            button.prop("disabled", false);
            if (result.successful) {
                this.phoneFreeSuccess();
            }
            else {
                errorBlock.append("<li>" + result.errorMessage + "</li>")
            }
        }.bind(this)).catch(function (e) {
            exceptionController.handleError(e);
            button.prop("disabled", false);
            errorBlock.append("<li>An error occured</li>")
        })
    },

    submitEmailCode: function (username) {
        var button = $(ResetAccountController.ids.submitEmailCode);
        button.prop("disabled", true);

        var errorBlock = $(ResetAccountController.ids.emailConfirmationForm).find(ResetAccountController.ids.errorBlock);
        errorBlock.html("");

        var valid = slychat.validateForm($(ResetAccountController.ids.emailConfirmationForm));
        if(!valid) {
            button.prop("disabled", false);
            return;
        }

        var emailCode = $(ResetAccountController.ids.emailCode).val();

        resetAccountService.submitEmailConfirmationCode(username, emailCode).then(function (result) {
            button.prop("disabled", false);
            if (result.successful) {
                this.emailFreeSuccess();
            }
            else {
                errorBlock.append("<li>" + result.errorMessage + "</li>")
            }
        }.bind(this)).catch(function (e) {
            button.prop("disabled", false);
            exceptionController.handleError(e);
            errorBlock.append("<li>An error occured</li>")
        });
    },

    phoneFreeSuccess: function () {
        this.phoneFreed = true;
        var callback = function () {
            if (this.phoneFreed && this.emailFreed)
                this.finishResetProcedure();
            else
                $(ResetAccountController.ids.smsConfirmationForm).remove();
        }.bind(this);

        slychat.alert("Your phone number has been release and can now be used for a new account", "Success", callback);
    },

    emailFreeSuccess: function () {
        this.emailFreed = true;
        var callback = function () {
            if (this.phoneFreed && this.emailFreed)
                this.finishResetProcedure();
            else
                $(ResetAccountController.ids.emailConfirmationForm).remove();
        }.bind(this);

        slychat.alert("Your email has been release and can now be used for a new account", "Success", callback);
    },

    finishResetProcedure: function () {
        var callback = function () {
            navigationController.loadPage("login.html", false);
        };
        slychat.alert("A uninstall and reinstall is needed on other devices to delete local data.", "Success", callback);
    }
};
