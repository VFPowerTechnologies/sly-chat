var KEYTAP = KEYTAP || {};
window.registrationService = new RegistrationService();
window.platformInfoService = new PlatformInfoService();
window.messengerService = new MessengerService();
window.loginService = new LoginService();
window.contactService = new ContactsService();
window.historyService = new HistoryService();
window.develService = new DevelService();

KEYTAP.contacts = new Contacts();

// SmoothState, makes only the main div reload on page load.
$(function(){
    'use strict';
    var duration_CONSTANT = 250;
    var options = {
        prefetch: true,
        cacheLength: 20,
        onStart: {
            duration: duration_CONSTANT,
            render: function ($container) {
                $container.addClass('is-exiting');
                smoothState.restartCSSAnimations();
            }
        },

        onReady: {
            duration: 0,
            render: function ($container, $newContent) {
                $container.removeClass('is-exiting');
                $container.html($newContent);
            }
        }
    };

    window.smoothState = $('#main').smoothState(options).data('smoothState');
});

// Message update listener
messengerService.addMessageStatusUpdateListener(function (messageInfo) {
    messageDiv = document.getElementById("message_" + messageInfo.message.id);

    if(messageDiv != null && messageInfo.message.sent == true){
        messageDiv.getElementsByClassName("timespan")[0].innerHTML = messageInfo.message.timestamp;// + '<i class="mdi mdi-checkbox-marked-circle pull-right"></i>';
    }
});

// New message listener
messengerService.addNewMessageListener(function (messageInfo) {
        if(document.getElementById("page-title") != null && document.getElementById("page-title").textContent == messageInfo.contact.name){
            var messagesDiv = document.getElementById("messages");
            if(messagesDiv != null){
                var newMessageNode = createMessageNode(messageInfo.message, messageInfo.contact.name);
                messagesDiv.innerHTML += newMessageNode;
                window.scrollTo(0,document.body.scrollHeight);
            }
        }
});

// Back button listener
navigationService = {
    goBack: function () {
        goBack();
    }
};

// Go back function
function goBack(){
    historyService.pop().then(function(url){
        smoothState.load(url);
    }).catch(function (e){
        console.log(e);
    })
}

// Push the current location to the java side
function pushHistory(){
    historyService.push(window.location.href).then(function(){

    }).catch(function(e){
        console.log(e);
    });
}

// Loads a page using smoothState and push the current url to history
function loadPage(url){
    pushHistory();
    smoothState.load(url);
}

// UI function, creates contact
function createContactBlock(contact, status){
    if(status.lastMessage == null){
        lastMessage = "";
        timestamp = ""
    }
    else if(lastMessage.message.length > 40){
        lastMessage = status.lastmessage.message.substring(0, 40) + "...";
        timestamp = status.lastMessage.timestamp;
    }
    else{
        lastMessage = status.lastMessage.message;
        timestamp = status.lastMessage.timestamp;
    }

    if(status.online == true){
        availableClass = "dot green";
    }
    else{
        availableClass = "dot red";
    }

    if(status.unreadMessageCount > 0){
        newMessageClass = "new-messages";
    }
    else{
        newMessageClass = "";
    }

    var contactBlock = "<div class='contact-link " + newMessageClass + "' id='contact_" + contact.id + "'><div class='contact'>";
    contactBlock += createAvatar(contact.name);
    contactBlock += "<span class='" + availableClass + "'></span>";
    contactBlock += "<p>" + contact.name + "</p>";
    contactBlock += "<span class='last_message'>" + lastMessage + "</span>";
    contactBlock += "<span class='time'>" + timestamp + "</span>";
    contactBlock += "</div></div>";

    return contactBlock;
}

// Create user avatar from first letter of name
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

//Send a fake message to test receive message listener.
$(document).on("click", '#sendFakeMessage', function(e){
    e.preventDefault();

    develService.receiveFakeMessage(KEYTAP.contacts.getContact(0), "Fake").catch(function (e) {
        console.log('receiveFakeMessage failed: ' + e);
    });
});
