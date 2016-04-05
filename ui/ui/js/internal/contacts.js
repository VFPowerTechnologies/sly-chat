if(typeof $ == "undefined"){
    window.location.href = "index.html";
}

$(function(){
    if(typeof KEYTAP.firstContactLoad == "undefined"){
        $("ul.tabs").tabs();
        KEYTAP.firstContactLoad = false;
    }
});