#pragma once

#include <jni.h>
#include <android/log.h>
#include <unistd.h>
#include <memory>
#include <string>
#include <utility>

#ifdef __cplusplus
extern "C" {
#endif

int android_protect_tunnel_socket(int fd);

#ifdef __cplusplus
}
#endif

namespace android_utils {

class ScopedFd {
public:
    explicit ScopedFd(int fd = -1) noexcept : fd_(fd) {}
    ~ScopedFd() { reset(); }

    ScopedFd(const ScopedFd&) = delete;
    ScopedFd& operator=(const ScopedFd&) = delete;

    ScopedFd(ScopedFd&& other) noexcept : fd_(other.release()) {}
    ScopedFd& operator=(ScopedFd&& other) noexcept {
        reset(other.release());
        return *this;
    }

    [[nodiscard]] int get() const noexcept { return fd_; }
    [[nodiscard]] bool is_valid() const noexcept { return fd_ >= 0; }

    int release() noexcept { return std::exchange(fd_, -1); }

    void reset(int fd = -1) noexcept {
        if (fd_ >= 0) close(fd_);
        fd_ = fd;
    }

    operator int() const noexcept { return fd_; }

private:
    int fd_;
};

template <typename T>
class LocalRef {
public:
    LocalRef(JNIEnv* env, T ref) noexcept : env_(env), ref_(ref) {}
    ~LocalRef() { if (ref_) env_->DeleteLocalRef(ref_); }

    LocalRef(const LocalRef&) = delete;
    LocalRef& operator=(const LocalRef&) = delete;

    LocalRef(LocalRef&& other) noexcept
        : env_(other.env_), ref_(std::exchange(other.ref_, nullptr)) {}

    [[nodiscard]] T get() const noexcept { return ref_; }
    operator T() const noexcept { return ref_; }
    explicit operator bool() const noexcept { return ref_ != nullptr; }

private:
    JNIEnv* env_;
    T ref_;
};

class GlobalRef {
public:
    GlobalRef(JavaVM* vm, JNIEnv* env, jobject ref) noexcept : vm_(vm) {
        ref_ = env->NewGlobalRef(ref);
    }
    ~GlobalRef() {
        if (ref_) {
            JNIEnv* env = nullptr;
            if (vm_->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) == JNI_OK) {
                env->DeleteGlobalRef(ref_);
            } else if (vm_->AttachCurrentThread(&env, nullptr) == JNI_OK) {
                env->DeleteGlobalRef(ref_);
                vm_->DetachCurrentThread();
            }
        }
    }

    GlobalRef(const GlobalRef&) = delete;
    GlobalRef& operator=(const GlobalRef&) = delete;

    GlobalRef(GlobalRef&& other) noexcept
        : vm_(other.vm_), ref_(std::exchange(other.ref_, nullptr)) {}

    [[nodiscard]] jobject get() const noexcept { return ref_; }

private:
    JavaVM* vm_;
    jobject ref_;
};

inline void log_error(const char* tag, const char* message) {
    __android_log_print(ANDROID_LOG_ERROR, tag, "%s", message);
}

inline void log_info(const char* tag, const char* message) {
    __android_log_print(ANDROID_LOG_INFO, tag, "%s", message);
}

inline void log_warn(const char* tag, const char* message) {
    __android_log_print(ANDROID_LOG_WARN, tag, "%s", message);
}

inline void log_error(const char* tag, const std::string& message) {
    __android_log_print(ANDROID_LOG_ERROR, tag, "%s", message.c_str());
}

inline void log_info(const char* tag, const std::string& message) {
    __android_log_print(ANDROID_LOG_INFO, tag, "%s", message.c_str());
}

inline void log_warn(const char* tag, const std::string& message) {
    __android_log_print(ANDROID_LOG_WARN, tag, "%s", message.c_str());
}

} // namespace android_utils
