$(document).ready(function(){
    var loggedIn = false;
    if(loggedIn){
        loadPage("contacts.html");
    }else{
        loadPage("login.html");
    }
});