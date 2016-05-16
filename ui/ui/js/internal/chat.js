if(typeof $ == "undefined"){
    window.location.href = "index.html";
}

$(function(){
    KEYTAP.chatController.init();
    var currentContact = KEYTAP.contactController.getCurrentContact();

    $("#currentPageChatId").html(currentContact.id);
    $("#chat-page-title").html(currentContact.name + "<i class='fa fa-lock' style='color: green; margin-left: 5px;'></i>");
});
