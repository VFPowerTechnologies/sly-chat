var RegistrationController = function () {
    this.registrationInfo = {
        name : '',
        email : '',
        password : '',
        phoneNumber : ''
    }
};

RegistrationController.prototype = {
    register : function () {
        var form = $("#registrationForm");
        var formValid = slychat.validateForm(form);

        if(!formValid)
            return;

        var email = $$('#registration-email').val();
        var name = $$('#registration-name').val();
        var password = $$('#registration-password').val();
        var phoneValue = $("#phone").val();
        var selectedCountry = $("#countrySelect").val();
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
                console.log(e);
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
        var form = $("#phoneUpdateForm");
        var valid = slychat.validateForm(form);
        if (!valid)
            return;

        var email = $("#hiddenEmail").val();
        var password = $("#phone-update-password").val();
        var phoneValue = $("#phone").val();
        var selectedCountry = $("#countrySelect").val();
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
                console.log(e);
            });
        }
    },

    getRegistrationInfo : function () {
        return this.registrationInfo;
    },

    submitVerificationCode : function () {
        var form = $("#smsVerificationForm");
        var formValid = slychat.validateForm(form);
        if (!formValid)
            return;

        var code = $$("#smsCode").val();
        var email = $$('#hiddenEmail').val();
        var password = $$('#hiddenPassword').val();

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
            console.log(e);
        });
    },

    resendVerificationCode : function () {
        var email = $$('#hiddenEmail').val();

        slychat.showPreloader();
        $$("#resendVerificationCode").prop("disabled", true);
        registrationService.resendVerificationCode(email).then(function (result) {
            if(result.successful == true) {
                setTimeout(function(){
                    $$("#resendVerificationCode").prop("disabled", false);
                }, 20000);
                slychat.hidePreloader();
            }
            else {
                console.log(result.errorMessage);
                $("#resendVerificationCode").prop("disabled", false);
                slychat.hidePreloader();
            }
        }).catch(function (e) {
            console.log(e);
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