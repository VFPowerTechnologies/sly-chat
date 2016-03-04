$(document).ready(function(){
    var height = window.innerHeight - 56;
    $("#main").css("height", height + "px");

    window.configService.getStartupInfo().then(function (startupInfo) {
        KEYTAP.navigationController.loadPage("login.html");
        if(startupInfo != null && startupInfo.lastLoggedInAccount !== null && startupInfo.savedAccountPassword !== null) {
            KEYTAP.loginController.model.setItems({
                "login": startupInfo.lastLoggedInAccount,
                "password": startupInfo.savedAccountPassword
            });
            KEYTAP.loginController.login();
        }
    }).catch(function (e) {
        KEYTAP.exceptionController.displayDebugMessage(e);
        console.log(e);
    });

    $(window).resize(function () {
        resizeWindow();
    });

});

function resizeWindow() {
    var height = window.innerHeight - 56;
    $("#main").css("height", height + "px");

    if ($("#chatContent").length) {
        document.getElementById("chatContent").contentWindow.scrollTo(0, 9999999);
    }
}