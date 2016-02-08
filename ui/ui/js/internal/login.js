document.getElementById('loginBtn').addEventListener("click", function(e){
    e.preventDefault();
    login();
});

document.getElementById("page-title").textContent = "Login";

document.getElementById("login").focus();

function login(){
    var validation = $("#loginForm").parsley({
        errorClass: "invalid",
        focus: 'none',
        errorsWrapper: '<div class="pull-right parsley-errors-list" style="color: red;"></div>',
        errorTemplate: '<p></p>'
    });
    var isValid = validation.validate();

    if(isValid == true){
        var login = document.getElementById("login").value;
        var password = document.getElementById("login-psw").value;

        loginService.login(login, password).then(function () {
            $(".menu-hidden").show();
            loadPage('contacts.html');
        }).catch(function (e) {
            document.getElementById("login-error").innerHTML = "<li>Wrong email or password</li>";
        });
    }
}