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

messengerService.getLastMessagesFor("a", 0, 100).then(function (messages) {
    console.log("Got messages");
    var messagesNode = document.getElementById('messages');
    for (var i=0; i < messages.length; ++i) {
        var msg = messages[i];
        var div = document.createElement('div');
        div.textContent = msg.timestamp + ' [' + msg.contactName + '] ' + msg.message;
        messagesNode.appendChild(div);
    }
}).catch(function (e) {
    console.error("Unable to fetch messages: " + e);
});

loginService.login('emailOrPhoneNumber', 'password').then(function () {
    console.log('Login successful');
}).catch(function (e) {
    console.error('Login failed');
});

contactService.getContacts().then(function (contacts) {
    console.log('contacts: ' + contacts);
}).catch(function (e) {
    console.error('Unable to fetch contacts: ' + e);
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
