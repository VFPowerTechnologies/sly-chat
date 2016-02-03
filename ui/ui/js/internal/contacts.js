$(document).ready(function(){
    var flashMessage = getParameterByName("flash-message");

    if(flashMessage == "contactAdded"){
        $(".navbar-fixed-top").after("<div id='flashMessage' style='text-align: center; background-color: green; color: white;'>Contact added successfully</div>");

        setTimeout(function(){
            $("#flashMessage").remove();
        }, 3000);
    }
});

contactService.getContacts().then(function (contacts) {
    var contactsBlock = "";
    contacts.forEach(function (contactDetails) {
        contactsBlock += createContactBlock(contactDetails);
    });

    if(contactsBlock != ""){
        var contactList = document.getElementById("contactList");
        contactList.innerHTML = contactsBlock;
    }

}).catch(function (e) {
    console.error('Unable to fetch contacts: ' + e);
});


function createContactBlock(contact){
    var contactBlock = "<a href='#' class='contact-link' id='contact_" + contact.id + "' onclick=\"loadPage('chat.html?contactId=" + contact.id + "');\"><div class='contact'>";
    contactBlock += createAvatar(contact.name);
    contactBlock += "<span class='dot green'></span>";
    contactBlock += "<p>" + contact.name + "</p>";
    contactBlock += "<span class='last_message'>last message...</span>";
    contactBlock += "<span class='time'>1 min ago</span>";
    contactBlock += "</div></a>";


    return contactBlock;
}

function createAvatar(name){
    var img = new Image();
    img.setAttribute('data-name', name);
    img.setAttribute('class', 'avatarCircle');

    $(img).initial({
        textColor: '#000000',
        seed: 0
    });

    return img.outerHTML;
}

function getParameterByName(name) {
    name = name.replace(/[\[]/, "\\[").replace(/[\]]/, "\\]");
    var regex = new RegExp("[\\?&]" + name + "=([^&#]*)"),

    results = regex.exec(location.search);

    return results === null ? "" : decodeURIComponent(results[1].replace(/\+/g, " "));
}