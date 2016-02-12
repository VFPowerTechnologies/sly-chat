var RegistrationController = function (model) {
    this.model = model;
}

RegistrationController.prototype = {
    init : function () {
        $("#registerBtn").click(function(e){
            e.preventDefault();

            $("#registerBtn").prop("disabled", true);

            this.model.setItems({
                "name" : $("#name").val(),
                "email" : $("#email").val(),
                "phoneNumber" : $("#phone").val(),
                "password" : $("#password").val()
            });

            if(this.model.validate() == true){
                this.register();
                $("#registerStatusModal").openModal({
                    dismissible: false
                });
            }
            else {
                $("#registerBtn").prop("disabled", false);
            }
        }.bind(this));

        $("#signInBtn").click(function (e) {
            e.preventDefault();
            KEYTAP.navigationController.loadPage("login.html");
        });

        $(document).on("click", "#successLoginBtn", function (e) {
            e.preventDefault();
            $("#registerStatusModal").closeModal();
            var info = this.model.getItems();
            KEYTAP.loginController.setInfo(info.email, info.password);
            KEYTAP.loginController.login();
        }.bind(this));
    },
    register : function () {
        registrationService.addListener(function (registrationStatus) {
            $("#registrationStatusUpdate").html(registrationStatus + "...");
        });
        registrationService.doRegistration(this.model.getItems()).then(function (result) {
            if(result.successful == true) {
                $("#registerStatusModal").html(this.createRegistrationSuccessContent());
            }
            else{
                this.displayRegistrationError(result);
                $("#registerBtn").prop("disabled", false);
            }
        }.bind(this)).catch(function(e) {
            document.getElementById("register-error").innerHTML = "<li>Registration failed</li>";
            $("#registerBtn").prop("disabled", false);
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
    }
}