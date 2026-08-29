#include <string.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <unistd.h>
#include <pthread.h>
#include <jni.h>
#include <getopt.h>
#include <signal.h>
#include <setjmp.h>
#include <stdlib.h>
#include <errno.h>
#include <android/log.h>

#include "byedpi/error.h"
#include "byedpi_main.h"
#include "proxy.h"

#define LOG_TAG "NoZapretNative"
#ifdef NDEBUG
    #define LOGI(...) do {} while(0)
    #define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#else
    #define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
    #define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#endif

extern int server_fd;
static int g_proxy_running = 0;
static int g_tunnel_running = 0;
static JavaVM *g_jvm = NULL;

static jobject g_vpn_service = NULL;
static jclass g_vpn_service_class = NULL;
static jmethodID g_protect_method = NULL;
static pthread_mutex_t g_vpn_service_mutex = PTHREAD_MUTEX_INITIALIZER;

// Protection Proxy Thread State
static pthread_t g_proxy_thread;
static int g_proxy_req_fd = -1; // Pipe for sending FDs to proxy thread
static int g_caller_req_fd = -1; // Pipe for receiving FDs by proxy thread (read end)
static int g_proxy_res_fd = -1; // Pipe for sending results back
static int g_caller_res_fd = -1; // Pipe for receiving results from proxy thread
static volatile int g_proxy_running_flag = 0;
static pthread_mutex_t g_proxy_comm_mutex = PTHREAD_MUTEX_INITIALIZER;

static void* vpn_protect_proxy_thread(void* arg) {
    JNIEnv *env;
    if ((*g_jvm)->AttachCurrentThread(g_jvm, &env, NULL) != 0) {
        LOGE("Proxy thread failed to attach to JVM");
        return NULL;
    }

    LOGI("VPN Protect Proxy Thread started");
    while (g_proxy_running_flag) {
        int fd;
        ssize_t n = read(g_proxy_req_fd, &fd, sizeof(fd));
        if (n <= 0) {
            if (errno == EINTR) continue;
            break;
        }

        if (fd == -1) break; // Sentinel to stop

        int ret = 0;
        pthread_mutex_lock(&g_vpn_service_mutex);
        if (g_vpn_service && g_protect_method) {
            jboolean success = (*env)->CallBooleanMethod(env, g_vpn_service, g_protect_method, fd);
            if (!success) {
                LOGE("Proxy: VpnService.protect(%d) returned FALSE", fd);
                ret = -1;
            }
            if ((*env)->ExceptionCheck(env)) {
                LOGE("Proxy: VpnService.protect(%d) threw exception", fd);
                (*env)->ExceptionDescribe(env);
                (*env)->ExceptionClear(env);
                ret = -1;
            }
        } else {
            LOGE("Proxy: VpnService or protect method NULL during call for fd %d", fd);
            ret = -1;
        }
        pthread_mutex_unlock(&g_vpn_service_mutex);

        write(g_proxy_res_fd, &ret, sizeof(ret));
    }

    (*g_jvm)->DetachCurrentThread(g_jvm);
    LOGI("VPN Protect Proxy Thread stopped");
    return NULL;
}

static void stop_protect_proxy() {
    if (g_proxy_running_flag) {
        g_proxy_running_flag = 0;
        int sentinel = -1;
        if (g_caller_req_fd != -1) {
            write(g_caller_req_fd, &sentinel, sizeof(sentinel));
        }
        pthread_join(g_proxy_thread, NULL);

        if (g_proxy_req_fd != -1) close(g_proxy_req_fd);
        if (g_caller_req_fd != -1) close(g_caller_req_fd);
        if (g_proxy_res_fd != -1) close(g_proxy_res_fd);
        if (g_caller_res_fd != -1) close(g_caller_res_fd);

        g_proxy_req_fd = -1;
        g_caller_req_fd = -1;
        g_proxy_res_fd = -1;
        g_caller_res_fd = -1;
    }
}

static void start_protect_proxy() {
    stop_protect_proxy();

    int fds1[2], fds2[2];
    if (pipe(fds1) != 0 || pipe(fds2) != 0) {
        LOGE("Failed to create pipes for protect proxy");
        return;
    }

    g_proxy_req_fd = fds1[0]; // Proxy thread reads from here
    g_caller_req_fd = fds1[1]; // Caller writes to here

    g_caller_res_fd = fds2[0]; // Caller reads from here
    g_proxy_res_fd = fds2[1]; // Proxy thread writes to here

    g_proxy_running_flag = 1;
    if (pthread_create(&g_proxy_thread, NULL, vpn_protect_proxy_thread, NULL) != 0) {
        LOGE("Failed to create protect proxy thread");
        g_proxy_running_flag = 0;
        close(fds1[0]); close(fds1[1]);
        close(fds2[0]); close(fds2[1]);
        g_proxy_req_fd = g_caller_req_fd = g_proxy_res_fd = g_caller_res_fd = -1;
    }
}

