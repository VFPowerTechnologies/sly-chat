if(typeof $ == "undefined"){
    window.location.href = "index.html";
}

$(function(){
    document.getElementById("username").focus();

    KEYTAP.contactController.newContactEvent();
});