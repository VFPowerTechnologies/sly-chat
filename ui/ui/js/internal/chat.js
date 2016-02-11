$(function(){
    $("#nav-menu-back").show();
    $("#nav-menu-logout").hide();

    document.getElementById("page-title").textContent = KEYTAP.contactController.getCurrentContact().name;

    KEYTAP.chatController.init();
});