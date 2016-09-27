var RegistrationController = function () {
    this.registrationInfo = {
        name : '',
        email : '',
        password : '',
        phoneNumber : ''
    }
};

RegistrationController.ids = {
    registrationForm : '#registrationForm',
    phoneUpdateForm : '#phoneUpdateForm',
    smsVerificationForm : '#smsVerificationForm',
    registrationPasswordInput : '#registration-password',
    registrationPassConfirmInput : '#registration-password-confirm',
    registrationEmailInput : '#registration-email',
    registrationNameInput : '#registration-name',
    countryInput : '#countrySelect',
    phoneInput : '#phone',
    hiddenEmailInput : '#hiddenEmail',
    phoneUpdatePassword : '#phone-update-password',
    smsVerificationCode : '#smsCode',
    smsVerificationHiddenPass : '#hiddenPassword',
    resendVerificationCodeBtn : "#resendVerificationCode"
};

RegistrationController.prototype = {
    register : function () {
        var form = $(RegistrationController.ids.registrationForm);
        var formValid = slychat.validateForm(form);

        if(!formValid)
            return;

        var passwordConfirm = $$(RegistrationController.ids.registrationPassConfirmInput).val();
        var password = $$(RegistrationController.ids.registrationPasswordInput).val();

        if (password !== '' && password !== passwordConfirm) {
            form.find(".error-block").html("<li>Passwords does not match</li>");
            return;
        }

        var email = $$(RegistrationController.ids.registrationEmailInput).val();
        var name = $$(RegistrationController.ids.registrationNameInput).val();
        var phoneValue = $(RegistrationController.ids.phoneInput).val();
        var selectedCountry = $(RegistrationController.ids.countryInput).val();
        var phone = getFormatedPhoneNumber(phoneValue, selectedCountry);

        this.setRegistrationInfo(name, email, phone, password);

        var phoneValid = validatePhone(phoneValue, selectedCountry);

        if(formValid === true && phoneValid === true) {
            slychat.showPreloader();
            registrationService.doRegistration(this.registrationInfo).then(function (result) {
                slychat.hidePreloader();
                if (result.successful == true) {
                    var options = {
                        url: 'smsVerification.html',
                        query: {
                            email: this.registrationInfo.email,
                            password: this.registrationInfo.password
                        }
                    };
                    navigationController.loadPage("smsVerification.html", true, options);
                }
                else {
                    form.find(".error-block").html("<li>" + result.errorMessage +"</li>");
                    console.log(result);
                }
            }.bind(this)).catch(function (e) {
                slychat.hidePreloader();
                form.find(".error-block").html("<li>An unexpected error occurred</li>");
                exceptionController.handleError(e);
            }.bind(this));
        }
    },

    setRegistrationInfo : function (name, email, phone, password) {
        this.registrationInfo.name = name;
        this.registrationInfo.email = email;
        this.registrationInfo.phoneNumber = phone;
        this.registrationInfo.password = password;
    },

    updatePhone : function () {
        var form = $(RegistrationController.ids.phoneUpdateForm);
        var valid = slychat.validateForm(form);
        if (!valid)
            return;

        var email = $(RegistrationController.ids.hiddenEmailInput).val();
        var password = $(RegistrationController.ids.phoneUpdatePassword).val();
        var phoneValue = $(RegistrationController.ids.phoneInput).val();
        var selectedCountry = $(RegistrationController.ids.countryInput).val();
        var phone = getFormatedPhoneNumber(phoneValue, selectedCountry);

        var phoneValid = validatePhone(phoneValue, selectedCountry);

        if (phoneValid) {
            slychat.showPreloader();
            registrationService.updatePhone({
                "email": email,
                "password": password,
                "phoneNumber": phone
            }).then(function (result) {
                slychat.hidePreloader();
                if (result.successful == true) {
                    var options = {
                        url: "smsVerification.html",
                        query: {
                            email: email,
                            password: password
                        }
                    };
                    navigationController.loadPage("smsVerification.html", false, options);
                }
                else {
                    form.find(".error-block").html("<li>" + result.errorMessage +"</li>");
                }
            }.bind(this)).catch(function (e) {
                slychat.hidePreloader();
                form.find(".error-block").html("<li>An error occurred</li>");
                exceptionController.handleError(e);
            });
        }
    },

    submitVerificationCode : function () {
        var form = $(RegistrationController.ids.smsVerificationForm);
        var formValid = slychat.validateForm(form);
        if (!formValid)
            return;

        var code = $$(RegistrationController.ids.smsVerificationCode).val();
        var email = $$(RegistrationController.ids.hiddenEmailInput).val();
        var password = $$(RegistrationController.ids.smsVerificationHiddenPass).val();

        slychat.showPreloader();
        registrationService.submitVerificationCode(email, code).then(function (result) {
            if(result.successful == true) {
                loginService.login(email, password, true);
            }
            else {
                slychat.hidePreloader();
                form.find(".error-block").html("<li>" + result.errorMessage +"</li>");
                console.log(result.errorMessage);
            }
        }.bind(this)).catch(function (e) {
            slychat.hidePreloader();
            form.find(".error-block").html("<li>Verification failed</li>");
            exceptionController.handleError(e);
        });
    },

    resendVerificationCode : function () {
        var email = $$(RegistrationController.ids.hiddenEmailInput).val();

        slychat.showPreloader();
        $$("#resendVerificationCode").prop("disabled", true);
        registrationService.resendVerificationCode(email).then(function (result) {
            var resendCodeBtn = $(RegistrationController.ids.resendVerificationCodeBtn);
            if(result.successful == true) {
                setTimeout(function(){
                    resendCodeBtn.prop("disabled", false);
                }, 20000);
                slychat.hidePreloader();
            }
            else {
                console.log(result.errorMessage);
                resendCodeBtn.prop("disabled", false);
                slychat.hidePreloader();
            }
        }).catch(function (e) {
            exceptionController.handleError(e);
            slychat.hidePreloader();
        });
    },

    clearCache : function () {
        this.registrationInfo = {
            name : '',
            email : '',
            password : '',
            phoneNumber : ''
        };
    }
};