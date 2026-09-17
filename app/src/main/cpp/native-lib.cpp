#include <string>
#include <vector>
#include <mutex>
#include <thread>
#include <atomic>
#include <memory>
#include <optional>
#include <string_view>
#include <poll.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <unistd.h>
#include <jni.h>
#include <getopt.h>
#include <signal.h>
#include <stdlib.h>
#include <errno.h>

#include "android_utils.hpp"

extern "C" {
#include "byedpi/error.h"
#include "byedpi_main.h"
#include "hev-main.h"
}

#define LOG_TAG "NoZapretNative"

using namespace android_utils;

static std::atomic<JavaVM*> g_jvm{nullptr};
static std::atomic<bool> g_proxy_running{false};
static std::atomic<bool> g_tunnel_running{false};
static std::mutex g_proxy_mutex;
static std::mutex g_tunnel_mutex;

static std::mutex g_vpn_service_mutex;
static std::unique_ptr<GlobalRef> g_vpn_service;
static jmethodID g_protect_method = nullptr;

static JNIEnv* get_jni_env(bool* attached) {
    JavaVM* jvm = g_jvm.load();
    if (!jvm) return nullptr;

    JNIEnv* env = nullptr;
    if (jvm->GetEnv((void**)&env, JNI_VERSION_1_6) == JNI_OK) {
        if (attached) *attached = false;
        return env;
    }

    JavaVMAttachArgs args = { JNI_VERSION_1_6, "NativeThread", nullptr };
    if (jvm->AttachCurrentThread(&env, &args) == JNI_OK) {
        if (attached) *attached = true;
        return env;
    }

    return nullptr;
}

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_example_nozapret_core_ByeDpiProxy_jniIsRunning([[maybe_unused]] JNIEnv *env, [[maybe_unused]] jobject thiz) {
    return g_proxy_running.load();
}

JNIEXPORT jboolean JNICALL
Java_com_example_nozapret_core_HevSocks5Tunnel_jniIsRunning([[maybe_unused]] JNIEnv *env, [[maybe_unused]] jobject thiz) {
    return g_tunnel_running.load();
}

jint JNI_OnLoad(JavaVM *vm, [[maybe_unused]] void *reserved) {
    g_jvm.store(vm);
    log_info(LOG_TAG, "JNI_OnLoad: JavaVM cached");
    return JNI_VERSION_1_6;
}

__attribute__((visibility("default")))
int android_protect_tunnel_socket(int fd) {
    if (fd < 0) return -1;

    bool attached = false;
    JNIEnv* env = get_jni_env(&attached);
    if (!env) return -1;

    int result = 0;
    {
        std::lock_guard<std::mutex> lock(g_vpn_service_mutex);
        if (g_vpn_service && g_protect_method) {
            jobject service = g_vpn_service->get();
            if (service) {
                jboolean success = env->CallBooleanMethod(service, g_protect_method, (jint)fd);
                if (env->ExceptionCheck()) {
                    env->ExceptionDescribe();
                    env->ExceptionClear();
                    result = -1;
                } else {
                    result = success ? 0 : -1;
                }
            }
        }
    }

    if (attached) {
        g_jvm.load()->DetachCurrentThread();
    }

    return result;
}

JNIEXPORT void JNICALL
Java_com_example_nozapret_services_DpiVpnService_jniCleanup(JNIEnv *env, [[maybe_unused]] jobject thiz) {
    std::lock_guard<std::mutex> lock(g_vpn_service_mutex);
    g_vpn_service.reset();
    g_protect_method = nullptr;
    log_info(LOG_TAG, "jniCleanup: VpnService reference cleared");
}

JNIEXPORT void JNICALL
Java_com_example_nozapret_services_DpiVpnService_jniSetVpnService(JNIEnv *env, [[maybe_unused]] jobject thiz, jobject vpn_service) {
    std::lock_guard<std::mutex> lock(g_vpn_service_mutex);

    g_vpn_service.reset();
    g_protect_method = nullptr;

    if (vpn_service) {
        JavaVM* jvm = g_jvm.load();
        if (!jvm) return;

        g_vpn_service = std::make_unique<GlobalRef>(jvm, env, vpn_service);
        LocalRef<jclass> local_class(env, env->GetObjectClass(vpn_service));
        if (local_class) {
            g_protect_method = env->GetMethodID(local_class.get(), "protect", "(I)Z");
        }

        if (g_protect_method) {
            log_info(LOG_TAG, "jniSetVpnService: VpnService.protect method cached");
        } else {
            log_error(LOG_TAG, "jniSetVpnService: VpnService.protect method NOT found");
        }
    }
}

