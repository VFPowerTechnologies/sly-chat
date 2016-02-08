var KEYTAP = KEYTAP || {};
window.registrationService = new RegistrationService();
window.platformInfoService = new PlatformInfoService();
window.messengerService = new MessengerService();
window.loginService = new LoginService();
window.contactService = new ContactsService();
window.historyService = new HistoryService();
window.develService = new DevelService();

KEYTAP.contacts = new Contacts();

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

navigationService = {
    goBack: function () {
        goBack();
    }
};

messengerService.addMessageStatusUpdateListener(function (messageInfo) {
    messageDiv = document.getElementById("message_" + messageInfo.message.id);

    if(messageDiv != null && messageInfo.message.sent == true){
        messageDiv.getElementsByClassName("timespan")[0].innerHTML = messageInfo.message.timestamp;// + '<i class="mdi mdi-checkbox-marked-circle pull-right"></i>';
    }
});

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

function goBack(){
    historyService.pop().then(function(url){
        smoothState.load(url);
    }).catch(function (e){
        console.log(e);
    })
}

function pushHistory(){
    historyService.push(window.location.href).then(function(){

    }).catch(function(e){
        console.log(e);
    });
}

function loadPage(url){
    pushHistory();
    smoothState.load(url);
}

function createContactBlock(contact, lastMessage){
    var contactBlock = "<a href='#' class='contact-link' id='contact_" + contact.id + "'><div class='contact'>";
    contactBlock += createAvatar(contact.name);
    contactBlock += "<span class='dot green'></span>";
    contactBlock += "<p>" + contact.name + "</p>";
    contactBlock += "<span class='last_message'>" + lastMessage.message.substring(0, 40) + "...</span>";
    contactBlock += "<span class='time'>" + lastMessage.timestamp + "</span>";
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

function submitNewMessage(){
    if(document.getElementById('newMessageInput').value != ""){
        var messageInput = document.getElementById('newMessageInput');
        var message = messageInput.value;

        var messagesNode = document.getElementById('messages');
        messengerService.sendMessageTo(KEYTAP.contacts.getChatContact(), message).then(function (messageDetails) {
            messageInput.value = "";
            messagesNode.innerHTML += createMessageNode(messageDetails, "me");
            window.scrollTo(0,document.body.scrollHeight);
        }).catch(function (e) {
            console.log(e);
        });
    }
}

//Send a fake message to test receive message listener.
$(document).on("click", '#sendFakeMessage', function(e){
    e.preventDefault();

    develService.receiveFakeMessage(KEYTAP.contacts.getContact(0), "Fake").catch(function (e) {
        console.log('receiveFakeMessage failed: ' + e);
    });
});
