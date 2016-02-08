document.getElementById("registerBtn").addEventListener("click", function(e){
    e.preventDefault();
    register();
});

document.getElementById("page-title").textContent = "Register";

document.getElementById("name").focus();

function register(){
    var validation = $("#registerForm").parsley({
        errorClass: "invalid",
        focus: 'none',
        errorsWrapper: '<div class="pull-right parsley-errors-list" style="color: red;"></div>',
        errorTemplate: '<p></p>'
    });

    var isValid = validation.validate();

    if(isValid == true){
        var name = document.getElementById("name").value;
        var email = document.getElementById("email").value;
        var phone = document.getElementById("phone").value;
        var password = document.getElementById("password").value;

        var registrationPromise = registrationService.doRegistration({
            "name": name,
            "email": email,
            "phoneNumber": phone,
            "password": password
        });

        registrationPromise.then(function (v) {
            loadPage("contacts.html");
        }, function (e) {
            var msg = 'Registration error: ' + e;
            console.error(msg);
            updateProgress(msg);
        });

        registrationPromise.catch(function(e) {
            document.getElementById("register-error").innerHTML = "<li>Email is taken.</li>";
            console.log("registration failed: " + e);
        });
    }
}
