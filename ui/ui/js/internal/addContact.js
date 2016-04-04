$(function(){
    document.getElementById("username").focus();

    KEYTAP.contactController.newContactEvent();

    var height = window.innerHeight - 52;
    $("#content").css("min-height", height + "px");
});