$(function(){
    if(typeof KEYTAP.firstContactLoad == "undefined"){
        $("ul.tabs").tabs();
        KEYTAP.firstContactLoad = false;
    }
});