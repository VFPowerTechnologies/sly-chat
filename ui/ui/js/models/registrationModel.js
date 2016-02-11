var RegistrationModel = function () {}

RegistrationModel.prototype = {
    setItems : function (items) {
        this.items = items;
        this.name = items.name;
        this.phone = items.phone;
        this.email = items.email;
        this.password = items.password;
    },
    validate : function () {
        var validation = $("#registerForm").parsley({
            errorClass: "invalid",
            focus: 'none',
            errorsWrapper: '<div class="pull-right parsley-errors-list" style="color: red;"></div>',
            errorTemplate: '<p></p>'
        });

        var isValid = validation.validate();

        if(isValid == true){
            return true;
        }
        else{
            return false;
        }
    },
    register : function () {
        registrationService.doRegistration({
            "name": this.name,
            "email": this.email,
            "phoneNumber": this.phone,
            "password": this.password
        }).then(function (v) {
            loadPage("contacts.html");
        }).catch(function(e) {
            document.getElementById("register-error").innerHTML = "<li>Email is taken.</li>";
            console.log("registration failed: " + e);
        });
    }
}