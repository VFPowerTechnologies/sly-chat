if(typeof $ == "undefined"){
    window.location.href = "index.html";
}

$(function(){
    KEYTAP.chatController.init();
    var currentContact = KEYTAP.contactController.getCurrentContact();

    $("#currentPageChatEmail").html(currentContact.email);
    $("#chat-page-title").html(currentContact.name);
});
