var RegistrationController = function (model) {
    this.model = model;
    this.modal = this.createRegisterModal();
};

RegistrationController.prototype = {
    init : function () {
        $(document).on("click", "#submitRegisterBtn", function(e){
            e.preventDefault();

            var phone = this.getFormattedPhoneNumber();

            var submitRegisterBtn = $("#submitRegisterBtn");

            submitRegisterBtn.prop("disabled", true);

            this.model.setItems({
                "name" : $("#name").val(),
                "email" : $("#email").val(),
                "phoneNumber" : phone,
                "password" : $("#password").val()
            });

            var formValid = this.model.validate();
            var phoneValid = this.validatePhone();

            if(formValid == true && phoneValid == true){
                this.register();
                this.modal.open();
            }
            else {
                submitRegisterBtn.prop("disabled", false);
            }
        }.bind(this));

        $(document).on("click", "#signInGoBtn", function (e) {
            e.preventDefault();
            KEYTAP.navigationController.loadPage("login.html");
        });

        $(document).on("click", "#successLoginBtn", function (e) {
            e.preventDefault();
            this.modal.close();
            KEYTAP.loginController.modal.open();
            var info = this.model.getItems();
            KEYTAP.loginController.setInfo(info.email, info.password);
            KEYTAP.loginController.login();
        }.bind(this));

        $(document).on("click", "#submitVerificationCode", function (e) {
            e.preventDefault();

            var code = $("#smsCode").val();
            registrationService.submitVerificationCode(this.model.getItems().email, code).then(function (result) {
                if(result.successful == true) {
                    this.modal = createStatusModal(this.createRegistrationSuccessContent());
                    this.modal.open();
                }
                else {
                    document.getElementById("verification-error").innerHTML = "<li>" + result.errorMessage + "</li>";
                }
            }.bind(this)).catch(function (e) {
                KEYTAP.exceptionController.displayDebugMessage(e);
                document.getElementById("verification-error").innerHTML = "<li>Verification failed</li>";
            });
        }.bind(this));

        $(document).on("click", "#resendVerificationCode", function (e) {
            e.preventDefault();
            $("#resendVerificationCode").prop("disabled", true);
            registrationService.resendVerificationCode(this.model.getItems().email).then(function (result) {
                if(result.successful == true) {
                    setTimeout(function(){
                        $("#resendVerificationCode").prop("disabled", false);
                    }, 20000);
                }
                else {
                    document.getElementById("verification-error").innerHTML = "<li>" + result.errorMessage + "</li>";
                    $("#resendVerificationCode").prop("disabled", false);
                }
            }).catch(function (e) {
                document.getElementById("verification-error").innerHTML = "<li>An error occurred</li>";
                KEYTAP.exceptionController.displayDebugMessage(e);
            });
        }.bind(this));

        $(document).on("click", "#updatePhoneNumberLink", function (e) {
            e.preventDefault();
            KEYTAP.navigationController.loadPage("updatePhone.html");
        });

        $(document).on("click", "#updatePhoneSubmitBtn", function (e) {
            e.preventDefault();
            var updateBtn = $("#updatePhoneSubmitBtn");

            updateBtn.prop("disabled", true);
            updateBtn.html("<i class='fa fa-spinner fa-spin'></i>");

            var validation = $("#updatePhoneForm").parsley({
                errorClass: "invalid",
                focus: 'none',
                errorsWrapper: '<div class="pull-right parsley-errors-list" style="color: red;"></div>',
                errorTemplate: '<p></p>'
            });

            var phone = this.getFormattedPhoneNumber();

            var isValid = validation.validate();
            var phoneValid = this.validatePhone();

            if (isValid && phoneValid) {
                registrationService.updatePhone({
                    "email": KEYTAP.loginController.model.getLogin(),
                    "password": $("#phoneUpdatePassword").val(),
                    "phoneNumber": phone
                }).then(function (result) {
                    if (result.successful == true) {
                        KEYTAP.navigationController.loadPage("smsVerification.html");
                    }
                    else {
                        updateBtn.prop("disabled", false);
                        updateBtn.html("Submit");
                        document.getElementById("verification-error").innerHTML = "<li>" + result.errorMessage + "</li>";
                    }
                }.bind(this)).catch(function (e) {
                    updateBtn.prop("disabled", false);
                    updateBtn.html("Submit");
                    KEYTAP.exceptionController.displayDebugMessage(e);
                    document.getElementById("verification-error").innerHTML = "<li>An error occured</li>";
                });
            }
            else {
                updateBtn.prop("disabled", false);
                updateBtn.html("Submit");
            }
        }.bind(this));

        $(document).on("change", "#countrySelect", function(e) {
            var hiddenPhoneInput = $("#hiddenPhoneInput");
            hiddenPhoneInput.intlTelInput("setCountry", $("#countrySelect").val());
            hiddenPhoneInput.intlTelInput("setNumber", $("#phone").val());

            var ext = $("#countrySelect :selected").text().split("+")[1];

            this.setPhoneExt(ext);

            this.validatePhone();
        }.bind(this));

        $(document).on("change keyup", "#phone", function (e) {
            $("#hiddenPhoneInput").intlTelInput("setNumber", $("#phone").val());
            if($(".invalidPhone").length)
                this.validatePhone();
        }.bind(this));
    },
    register : function () {
        registrationService.doRegistration(this.model.getItems()).then(function (result) {
            if(result.successful == true) {
                this.modal.close();
                KEYTAP.navigationController.loadPage("smsVerification.html");
            }
            else{
                this.modal.close();
                this.displayRegistrationError(result);
                $("#submitRegisterBtn").prop("disabled", false);
            }
        }.bind(this)).catch(function(e) {
            this.modal.close();
            KEYTAP.exceptionController.displayDebugMessage(e);
            document.getElementById("register-error").innerHTML = "<li>Registration failed</li>";
            $("#submitRegisterBtn").prop("disabled", false);
        }.bind(this));
    },
    displayRegistrationError : function (result) {
        document.getElementById("register-error").innerHTML = "<li>" + result.errorMessage + "</li>";
        console.log("displaying error");
    },
    createRegistrationSuccessContent : function () {
        var username = this.model.getItems().name;
        if(username == undefined)
            username = "";
        return "<div style='text-align: center;'> <h6 style='margin-bottom: 15px; color: whitesmoke;'>Registration Successful</h6> <p>Thank you <strong>" + username + "</p><p style='margin-bottom: 10px;'>Login to access your new account</p><button id='successLoginBtn' class='btn btn-success'>Login</button></div>";
    },
    validatePhone : function () {
        var phoneInput = $("#phone");
        var hiddenPhoneInput = $("#hiddenPhoneInput");
        var phoneValue = phoneInput.val();
        var valid = hiddenPhoneInput.intlTelInput("isValidNumber");
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
    },
    getFormattedPhoneNumber : function () {
        var hiddenPhoneInput = $("#hiddenPhoneInput");

        var countryData = hiddenPhoneInput.intlTelInput("getSelectedCountryData");
        var phoneValue = $("#phone").val();

        hiddenPhoneInput.intlTelInput("setNumber", phoneValue);

        return phoneValue.indexOf(countryData.dialCode) == 0 ?
            countryData.dialCode + phoneValue.substring(countryData.dialCode.length) :
                countryData.dialCode + phoneValue;

    },
    setPhoneExt : function (dialCode) {
        if(typeof dialCode != "undefined") {
            var extension = $("#phoneIntlExt");
            $("#phoneInputIcon").hide();

            extension.html("+" + dialCode);

            var width = extension.outerWidth(true);

            if (width + 24 > 43)
                $("#phone").css("padding-left", width + "px");

            extension.removeClass("hidden");
        }
    },
    createRegisterModal : function () {
        var html = "<div style='text-align: center;'> <h6 style='margin-bottom: 15px; color: whitesmoke;'>Registration in process</h6> <i class='fa fa-spinner fa-3x fa-spin'></i> <p id='registrationStatusUpdate' style='margin-top: 40px;'></p></div>";

        return createStatusModal(html);
    },
    clearCache : function () {
        this.model.clearCache();
    }
};
