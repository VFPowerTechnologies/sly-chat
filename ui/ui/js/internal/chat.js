$(function(){
    var currentContact = KEYTAP.contactController.getCurrentContact();

    $("#chatContent").after("<div id='currentPageChatEmail' class='hidden'>" + currentContact.email + "</div>");
});
