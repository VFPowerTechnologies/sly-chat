if(typeof $ == "undefined"){
    window.location.href = "index.html";
}

$(function(){
    KEYTAP.chatController.init();
    var currentContact = KEYTAP.contactController.getCurrentContact();

    $("#chatContent").after("<div id='currentPageChatEmail' class='hidden'>" + currentContact.email + "</div>");
    $("#page-title").html(currentContact.name);
});
