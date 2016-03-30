$(document).ready(function () {
    document.getElementById("page-title").textContent = "Phone Update";

    var height = window.innerHeight - 52;
    $("#content").css("min-height", height + "px");

    $("#backToSmsVerification").click(function (e) {
        e.preventDefault();
        KEYTAP.navigationController.loadPage("smsVerification.html");
    });
});