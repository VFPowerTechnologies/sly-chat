$(function(){
    $("#nav-menu-back").show();
    $("#nav-menu-logout").hide();
});

document.getElementById("name").focus();

var newContactBtn = document.getElementById("newContactBtn");

document.getElementById("page-title").textContent = "Add Contact";

newContactBtn.addEventListener("click", function(e){
    e.preventDefault();
    addContact();
});

function addContact(){
    var validation = $("#addContactForm").parsley({
        errorClass: "invalid",
        focus: 'none',
        errorsWrapper: '<div class="pull-right parsley-errors-list" style="color: red;"></div>',
        errorTemplate: '<p></p>'
    });
    var isValid = validation.validate();

    if(isValid == true){
        var name = document.getElementById("name").value;
        var phone = document.getElementById("phone").value;
        var email = document.getElementById("email").value;

        contactService.addNewContact({
            name: name,
            email: email,
            phoneNumber: phone
        }).then(function () {
            KEYTAP.contacts.setContacts([]);
            loadPage("contacts.html");
        }).catch(function (e) {
            console.error('Unable to add contact: ' + e);
        });
    }

}