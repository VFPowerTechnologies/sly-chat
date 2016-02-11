$(function(){
    $("#nav-menu-back").show();
    $("#nav-menu-logout").hide();

    document.getElementById("name").focus();
    document.getElementById("page-title").textContent = "Add Contact";

    KEYTAP.contactController.newContactEvent();
});