$(function(){
    document.getElementById("page-title").textContent = "Register";
    document.getElementById("name").focus();

    KEYTAP.registrationController.init();

    var height = window.innerHeight - 52;
    $("#content").css("min-height", height + "px");
});
