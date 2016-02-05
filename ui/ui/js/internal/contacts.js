$(function(){
    $("#nav-menu-back").hide();
    $("#nav-menu-logout").show();
});


KEYTAP.contacts.displayContacts();

document.getElementById("page-title").textContent = "Contacts";

document.getElementById("logoutBtn").addEventListener("click", function(e){
    e.preventDefault();

//    loginService.logout().then(function () {
//        loadPage('login.html');
//    }).catch(function (e) {
//        console.log(e);
//        console.error('Login failed');
//    });

    //temp: just send user back to login
    loadPage("login.html");
});

