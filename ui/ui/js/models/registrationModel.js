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