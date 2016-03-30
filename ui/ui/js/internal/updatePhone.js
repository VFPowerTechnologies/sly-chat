$(document).ready(function () {
    document.getElementById("page-title").textContent = "Phone Update";

    var height = window.innerHeight - 52;
    $("#content").css("min-height", height + "px");

    $("#backToSmsVerification").click(function (e) {
        e.preventDefault();
        KEYTAP.navigationController.loadPage("smsVerification.html");
    });

    window.telephonyService.getDevicePhoneNumber().then(function (maybePhoneNumber) {
        if (maybePhoneNumber !== null) {
            $('label[for="phone"]').addClass("active");
            $("#phone").val(maybePhoneNumber);
        }
    });

    //Set default country based on geo location.
    setTimeout(function() {
        var data = $("#hiddenPhoneInput").intlTelInput("getSelectedCountryData");
        $("#countrySelect").val(data.iso2);
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
                }, 100);
            });
        },
        utilsScript: "js/external-lib/utils.js"
    });

});