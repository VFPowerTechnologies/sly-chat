if(typeof $ == "undefined"){
    window.location.href = "index.html";
}
else {
    window.configService.getAppConfig().then(function (appConfig) {
        document.getElementById("rememberMe").checked = appConfig.loginRememberMe;
    }).catch(function (e) {
        KEYTAP.exceptionController.displayDebugMessage(e);
        console.error("Unable to fetch app config: " + e.message);
    });
}
