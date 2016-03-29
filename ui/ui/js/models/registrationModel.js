var RegistrationModel = function () {};

RegistrationModel.prototype = {
    setItems : function (items) {
        this.items = items;
    },
    validate : function () {
        var validation = $("#registerForm").parsley({
            errorClass: "invalid",
            focus: 'none',
            errorsWrapper: '<div class="pull-right parsley-errors-list" style="color: red;"></div>',
            errorTemplate: '<p></p>'
        });

        var hiddenPhoneInput = $("#hiddenPhoneInput");
        var phone = $("#phone");

        var phoneValue = phone.val();

        if(phoneValue != "" && hiddenPhoneInput.intlTelInput("isValidNumber") == false) {
            phone.addClass("invalid");
            var errorDiv = phone.next("div");
            if(!errorDiv.hasClass("invalidPhone"))
                phone.after("<div class='pull-right invalidPhone filled' style='color: red;'><p>Phone Number seems invalid.</p></div>");
        }else if(phoneValue == "") {
            $(".invalidPhone").remove();
        }

        var isValid = validation.validate();

        if(isValid == true){
            return true;
        }
        else{
            return false;
        }
    },
    getItems : function () {
        return this.items;
    }
};