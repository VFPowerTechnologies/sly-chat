window.registrationService = new RegistrationService();
var registrationPromise = registrationService.doRegistration({
    'username': '',
    'password': '',
    'metadata': {}
}, function (progressInfo) {
    console.log('progress: ' + progressInfo);
    document.getElementById('progress-info').textContent = progressInfo;
});

registrationPromise.then(function (v) {
    console.log('Registration successful');
}, function (e) {
    console.log('Registration error: ' + e);
});