JNIEXPORT jint JNICALL
Java_com_example_nozapret_core_ByeDpiProxy_jniStartProxy(JNIEnv *env, [[maybe_unused]] jobject thiz, jobjectArray args) {
    std::unique_lock<std::mutex> lock(g_proxy_mutex);

    if (g_proxy_running.exchange(true)) {
        log_warn(LOG_TAG, "jniStartProxy: Proxy already running");
        return -1;
    }

    log_info(LOG_TAG, "jniStartProxy: Initiating byedpi_main");

    int argc = env->GetArrayLength(args);
    std::vector<std::string> arg_strings;
    arg_strings.reserve(static_cast<size_t>(argc));
    std::vector<char*> argv;
    argv.reserve(static_cast<size_t>(argc) + 1);

    for (int i = 0; i < argc; i++) {
        jstring arg = (jstring)env->GetObjectArrayElement(args, i);
        if (arg) {
            const char *str = env->GetStringUTFChars(arg, nullptr);
            if (str) {
                arg_strings.emplace_back(str);
                env->ReleaseStringUTFChars(arg, str);
            }
            env->DeleteLocalRef(arg);
        }
    }

    for (auto& s : arg_strings) {
        argv.push_back(s.data());
    }
    argv.push_back(nullptr);

    reset_params();
    optind = 1;

    // Release lock before blocking call to allow jniStopProxy/jniForceClose to proceed
    lock.unlock();

    int result = -1;
    try {
        result = byedpi_main(static_cast<int>(arg_strings.size()), argv.data());
    } catch (...) {
        log_error(LOG_TAG, "jniStartProxy: Fatal error in byedpi_main");
    }

    lock.lock();
    log_info(LOG_TAG, "jniStartProxy: byedpi_main returned " + std::to_string(result));

    server_fd = -1;
    g_proxy_running = false;
    return result;
}

JNIEXPORT void JNICALL
Java_com_example_nozapret_core_ByeDpiProxy_jniStopProxy([[maybe_unused]] JNIEnv *env, [[maybe_unused]] jobject thiz) {
    // We don't necessarily need to hold the lock here as byedpi_stop is thread-safe (just sets a flag)
    // but we can if we want to ensure no overlap with init/cleanup.
    log_info(LOG_TAG, "jniStopProxy: Calling byedpi_stop");
    byedpi_stop();
}

JNIEXPORT jint JNICALL
Java_com_example_nozapret_core_ByeDpiProxy_jniForceClose([[maybe_unused]] JNIEnv *env, [[maybe_unused]] jobject thiz) {
    log_info(LOG_TAG, "jniForceClose: Calling byedpi_stop and waiting");
    byedpi_stop();

    // Wait for the running thread to finish and release the mutex
    std::lock_guard<std::mutex> lock(g_proxy_mutex);

    if (g_proxy_running) {
        log_warn(LOG_TAG, "jniForceClose: Proxy still marked as running, resetting manually");
        server_fd = -1;
        g_proxy_running = false;
    }
    return 0;
}

JNIEXPORT jint JNICALL
Java_com_example_nozapret_core_HevSocks5Tunnel_TProxyStartService(JNIEnv *env, [[maybe_unused]] jobject thiz, jstring config_path, jint fd) {
    std::unique_lock<std::mutex> lock(g_tunnel_mutex);

    if (g_tunnel_running.exchange(true)) {
        log_warn(LOG_TAG, "TProxyStartService: Tunnel already running");
        return -1;
    }

    const char *path = env->GetStringUTFChars(config_path, nullptr);
    log_info(LOG_TAG, "TProxyStartService: Starting hev_socks5_tunnel_main");

    lock.unlock();

    int res = -1;
    try {
        res = hev_socks5_tunnel_main(path, fd);
    } catch (...) {
        log_error(LOG_TAG, "TProxyStartService: Fatal error in hev_socks5_tunnel_main");
    }

    env->ReleaseStringUTFChars(config_path, path);

    lock.lock();
    log_info(LOG_TAG, "TProxyStartService: hev_socks5_tunnel_main returned " + std::to_string(res));

    g_tunnel_running = false;
    return res;
}

JNIEXPORT void JNICALL
Java_com_example_nozapret_core_HevSocks5Tunnel_TProxyStopService([[maybe_unused]] JNIEnv *env, [[maybe_unused]] jobject thiz) {
    log_info(LOG_TAG, "TProxyStopService: Calling hev_socks5_tunnel_quit");
    hev_socks5_tunnel_quit();
}

JNIEXPORT jlongArray JNICALL
Java_com_example_nozapret_core_HevSocks5Tunnel_TProxyGetStats(JNIEnv *env, [[maybe_unused]] jobject thiz) {
    jlongArray result = env->NewLongArray(2);
    jlong stats[2] = {0, 0};
    env->SetLongArrayRegion(result, 0, 2, stats);
    return result;
}

} // extern "C"
