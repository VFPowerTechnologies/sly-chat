window.registrationService = new RegistrationService();
window.platformInfoService = new PlatformInfoService();
window.messengerService = new MessengerService();
window.loginService = new LoginService();
window.contactService = new ContactsService();

function updateProgress(status) {
    document.getElementById('progress-info').textContent = status;
}

registrationService.addListener(function (progressInfo) {
    console.log('progress: ' + progressInfo);
    updateProgress(progressInfo);
});

var registrationPromise = registrationService.doRegistration({
    'name': '',
    'email': '',
    'phoneNumber': '',
    'password': '',
});

registrationPromise.then(function (v) {
    var msg = 'Registration successful';
    console.log(msg);
    updateProgress(msg);
}, function (e) {
    var msg = 'Registration error: ' + e;
    console.error(msg);
    updateProgress(msg);
});

platformInfoService.getInfo().then(function (platformInfo) {
    console.log('Running on platform: ' + platformInfo.name)
    console.log('Running under os: ' + platformInfo.os)
});

loginService.login('emailOrPhoneNumber', 'password').then(function () {
    console.log('Login successful');
}).catch(function (e) {
    console.error('Login failed');
});

function fetchMessagesForContact(contactDetails) {
    return messengerService.getLastMessagesFor(contactDetails, 0, 100).then(function (messages) {
        console.log("Got messages");
        var messagesNode = document.getElementById('messages');
        for (var i=0; i < messages.length; ++i) {
            var msg = messages[i];
            var div = document.createElement('div');
            if (!msg.isSent)
                div.textContent = msg.timestamp + ' [' + contactDetails.name + '] ' + msg.message;
            else
                div.textContent = '> ' + msg.message;
            messagesNode.appendChild(div);
        }
    });
}

messengerService.addMessageStatusUpdateListener(function (messageInfo) {
    var message = messageInfo.message;
    var contactDetails = messageInfo.contact;
    console.log('Message id=' + message.id + ' to ' + contactDetails.name + ' updated; timestamp=' + message.timestamp);
});

contactService.getContacts().then(function (contacts) {
    console.log('contacts: ' + contacts);

    contacts.forEach(function (contactDetails) {
        messengerService.sendMessageTo(contactDetails, "Hello").then(function (m) {
            console.log('Sending message id=' + m.id + ' to ' + contactDetails.name);
        });
    });

    contacts.forEach(function (contactDetails) {
        fetchMessagesForContact(contactDetails).catch(function (e) {
            console.error("Unable to fetch messages: " + e);
        });
    });
}).catch(function (e) {
    console.error('Unable to fetch contacts: ' + e);
});

messengerService.getConversations().then(function (conversations) {
    conversations.forEach(function (convo) {
        console.log('Unread from ' + convo.contact.name + ': ' + convo.info.unreadMessageCount);
    });
}).catch(function (e) {
    console.error('Unable to fetch conversations: ' + conversations);
});

contactService.addNewContact({
    name: 'name',
    email: 'email',
    phoneNumber: '000-000-0000'
}).then(function () {
    console.log('Contact added');
}).catch(function (e) {
    console.error('Unable to add contact: ' + e);
});

//js-provided service
window.navigationService = {
    goBack: function () {
        console.log('Back button pressed');
    }
};
