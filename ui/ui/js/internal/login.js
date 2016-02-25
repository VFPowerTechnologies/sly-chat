$(function(){
    document.getElementById("page-title").textContent = "Login";
    document.getElementById("login").focus();

    KEYTAP.loginController.init();

    var height = window.innerHeight - 52;
    $("#content").css("min-height", height + "px");
});