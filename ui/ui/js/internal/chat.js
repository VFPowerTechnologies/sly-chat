$(function(){
    $("#nav-menu-back").show();
    $("#nav-menu-logout").hide();

    var currentContact = KEYTAP.contactController.getCurrentContact();

    $("#page-title").html(currentContact.name);

    $("#chatContent").after("<div id='currentPageChatEmail' class='hidden'>" + currentContact.email + "</div>");
});
