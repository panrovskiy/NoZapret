#pragma once

#include <jni.h>
#include <android/log.h>

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Protects a socket from being routed through the VPN.
 * Must be called for all outbound sockets created by the proxy or tunnel.
 *
 * @param fd The socket file descriptor to protect.
 * @return 0 on success, -1 on failure.
 */
int android_protect_socket(int fd);

#ifdef __cplusplus
}
#endif

#ifdef __cplusplus
#include <memory>
#include <string_view>

namespace android_utils {

// RAII wrapper for JNI LocalRef
template <typename T>
struct LocalRefDeleter {
    JNIEnv* env;
    void operator()(T obj) const {
        if (obj) env->DeleteLocalRef(obj);
    }
};

template <typename T>
using LocalRef = std::unique_ptr<typename std::remove_pointer<T>::type, LocalRefDeleter<T>>;

inline void log_error(std::string_view tag, std::string_view message) {
    __android_log_print(ANDROID_LOG_ERROR, tag.data(), "%s", message.data());
}

inline void log_info(std::string_view tag, std::string_view message) {
    __android_log_print(ANDROID_LOG_INFO, tag.data(), "%s", message.data());
}

} // namespace android_utils
#endif
