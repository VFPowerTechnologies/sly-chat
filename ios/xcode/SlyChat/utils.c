#include <signal.h>
#include <errno.h>

__attribute__((used)) int ignoreSIGPIPE() {
    if (signal(SIGPIPE, SIG_IGN) == SIG_ERR)
        return errno;

    return 0;
}