// Global VM handle
jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    g_jvm = vm;
    return JNI_VERSION_1_6;
}

// Socket protection function
int android_protect_socket(int fd) {
    if (fd < 0) return -1;

    pthread_mutex_lock(&g_proxy_comm_mutex);
    if (!g_proxy_running_flag || g_caller_req_fd == -1) {
        LOGE("android_protect_socket(%d) failed: proxy not running", fd);
        pthread_mutex_unlock(&g_proxy_comm_mutex);
        return -1;
    }

    // Send FD to proxy thread
    if (write(g_caller_req_fd, &fd, sizeof(fd)) != sizeof(fd)) {
        LOGE("android_protect_socket(%d) failed: write to pipe failed", fd);
        pthread_mutex_unlock(&g_proxy_comm_mutex);
        return -1;
    }

    // Wait for result
    int result = -1;
    if (read(g_caller_res_fd, &result, sizeof(result)) != sizeof(result)) {
        LOGE("android_protect_socket(%d) failed: read from pipe failed", fd);
    }
    pthread_mutex_unlock(&g_proxy_comm_mutex);

    if (result != 0) {
        LOGE("android_protect_socket(%d) result: %d", fd, result);
    } else {
        LOGI("android_protect_socket(%d) success", fd);
    }

    return result;
}

// Tunnel protection function - for now, we don't protect tunnel sockets
// because they connect to 127.0.0.1 and protect() might break localhost connections
// on some devices. Also, the app is already excluded from VPN.
int android_protect_tunnel_socket(int fd) {
    return 0;
}

JNIEXPORT void JNICALL
Java_com_example_nozapret_services_DpiVpnService_jniSetVpnService(JNIEnv *env, jobject thiz, jobject vpn_service) {
    pthread_mutex_lock(&g_vpn_service_mutex);

    if (g_vpn_service != NULL) {
        (*env)->DeleteGlobalRef(env, g_vpn_service);
        g_vpn_service = NULL;
    }
    if (g_vpn_service_class != NULL) {
        (*env)->DeleteGlobalRef(env, g_vpn_service_class);
        g_vpn_service_class = NULL;
    }
    g_protect_method = NULL;

    if (vpn_service != NULL) {
        g_vpn_service = (*env)->NewGlobalRef(env, vpn_service);
        jclass local_class = (*env)->GetObjectClass(env, g_vpn_service);
        if (local_class) {
            g_vpn_service_class = (jclass)(*env)->NewGlobalRef(env, local_class);
            g_protect_method = (*env)->GetMethodID(env, g_vpn_service_class, "protect", "(I)Z");
            (*env)->DeleteLocalRef(env, local_class);
        }

        start_protect_proxy();
    } else {
        stop_protect_proxy();
    }

    pthread_mutex_unlock(&g_vpn_service_mutex);
}

struct params default_params = {
        .await_int = 10,
        .ipv6 = 1,
        .resolve = 1,
        .udp = 1,
        .max_open = 512,
        .bfsize = 16384,
        .baddr = {
            .in6 = { .sin6_family = AF_INET6 }
        },
        .laddr = {
            .in = { .sin_family = AF_INET }
        },
        .debug = 0
};

void reset_params(void) {
    params = default_params;
    params.dp = NULL;
    params.dp_n = 0;
    params.dp_full_mask = 0;
    params.need_free_n = 0;
    params.mempool = NULL;
}

