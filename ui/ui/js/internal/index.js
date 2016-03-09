$(document).ready(function(){
    var height = window.innerHeight - 56;
    $("#main").css("height", height + "px");

    $(window).resize(function () {
        resizeWindow();
    });

    stateService.getState().then(function (state) {
        if(state != null && typeof state.currentPage != "undefined" && state.currentPage != null) {
            if(state.currentPage.indexOf("chat.html") > -1) {
                if (typeof state.currentContact != "undefined" && state.currentContact != null) {
                    KEYTAP.contactController.model.fetchConversationForChat(state.currentContact.email);
                }
            }else {
                KEYTAP.navigationController.smoothStateLoad(state.currentPage);
            }
        }else {
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
        }
    }).catch(function (e) {
        KEYTAP.exceptionController.displayDebugMessage(e);
        console.log(e);
    });
});

function resizeWindow() {
    var height = window.innerHeight - 56;
    $("#main").css("height", height + "px");

    if ($("#chatContent").length) {
        document.getElementById("chatContent").contentWindow.scrollTo(0, 9999999);
    }
}