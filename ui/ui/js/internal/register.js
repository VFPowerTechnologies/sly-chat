$(function(){
    document.getElementById("page-title").textContent = "Register";
    document.getElementById("name").focus();

    var model = new RegistrationModel();
    var controller = new RegistrationController(model);
    controller.init();
});
