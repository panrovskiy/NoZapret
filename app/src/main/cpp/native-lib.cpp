#include <string>
#include <vector>
#include <mutex>
#include <thread>
#include <condition_variable>
#include <atomic>
#include <memory>
#include <span>
#include <string_view>

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
#include "byedpi/error.h"
#include "byedpi_main.h"
#include "proxy.h"

#define LOG_TAG "NoZapretNative"

extern "C" {
    extern int server_fd;
    extern int byedpi_main(int argc, char **argv);
    extern void byedpi_stop(void);
    extern struct params params;
    extern void reset_params(void);

    // HevSocks5Tunnel
    extern int hev_socks5_tunnel_main(const char *config_path, int tunnel_fd);
    extern void hev_socks5_tunnel_quit(void);
}

static std::atomic<bool> g_proxy_running{false};
static std::atomic<bool> g_tunnel_running{false};
static JavaVM *g_jvm = nullptr;

static jobject g_vpn_service = nullptr;
static jclass g_vpn_service_class = nullptr;
static jmethodID g_protect_method = nullptr;
static std::mutex g_vpn_service_mutex;

// Protection Proxy Thread State
static std::unique_ptr<std::thread> g_proxy_thread;
static int g_proxy_req_fd = -1; // Proxy thread reads from here
static int g_caller_req_fd = -1; // Caller writes to here
static int g_proxy_res_fd = -1; // Proxy thread writes to here
static int g_caller_res_fd = -1; // Caller reads from here
static std::atomic<bool> g_proxy_running_flag{false};
static std::mutex g_proxy_comm_mutex;

static void vpn_protect_proxy_worker() {
    JNIEnv *env;
    JavaVMAttachArgs args = { JNI_VERSION_1_6, "VpnProtectProxy", nullptr };
    if (g_jvm->AttachCurrentThread(&env, &args) != 0) {
        android_utils::log_error(LOG_TAG, "Proxy thread failed to attach to JVM");
        return;
    }

    android_utils::log_info(LOG_TAG, "VPN Protect Proxy Thread started");
    while (g_proxy_running_flag) {
        int fd;
        ssize_t n = read(g_proxy_req_fd, &fd, sizeof(fd));
        if (n <= 0) {
            if (errno == EINTR) continue;
            break;
        }

        if (fd == -1) break; // Sentinel to stop

        int ret = 0;
        {
            std::lock_guard<std::mutex> lock(g_vpn_service_mutex);
            if (g_vpn_service && g_protect_method) {
                jboolean success = env->CallBooleanMethod(g_vpn_service, g_protect_method, fd);
                if (!success) {
                    android_utils::log_error(LOG_TAG, "Proxy: VpnService.protect(" + std::to_string(fd) + ") returned FALSE");
                    ret = -1;
                }
                if (env->ExceptionCheck()) {
                    android_utils::log_error(LOG_TAG, "Proxy: VpnService.protect(" + std::to_string(fd) + ") threw exception");
                    env->ExceptionDescribe();
                    env->ExceptionClear();
                    ret = -1;
                }
            } else {
                android_utils::log_error(LOG_TAG, "Proxy: VpnService or protect method NULL during call for fd " + std::to_string(fd));
                ret = -1;
            }
        }

        write(g_proxy_res_fd, &ret, sizeof(ret));
    }

    g_jvm->DetachCurrentThread();
    android_utils::log_info(LOG_TAG, "VPN Protect Proxy Thread stopped");
}

static void stop_protect_proxy() {
    if (g_proxy_running_flag.exchange(false)) {
        int sentinel = -1;
        if (g_caller_req_fd != -1) {
            write(g_caller_req_fd, &sentinel, sizeof(sentinel));
        }
        if (g_proxy_thread && g_proxy_thread->joinable()) {
            g_proxy_thread->join();
        }
        g_proxy_thread.reset();

        if (g_proxy_req_fd != -1) close(g_proxy_req_fd);
        if (g_caller_req_fd != -1) close(g_caller_req_fd);
        if (g_proxy_res_fd != -1) close(g_proxy_res_fd);
        if (g_caller_res_fd != -1) close(g_caller_res_fd);

        g_proxy_req_fd = g_caller_req_fd = g_proxy_res_fd = g_caller_res_fd = -1;
    }
}

static void start_protect_proxy() {
    stop_protect_proxy();

    int fds1[2], fds2[2];
    if (pipe(fds1) != 0 || pipe(fds2) != 0) {
        android_utils::log_error(LOG_TAG, "Failed to create pipes for protect proxy");
        return;
    }

    g_proxy_req_fd = fds1[0];
    g_caller_req_fd = fds1[1];
    g_caller_res_fd = fds2[0];
    g_proxy_res_fd = fds2[1];

    g_proxy_running_flag = true;
    g_proxy_thread = std::make_unique<std::thread>(vpn_protect_proxy_worker);
}

extern "C" {

jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    g_jvm = vm;
    return JNI_VERSION_1_6;
}

