#include <signal.h>
#include <errno.h>
#include <stdio.h>
#include <execinfo.h>
#include <fcntl.h>
#include <string.h>
#include <unistd.h>
#include <pthread.h>

#include "utils.h"

static volatile const char* dumpFilePath = NULL;

int ignoreSIGPIPE() {
    if (signal(SIGPIPE, SIG_IGN) == SIG_ERR)
        return errno;

    return 0;
}

//let's try and avoid heap allocations in here (doesn't matter when printing errors due to errors)
static void signalHandler(int signo) {
    if (!dumpFilePath) {
        fprintf(stderr, "dumpFilePath unset, not dumping backtrace\n");
        goto finish;
    }

    void* callstack[128];
    int frames = backtrace(callstack, 128);

    const char* signalName = NULL;
    switch (signo) {
        case SIGSEGV:
            signalName = "SIGSEGV";
            break;

        case SIGILL:
            signalName = "SIGILL";
            break;

        case SIGBUS:
            signalName = "SIGBUS";
            break;

        case SIGABRT:
            signalName = "SIGABRT";
            break;

        case SIGPIPE:
            signalName = "SIGPIPE";
            break;

        default:
            signalName = "unknown";
            break;
    }

    int fd = open((const char*) dumpFilePath, O_CREAT | O_TRUNC | O_WRONLY, 0700);
    if (fd < 0) {
        fprintf(stderr, "Failed to open crash report file: %s\n", strerror(errno));
        goto finish;
    }

    const char* s = "Crash due to signal: ";
    const char* nl = "\n";

    char threadName[256] = "";

    pthread_t self = pthread_self();
    int gotThreadName = 0;
    if (self) {
        if (pthread_getname_np(self, threadName, sizeof(threadName)))
            fprintf(stderr, "Failed to fetch thread name: %s\n", strerror(errno));
        else
            gotThreadName = 1;
    }

    if (!gotThreadName)
        strcpy(threadName, "<no name>");

    write(fd, s, strlen(s));
    write(fd, signalName, strlen(signalName));
    write(fd, nl, strlen(nl));
    write(fd, nl, strlen(nl));

    const char* currentThreadText = "Current thread name: ";
    write(fd, currentThreadText, strlen(currentThreadText));
    write(fd, threadName, strlen(threadName));
    write(fd, nl, strlen(nl));
    write(fd, nl, strlen(nl));

    backtrace_symbols_fd(callstack, frames, fd);

    close(fd);

finish:
    signal(signo, SIG_DFL);
    raise(signo);
}

int hookSignalCrashHandler(const char* path) {
    dumpFilePath = path;

    //UIKit & co don't attach any signal handlers

    if (signal(SIGSEGV, signalHandler) == SIG_ERR)
        return errno;

    if (signal(SIGILL, signalHandler) == SIG_ERR)
        return errno;

    if (signal(SIGBUS, signalHandler) == SIG_ERR)
        return errno;

    if (signal(SIGABRT, signalHandler) == SIG_ERR)
        return errno;

    //we ignore SIGPIPE, so don't set a handler for it

    return 0;
}
