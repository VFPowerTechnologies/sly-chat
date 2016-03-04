$(function(){
    $("#nav-menu-back").show();
    $("#nav-menu-logout").hide();

    document.getElementById("username").focus();
    document.getElementById("page-title").textContent = "Add Contact";

    KEYTAP.contactController.newContactEvent();
});