int android_protect_socket(int fd) {
    if (fd < 0) return -1;

    std::lock_guard<std::mutex> lock(g_proxy_comm_mutex);
    if (!g_proxy_running_flag || g_caller_req_fd == -1) {
        return 0;
    }

    if (write(g_caller_req_fd, &fd, sizeof(fd)) != sizeof(fd)) {
        android_utils::log_error(LOG_TAG, "android_protect_socket failed: write to pipe failed");
        return -1;
    }

    int result = -1;
    ssize_t n = read(g_caller_res_fd, &result, sizeof(result));
    if (n != sizeof(result)) {
        android_utils::log_error(LOG_TAG, "android_protect_socket failed: read from pipe failed");
        return -1;
    }

    return result;
}

JNIEXPORT void JNICALL
Java_com_example_nozapret_services_DpiVpnService_jniCleanup(JNIEnv *env, jobject thiz) {
    std::lock_guard<std::mutex> lock(g_vpn_service_mutex);
    if (g_vpn_service) {
        env->DeleteGlobalRef(g_vpn_service);
        g_vpn_service = nullptr;
    }
    if (g_vpn_service_class) {
        env->DeleteGlobalRef(g_vpn_service_class);
        g_vpn_service_class = nullptr;
    }
    g_protect_method = nullptr;
    stop_protect_proxy();
}

JNIEXPORT void JNICALL
Java_com_example_nozapret_services_DpiVpnService_jniSetVpnService(JNIEnv *env, jobject thiz, jobject vpn_service) {
    std::lock_guard<std::mutex> lock(g_vpn_service_mutex);

    if (g_vpn_service) {
        env->DeleteGlobalRef(g_vpn_service);
        g_vpn_service = nullptr;
    }
    if (g_vpn_service_class) {
        env->DeleteGlobalRef(g_vpn_service_class);
        g_vpn_service_class = nullptr;
    }
    g_protect_method = nullptr;

    if (vpn_service) {
        g_vpn_service = env->NewGlobalRef(vpn_service);
        jclass local_class = env->GetObjectClass(g_vpn_service);
        if (local_class) {
            g_vpn_service_class = (jclass)env->NewGlobalRef(local_class);
            g_protect_method = env->GetMethodID(g_vpn_service_class, "protect", "(I)Z");
            env->DeleteLocalRef(local_class);
        }

        if (g_protect_method) {
            start_protect_proxy();
        } else {
            android_utils::log_error(LOG_TAG, "Failed to find VpnService.protect(int) method");
        }
    } else {
        stop_protect_proxy();
    }
}

JNIEXPORT jint JNICALL
Java_com_example_nozapret_core_ByeDpiProxy_jniStartProxy(JNIEnv *env, jobject thiz, jobjectArray args) {
    if (g_proxy_running.exchange(true)) {
        return -1;
    }

    signal(SIGPIPE, SIG_IGN);

    int argc = env->GetArrayLength(args);
    std::vector<std::string> arg_strings;
    std::vector<char*> argv;

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

    int result = byedpi_main(static_cast<int>(arg_strings.size()), argv.data());

    server_fd = -1;
    g_proxy_running = false;

    return result;
}

JNIEXPORT void JNICALL
Java_com_example_nozapret_core_ByeDpiProxy_jniStopProxy(JNIEnv *env, jobject thiz) {
    if (!g_proxy_running) return;
    byedpi_stop();
}

JNIEXPORT jint JNICALL
Java_com_example_nozapret_core_ByeDpiProxy_jniForceClose(JNIEnv *env, jobject thiz) {
    if (g_proxy_running) {
        byedpi_stop();
        // Simple wait
        for(int i=0; i<200 && g_proxy_running; ++i) usleep(10000);
    }
    return g_proxy_running ? 1 : 0;
}

JNIEXPORT jint JNICALL
Java_com_example_nozapret_core_HevSocks5Tunnel_TProxyStartService(JNIEnv *env, jobject thiz, jstring config_path, jint fd) {
    if (g_tunnel_running.exchange(true)) return -1;

    const char *path = env->GetStringUTFChars(config_path, nullptr);
    int res = hev_socks5_tunnel_main(path, fd);
    env->ReleaseStringUTFChars(config_path, path);

    g_tunnel_running = false;
    return res;
}

JNIEXPORT void JNICALL
Java_com_example_nozapret_core_HevSocks5Tunnel_TProxyStopService(JNIEnv *env, jobject thiz) {
    if (!g_tunnel_running) return;
    hev_socks5_tunnel_quit();
}

JNIEXPORT jlongArray JNICALL
Java_com_example_nozapret_core_HevSocks5Tunnel_TProxyGetStats(JNIEnv *env, jobject thiz) {
    jlongArray result = env->NewLongArray(2);
    jlong stats[2] = {0, 0};
    env->SetLongArrayRegion(result, 0, 2, stats);
    return result;
}

} // extern "C"
