$(function(){
    $("#nav-menu-back").show();
    $("#nav-menu-logout").hide();
});

document.getElementById("page-title").textContent = KEYTAP.contacts.getChatContact().name;

document.getElementById("submitNewMessage").addEventListener("click", function(e){
    e.preventDefault();
    submitNewMessage();
});

messengerService.getLastMessagesFor(KEYTAP.contacts.getChatContact(), 0, 100).then(function (messages) {
    var messagesNode = document.getElementById('messages');
    var messagesHtml = "";
    for (var i=0; i < messages.length; ++i) {
        messagesHtml += createMessageNode(messages[i], KEYTAP.contacts.getChatContact().name);
    }
    if(messagesHtml != ""){
        messagesNode.innerHTML = messagesHtml;
    }
    window.scrollTo(0,document.body.scrollHeight);
}).catch(function (e) {
    console.error("Unable to fetch messages: " + e);
});



function createMessageNode(message, contactName){
    if(contactName == "me"){
        fromClass = "message-left";
    }
    else{
        fromClass = "message-right";
    }
    var node = "<li id='message_" + message.id + "' class='" + fromClass + "'>";
    node += createAvatar(contactName);

    var msgDiv = document.createElement('div');
    msgDiv.setAttribute('class', 'message');

    var msgP = document.createElement('p');
    msgP.textContent = message.message;

    var timeSpan = document.createElement('span');

    if(message.timestamp != null){
        timeSpan.textContent = timeSince(convertToDate(message.timestamp)) + " ago";
    }else{
        timeSpan.textContent = message.timestamp;
    }
    timeSpan.className = "timespan";

    msgDiv.innerHTML = msgP.outerHTML + timeSpan.outerHTML;

    node += msgDiv.outerHTML + "</li>";

    return node;
}
