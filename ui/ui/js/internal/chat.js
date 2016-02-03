document.getElementById("submitNewMessage").addEventListener("click", function(e){
    e.preventDefault();
    submitNewMessage();
});

contactService.getContacts().then(function (contacts) {
    var contactId = getParameterByName("contactId");
    document.getElementById("contactNameTitle").innerHTML = contacts[contactId].name;

    messengerService.getLastMessagesFor(contacts[contactId], 0, 100).then(function (messages) {
        var messagesNode = document.getElementById('messages');
        var messagesHtml = "";
        for (var i=0; i < messages.length; ++i) {
            messagesHtml += createMessageNode(messages[i].message, messages[i].contactName, messages[i].timestamp);
        }
        if(messagesHtml != ""){
            messagesNode.innerHTML = messagesHtml;
        }
        window.scrollTo(0,document.body.scrollHeight);
    }).catch(function (e) {
        console.error("Unable to fetch messages: " + e);
    });
}).catch(function (e) {
    console.error('Unable to fetch contacts: ' + e);
});

function getParameterByName(name) {
    name = name.replace(/[\[]/, "\\[").replace(/[\]]/, "\\]");
    var regex = new RegExp("[\\?&]" + name + "=([^&#]*)"),

    results = regex.exec(location.search);

    return results === null ? "" : decodeURIComponent(results[1].replace(/\+/g, " "));
}

function createMessageNode(message, contactName, timestamp){
    if(contactName == "me"){
        fromClass = "message-left";
    }
    else{
        fromClass = "message-right";
    }
    var node = "<li class='" + fromClass + "'>";
    node += createAvatar(contactName);

    var msgDiv = document.createElement('div');
    msgDiv.setAttribute('class', 'message');

    var msgP = document.createElement('p');
    msgP.textContent = message;

    var timeSpan = document.createElement('span');
    timeSpan.textContent = timestamp;

    msgDiv.innerHTML = msgP.outerHTML + timeSpan.outerHTML;

    node += msgDiv.outerHTML + "</li>";

    return node;
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
        contactService.getContacts().then(function (contacts) {
            var messageInput = document.getElementById('newMessageInput');
            var message = messageInput.value;
            var contactId = getParameterByName("contactId");

            var messagesNode = document.getElementById('messages');
            messengerService.sendMessageTo(contacts[contactId], message).then(function () {
                messageInput.value = "";
                messagesNode.innerHTML += createMessageNode(message, "me", 'YYYY-MM-DD HH:MM:SS');
                window.scrollTo(0,document.body.scrollHeight);
            }).catch(function (e) {
                console.log(e);
            });
        }).catch(function (e) {
            console.error('Unable to fetch contacts: ' + e);
        });
    }
}
