//this is only supported in more recent versions of webview; doesn't work with
//the version shipped with jfx
window.addEventListener('unhandledrejection', function (event) {
    var stacktrace = event.reason.stacktrace;
    var stacktraceInfo = '';
    if (stacktrace)
        stacktraceInfo = "\n" + stacktrace;

    console.error('Unhandled promise rejection: ' + event.reason + stacktraceInfo);

    event.preventDefault();
});

window.uiController = new UIController();

uiController.startUI();
