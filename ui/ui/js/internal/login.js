if(typeof $ == "undefined"){
    window.location.href = "index.html";
}
else {
    window.configService.getLoginRememberMe().then(function (v) {
        document.getElementById("rememberMe").checked = v;
    }).catch(function (e) {
        KEYTAP.exceptionController.displayDebugMessage(e);
        console.error("Unable to fetch app config: " + e.message);
    });
}
