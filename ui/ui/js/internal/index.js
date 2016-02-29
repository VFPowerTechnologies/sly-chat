$(document).ready(function(){
    var height = window.innerHeight - 56;
    $("#main").css("height", height + "px");
    var loggedIn = false;
    if(loggedIn){
        KEYTAP.navigationController.loadPage("contacts.html");
    }else{
        KEYTAP.navigationController.loadPage("login.html");
    }

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