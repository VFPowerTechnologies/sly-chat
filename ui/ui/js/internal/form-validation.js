slychat.validateForm = function (form) {
    var valid = true;
    $(":input[required]", form).each(function(){
        if (this.value.trim() === '') {
            valid = false;
            $(this).addClass("invalid-required");
        }
        else {
            $(this).removeClass("invalid-required");
        }
    });

    return valid;
};