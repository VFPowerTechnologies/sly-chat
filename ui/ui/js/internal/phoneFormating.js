function updatePhoneWithIntl () {
    telephonyService.getDevicePhoneNumber().then(function (maybePhoneNumber) {
        if (maybePhoneNumber !== null) {
            $("#phone").val(maybePhoneNumber);
        }
    });

    //Get the country data.
    var countryData = $.fn.intlTelInput.getCountryData();

    $.each(countryData, function(i, country) {
        $("#countrySelect").append("<option value='" + country.iso2 + "'>" + country.name + " +" + country.dialCode +  "</option>");
    });

    infoService.getGeoLocation().then(function (country) {
        if(country !== null) {
            setTimeout(function () {
                var data = getCountryData(country);
                if(data != null) {
                    $("#countrySelect").val(data.iso2);
                    setPhoneExt(data.dialCode);
                }
            }, 100);
        }
    }).catch(function (e) {
        console.log(e);
    });
}

function getCountryData(iso2) {
    return $.fn.intlTelInput.getSpecifiedCountryData(iso2.toLowerCase());
}

function getFormatedPhoneNumber(number, iso2) {
    var phoneNumber = '';

    if (window.intlTelInputUtils)
        phoneNumber = intlTelInputUtils.formatNumber(number, iso2);

    if (phoneNumber.charAt(0) === "+")
        return phoneNumber.substr(1);
    else
        return phoneNumber;
}

function validatePhone(phone, iso2) {
    if(typeof window.intlTelInputUtils !== "undefined")
        return intlTelInputUtils.isValidNumber(phone, iso2);
    else
        return false;
}

function setPhoneExt(dialCode) {
    if(typeof dialCode != "undefined") {
        var extension = $$("#phoneIntlExt");
        $$("#phoneInputIcon").hide();

        extension.html("+" + dialCode);
        extension.show();
    }
}