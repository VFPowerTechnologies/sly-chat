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
//        messageDiv.getElementsByClassName("timespan")[0].innerHTML = messageInfo.message.timestamp;// + '<i class="mdi mdi-checkbox-marked-circle pull-right"></i>';
        messageDiv.getElementsByClassName("timespan")[0].innerHTML = timeSince(convertToDate(messageInfo.message.timestamp)) + " ago";// + '<i class="mdi mdi-checkbox-marked-circle pull-right"></i>';
    }
});

messengerService.addNewMessageListener(function (messageInfo) {
        if(document.getElementById("page-title") != null && document.getElementById("page-title").textContent == messageInfo.contact.name){
            var messagesDiv = document.getElementById("messages");
            if(messagesDiv != null){
                var newMessageNode = createMessageNode(messageInfo.message, messageInfo.contact.name);
                messagesDiv.innerHTML += newMessageNode;
                window.scrollTo(0,document.body.scrollHeight);
            }else{
                newMessageNotification(messageInfo);
            }
        }
        else{
            newMessageNotification(messageInfo);
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

function createContactBlock(contact){
    var contactBlock = "<a href='#' class='contact-link' id='contact_" + contact.id + "'><div class='contact'>";
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

function convertToDate(timeStamp){
    var bits = timeStamp.split(/\D/);
    return new Date(bits[0], --bits[1], bits[2], bits[3], bits[4], bits[5]);
}

function timeSince(date) {

    var seconds = Math.floor((new Date() - date) / 1000);

    var interval = Math.floor(seconds / 31536000);

    if (interval > 1) {
        return interval + " years";
    }
    interval = Math.floor(seconds / 2592000);
    if (interval > 1) {
        return interval + " months";
    }
    interval = Math.floor(seconds / 86400);
    if (interval > 1) {
        return interval + " days";
    }
    interval = Math.floor(seconds / 3600);
    if (interval > 1) {
        return interval + " hours";
    }
    interval = Math.floor(seconds / 60);
    if (interval > 1) {
        return interval + " minutes";
    }
    return Math.floor(seconds) + " seconds";
}

function newMessageNotification(messageInfo){
    notif = document.createElement("li");
    notif.innerHTML = "<a href='#'>New message received from " + messageInfo.contact.name + "</a>";
    var container = document.getElementById("notificationContainer");
    container.appendChild(notif);

    notif.onclick = function(e){
         e.preventDefault();
         KEYTAP.contacts.setChatContact(messageInfo.contact.id);
         console.log(KEYTAP.contacts.getChatContact.name);
         smoothState.load("chat.html");
    }.bind(messageInfo);
}

//Send a fake message to test receive message listener.
$(document).on("click", '#sendFakeMessage', function(e){
    e.preventDefault();

    develService.receiveFakeMessage(KEYTAP.contacts.getContact(0), "Fake").catch(function (e) {
        console.log('receiveFakeMessage failed: ' + e);
    });
});
