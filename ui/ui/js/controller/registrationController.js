var RegistrationController = function (model) {
    this.model = model;
};

RegistrationController.prototype = {
    init : function () {
        $(document).on("click", "#submitRegisterBtn", function(e){
            e.preventDefault();

            $("#submitRegisterBtn").prop("disabled", true);

            this.model.setItems({
                "name" : $("#name").val(),
                "email" : $("#email").val(),
                "phoneNumber" : $("#phone").val(),
                "password" : $("#password").val()
            });

            if(this.model.validate() == true){
                this.register();
                $("#statusModal").openModal({
                    dismissible: false
                });
            }
            else {
                $("#submitRegisterBtn").prop("disabled", false);
            }
        }.bind(this));

        $(document).on("click", "#signInGoBtn", function (e) {
            e.preventDefault();
            KEYTAP.navigationController.loadPage("login.html");
        });

        $(document).on("click", "#successLoginBtn", function (e) {
            e.preventDefault();
            $("#statusModal").html(this.createLoginModalContent());
            var info = this.model.getItems();
            KEYTAP.loginController.setInfo(info.email, info.password);
            KEYTAP.loginController.login();
        }.bind(this));

        $(document).on("click", "#submitVerificationCode", function (e) {
            e.preventDefault();

            var code = $("#smsCode").val();
            registrationService.submitVerificationCode(this.model.getItems().email, code).then(function (result) {
                if(result.successful == true) {
                    var modal = $("#statusModal");
                    modal.html(this.createRegistrationSuccessContent());
                    modal.openModal({
                        dismissible: false
                    });
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
    },
    register : function () {
        registrationService.doRegistration(this.model.getItems()).then(function (result) {
            if(result.successful == true) {
                $("#statusModal").closeModal();
                KEYTAP.navigationController.loadPage("smsVerification.html");
            }
            else{
                $("#statusModal").closeModal();
                this.displayRegistrationError(result);
                $("#submitRegisterBtn").prop("disabled", false);
            }
        }.bind(this)).catch(function(e) {
            $("#statusModal").closeModal();
            KEYTAP.exceptionController.displayDebugMessage(e);
            document.getElementById("register-error").innerHTML = "<li>Registration failed</li>";
            $("#submitRegisterBtn").prop("disabled", false);
        });
    },
    displayRegistrationError : function (result) {
        document.getElementById("register-error").innerHTML = "<li>" + result.errorMessage + "</li>";
        console.log("displaying error");
    },
    createRegistrationSuccessContent : function () {
        var username = this.model.getItems().name;
        var content = "<div class='modalHeader'><h5>Registration Successful</h5></div><div class='modalContent'><p>Thank you <strong>" + username + "</p><p style='margin-bottom: 10px;'>Login to access your new account</p><button id='successLoginBtn' class='btn btn-success'>Login</button></div>";
        return content;
    },
    createLoginModalContent : function () {
        var username = this.model.getItems().name;
        var content = "<div class='modalHeader'><h5>Thank you</h5></div><div class='modalContent'><i class='fa fa-spinner fa-3x fa-spin'></i><p>We are logging you in</p></div>";
        return content;
    }
};