if(typeof $ == "undefined"){
    window.location.href = "index.html";
}

var height = window.innerHeight - 56;
$("#content").css("min-height", height + "px");

$("ul.tabs").tabs();

KEYTAP.profileController.init();