JNIEXPORT jint JNICALL
Java_com_example_nozapret_core_ByeDpiProxy_jniStartProxy(JNIEnv *env, jobject thiz, jobjectArray args) {
    if (g_proxy_running) {
        LOG(LOG_S, "proxy already running");
        return -1;
    }

    // Ignore SIGPIPE to prevent crash when writing to a closed socket
    signal(SIGPIPE, SIG_IGN);

    int argc = (*env)->GetArrayLength(env, args);
    char **argv = calloc(argc + 1, sizeof(char *));
    int invalid = 0;

    if (!argv) {
        LOG(LOG_S, "failed to allocate memory for argv");
        return -1;
    }

    for (int i = 0; i < argc; i++) {
        jstring arg = (jstring) (*env)->GetObjectArrayElement(env, args, i);

        if (!arg) {
            argv[i] = NULL;
            continue;
        }

        const char *arg_str = (*env)->GetStringUTFChars(env, arg, 0);
        if (arg_str) {
            argv[i] = strdup(arg_str);
            if (!argv[i]) {
                LOGE("failed to strdup arg %d", i);
                invalid = 1;
            }
            (*env)->ReleaseStringUTFChars(env, arg, arg_str);
        } else {
            argv[i] = NULL;
        }

        (*env)->DeleteLocalRef(env, arg);
    }

    if (invalid) {
        for (int i = 0; i < argc; i++) {
            if (argv[i]) free(argv[i]);
        }
        free(argv);
        return -1;
    }
    
    LOG(LOG_S, "starting proxy with %d args", argc);
    reset_params();
    g_proxy_running = 1;
    optind = 1;

    int result = byedpi_main(argc, argv);

    LOG(LOG_S, "proxy return code %d", result);
    server_fd = -1;
    g_proxy_running = 0;

    for (int i = 0; i < argc; i++) {
        if (argv[i]) free(argv[i]);
    }
    free(argv);

    return result;
}

JNIEXPORT void JNICALL
Java_com_example_nozapret_core_ByeDpiProxy_jniStopProxy(JNIEnv *env, jobject thiz) {
    LOG(LOG_S, "stopping proxy via byedpi_stop");

    if (!g_proxy_running) {
        LOG(LOG_S, "proxy is not running");
        return;
    }

    byedpi_stop();

    // Wait for the proxy thread to exit (max 5 seconds)
    int timeout = 500;
    while (g_proxy_running && timeout-- > 0) {
        usleep(10000);
    }

    if (g_proxy_running) {
        LOGE("Timed out waiting for proxy to stop");
    }
}

JNIEXPORT jint JNICALL
Java_com_example_nozapret_core_ByeDpiProxy_jniForceClose(JNIEnv *env, jobject thiz) {
    if (g_proxy_running) {
        LOGI("jniForceClose: proxy is running, calling byedpi_stop()");
        byedpi_stop();

        // Wait for it to actually stop
        int timeout = 200; // 2 seconds total
        while (g_proxy_running && timeout-- > 0) {
            usleep(10000);
        }

        if (g_proxy_running) {
            LOGE("jniForceClose: timeout waiting for proxy to stop!");
        } else {
            LOGI("jniForceClose: proxy stopped successfully");
        }
    }
    return g_proxy_running;
}

// HevSocks5Tunnel JNI
extern int hev_socks5_tunnel_main(const char *config_path, int tunnel_fd);
extern void hev_socks5_tunnel_quit(void);

JNIEXPORT jint JNICALL
Java_com_example_nozapret_core_HevSocks5Tunnel_TProxyStartService(JNIEnv *env, jobject thiz, jstring config_path, jint fd) {
    if (g_tunnel_running) {
        LOGI("tunnel already running");
        return -1;
    }
    g_tunnel_running = 1;
    const char *path = (*env)->GetStringUTFChars(env, config_path, NULL);
    LOGI("starting hev-socks5-tunnel with config: %s, fd: %d", path, fd);
    int res = hev_socks5_tunnel_main(path, fd);
    LOGI("hev_socks5_tunnel_main returned with code: %d", res);
    (*env)->ReleaseStringUTFChars(env, config_path, path);
    g_tunnel_running = 0;
    return res;
}

JNIEXPORT void JNICALL
Java_com_example_nozapret_core_HevSocks5Tunnel_TProxyStopService(JNIEnv *env, jobject thiz) {
    LOG(LOG_S, "stopping hev-socks5-tunnel");
    if (!g_tunnel_running) {
        LOG(LOG_S, "tunnel is not running");
        return;
    }
    hev_socks5_tunnel_quit();

    // Wait for the tunnel thread to exit (max 5 seconds)
    int timeout = 500;
    while (g_tunnel_running && timeout-- > 0) {
        usleep(10000);
    }

    if (g_tunnel_running) {
        LOGE("Timed out waiting for tunnel to stop");
    }
}

JNIEXPORT jlongArray JNICALL
Java_com_example_nozapret_core_HevSocks5Tunnel_TProxyGetStats(JNIEnv *env, jobject thiz) {
    jlongArray result = (*env)->NewLongArray(env, 2);
    // Dummy implementation for stats
    jlong stats[2] = {0, 0};
    (*env)->SetLongArrayRegion(env, result, 0, 2, stats);
    return result;
}
