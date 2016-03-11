$(function(){
    document.getElementById("page-title").textContent = "Register";
    document.getElementById("name").focus();

    var height = window.innerHeight - 52;
    $("#content").css("min-height", height + "px");

    $("#networkStatus").addClass("hidden");

    window.telephonyService.getDevicePhoneNumber().then(function (maybePhoneNumber) {
        if (maybePhoneNumber !== null) {
            $('label[for="phone"]').addClass("active");
            $("#phone").val(maybePhoneNumber);
        }
    });
});
