$(document).ready(function(){
    window.firstLoad = true;

    var height = window.innerHeight - 56;
    $("#main").css("height", height + "px");

    $(window).resize(function () {
        resizeWindow();
    });

    stateService.getState().then(function (state) {
        if(state != null && typeof state.currentPage != "undefined" && state.currentPage != null) {
            if(state.currentPage.indexOf("login.html") <= -1 && state.currentPage.indexOf("register.html") <= -1) {
                $(".menu-hidden").show();
            }
            if(state.currentPage.indexOf("chat.html") > -1) {
                if (typeof state.currentContact != "undefined" && state.currentContact != null) {
                    KEYTAP.contactController.loadContactPage(state.currentContact.id, false);
                }
            }else {
                KEYTAP.navigationController.loadPage(state.currentPage, false);
            }
        }
    }).catch(function (e) {
        KEYTAP.exceptionController.displayDebugMessage(e);
        console.log(e);
    });
});
