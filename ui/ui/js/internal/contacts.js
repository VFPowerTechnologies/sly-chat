$(function(){
    $("#nav-menu-back").hide();
    $("#nav-menu-logout").show();

    document.getElementById("page-title").textContent = "Contacts";

    KEYTAP.contactController.init();
});