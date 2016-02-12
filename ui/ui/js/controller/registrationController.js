var RegistrationController = function (model) {
    this.model = model;
}

RegistrationController.prototype = {
    init : function () {
        $("#registerBtn").click(function(e){
            e.preventDefault();
            this.model.setItems({
                "name" : $("#name").val(),
                "email" : $("#email").val(),
                "phoneNumber" : $("#phone").val(),
                "password" : $("#password").val()
            });

            if(this.model.validate() == true){
                this.register();
            }
        }.bind(this));
    },
    register : function () {
        registrationService.addListener(function (registrationStatus) {
            console.log("Registration status : " + registrationStatus);
        });
        registrationService.doRegistration(this.model.getItems()).then(function (result) {
            if(result.successful == true) {
                $(".menu-hidden").show();
                loadPage("contacts.html");
            }
            else{
                this.displayRegistrationError(result);
            }
        }.bind(this)).catch(function(e) {
            document.getElementById("register-error").innerHTML = "<li>Registration failed</li>";
        });
    },
    displayRegistrationError : function (result) {
        document.getElementById("register-error").innerHTML = "<li>" + result.errorMessage + "</li>";
        console.log("displaying error");
    }
}