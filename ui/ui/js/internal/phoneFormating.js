$(document).ready(function () {
    window.telephonyService.getDevicePhoneNumber().then(function (maybePhoneNumber) {
        if (maybePhoneNumber !== null) {
            $('label[for="phone"]').addClass("active");
            $("#phone").val(maybePhoneNumber);
        }
    });

    //Set default country based on geo location.
    setTimeout(function() {
        var data = $("#hiddenPhoneInput").intlTelInput("getSelectedCountryData");
        if(!$.isEmptyObject(data) && typeof data.iso2 != "undefined") {
            $("#countrySelect").val(data.iso2);
            KEYTAP.registrationController.setPhoneExt(data.dialCode);
        }
    }, 100);

    //Get the country data.
    var countryData = $.fn.intlTelInput.getCountryData();

    $.each(countryData, function(i, country) {
        $("#countrySelect").append("<option value='" + country.iso2 + "'>" + country.name + " +" + country.dialCode +  "</option>");
    });

    $("#hiddenPhoneInput").intlTelInput({
        initialCountry: "auto",
        geoIpLookup: function(callback) {
            $.get('http://ipinfo.io', function() {}, "jsonp").always(function(resp) {
                var countryCode = (resp && resp.country) ? resp.country : "";
                callback(countryCode);

                setTimeout(function() {
                    var data = $("#hiddenPhoneInput").intlTelInput("getSelectedCountryData");
                    $("#countrySelect").val(data.iso2);
                    KEYTAP.registrationController.setPhoneExt(data.dialCode);
                }, 100);
            });
        },
        utilsScript: "js/external-lib/utils.js"
    });
});

function validatePhone() {
    var phoneInput = $("#phone");
    var hiddenPhoneInput = $("#hiddenPhoneInput");

    var phoneValue = phoneInput.val();

    hiddenPhoneInput.val(phoneValue);

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
}