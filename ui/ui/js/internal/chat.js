if(typeof $ == "undefined"){
    window.location.href = "index.html";
}

$(function(){
    KEYTAP.chatController.init();
    var currentContact = KEYTAP.contactController.getCurrentContact();

    $("#currentPageChatId").html(currentContact.id);
    $("#chat-page-title").html("<i class='fa fa-lock' style='color: green; margin-right: 5px;'></i>" + currentContact.name);

    setInterval( function () {
        $(".timeago").timeago();
    }, 60000);
});
