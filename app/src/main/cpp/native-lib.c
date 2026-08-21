#include <string.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <unistd.h>

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
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern int server_fd;
static int g_proxy_running = 0;
static int g_tunnel_running = 0;
static JavaVM *g_jvm = NULL;
static jobject g_vpn_service = NULL;

// Global VM handle
jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    g_jvm = vm;
    return JNI_VERSION_1_6;
}

// Socket protection function
int android_protect_socket(int fd) {
    if (g_jvm == NULL || g_vpn_service == NULL) {
        return 0;
    }

    JNIEnv *env;
    int attached = 0;
    int ret = 0;

    int res = (*g_jvm)->GetEnv(g_jvm, (void **)&env, JNI_VERSION_1_6);
    if (res == JNI_EDETACHED) {
        if ((*g_jvm)->AttachCurrentThread(g_jvm, &env, NULL) != 0) {
            LOGE("Failed to attach thread for socket protection");
            return -1;
        }
        attached = 1;
    }

    jclass vpn_service_class = (*env)->GetObjectClass(env, g_vpn_service);
    if (!vpn_service_class) {
        LOGE("Failed to get VpnService class");
        if (attached) (*g_jvm)->DetachCurrentThread(g_jvm);
        return -1;
    }

    jmethodID protect_method = (*env)->GetMethodID(env, vpn_service_class, "protect", "(I)Z");
    (*env)->DeleteLocalRef(env, vpn_service_class);

    if (protect_method) {
        if (!(*env)->CallBooleanMethod(env, g_vpn_service, protect_method, fd)) {
            LOGE("VpnService.protect(%d) failed", fd);
            ret = -1;
        }
    } else {
        LOGE("VpnService.protect method not found");
        ret = -1;
    }

    if (attached) {
        (*g_jvm)->DetachCurrentThread(g_jvm);
    }

    return ret;
}

JNIEXPORT void JNICALL
Java_com_example_nozapret_services_DpiVpnService_jniSetVpnService(JNIEnv *env, jobject thiz, jobject vpn_service) {
    if (g_vpn_service != NULL) {
        (*env)->DeleteGlobalRef(env, g_vpn_service);
        g_vpn_service = NULL;
    }
    if (vpn_service != NULL) {
        g_vpn_service = (*env)->NewGlobalRef(env, vpn_service);
    }
}

// struct params params; // Removed to avoid redefinition

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
            (*env)->ReleaseStringUTFChars(env, arg, arg_str);
        } else {
            argv[i] = NULL;
        }

        (*env)->DeleteLocalRef(env, arg);
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
// These functions are expected to be provided by the hev-socks5-tunnel library
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
