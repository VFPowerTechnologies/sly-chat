if(typeof $ == "undefined"){
    window.location.href = "index.html";
}

$(document).ready(function () {
    $("ul.tabs").tabs();

    KEYTAP.contactController.init();
    KEYTAP.recentChatController.init();
});