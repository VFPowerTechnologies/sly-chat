if(typeof $ == "undefined"){
    window.location.href = "index.html";
}

$(document).ready(function () {
    $("#backToSmsVerification").click(function (e) {
        e.preventDefault();
        KEYTAP.navigationController.loadPage("smsVerification.html", false);
    });
});