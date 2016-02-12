$(document).ready(function(){
    var loggedIn = false;
    if(loggedIn){
        KEYTAP.navigationController.loadPage("contacts.html");
    }else{
        KEYTAP.navigationController.loadPage("login.html");
    }
});