if(typeof $ == "undefined"){
    window.location.href = "index.html";
}

$(document).ready(function () {
    $("ul.tabs").tabs();

    KEYTAP.contactController.init();

    setInterval( function () {
        $(".timeago").timeago();
    }, 60000);
});