$(function(){
    document.getElementById("page-title").textContent = "Login";
    document.getElementById("login").focus();

    var model = new LoginModel();
    var controller = new LoginController(model);
    controller.init();
});