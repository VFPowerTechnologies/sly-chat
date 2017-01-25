#ifndef SLY_UTILS_H
#define SLY_UTILS_H

#define EXPORT __attribute__((visibility("default")))

#ifdef __cplusplus
extern "C" {
#endif

EXPORT int ignoreSIGPIPE();
EXPORT int hookSignalCrashHandler(const char* path);

#ifdef __cplusplus
}
#endif

#endif
