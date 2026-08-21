#ifndef CIADPI_ERROR_H
#define CIADPI_ERROR_H

#include <stdio.h>
#include <stdarg.h>
#include <errno.h>
#include <string.h>

#ifdef _WIN32
    #include <winsock2.h>
#endif
#ifdef ANDROID_APP
    #include <android/log.h>
#endif

#include "params.h"

#ifdef _WIN32
#define get_e() \
    unie(WSAGetLastError())
#else
#define get_e() \
    errno
#endif

#ifdef _WIN32
    #define uniperror(str) \
        fprintf(stderr, "%s: %ld\n", str, GetLastError())
#else
    #ifdef ANDROID_APP
    #define uniperror(str) \
        __android_log_print(ANDROID_LOG_ERROR, "proxy", \
            "%s: %s\n", str, strerror(errno))
    #else
    #define uniperror(str) \
        perror(str)
    #endif
#endif

static inline int unie(int e)
{
    #ifdef _WIN32
    switch (e) {
        case WSAEWOULDBLOCK:
            return EAGAIN;
        case WSAETIMEDOUT:
            return ETIMEDOUT;
        case WSAENETUNREACH:
            return ENETUNREACH;
        case WSAEHOSTUNREACH:
            return EHOSTUNREACH;
        case WSAECONNREFUSED:
            return ECONNREFUSED;
        case WSAECONNRESET:
            return ECONNRESET;
    }
    #endif
    return e;
}

#ifdef ANDROID_APP
    #define LOG_E ANDROID_LOG_ERROR
    #define LOG_S ANDROID_LOG_DEBUG
    #define LOG_L ANDROID_LOG_VERBOSE
    #define LOG(s, str, ...) \
        __android_log_print(s, "proxy", str, ##__VA_ARGS__)
    #define LOG_ENABLED (params.debug >= 0)
#else
    #define LOG_E -1
    #define LOG_S 1
    #define LOG_L 2
    static inline void LOG(int s, const char *str, ...) {
        if (params.debug >= s) {
            va_list args;
            va_start(args, str);
            vfprintf(stderr, str, args);
            va_end(args);
        }
    }
    #define LOG_ENABLED (params.debug >= LOG_S)
#endif

#define INIT_ADDR_STR(dst) \
    char ADDR_STR[INET6_ADDRSTRLEN]; \
    const char *p_addr = 0; \
    if (dst.sa.sa_family == AF_INET) \
        p_addr = inet_ntop(AF_INET, &dst.in.sin_addr, ADDR_STR, sizeof(ADDR_STR)); \
    else \
        p_addr = inet_ntop(AF_INET6, &dst.in6.sin6_addr, ADDR_STR, sizeof(ADDR_STR)); \
    if (!p_addr) uniperror("inet_ntop");

#define INIT_HEX_STR(b, s) \
    char HEX_STR[(size_t)(s) * 2 + 1]; \
    HEX_STR[sizeof(HEX_STR) - 1] = 0; \
    do { \
        size_t i_hex; \
        for (i_hex = 0; i_hex + 4 <= (size_t)(s); i_hex += 4) \
            snprintf(HEX_STR + i_hex * 2, sizeof(HEX_STR) - i_hex * 2, \
                "%02x%02x%02x%02x", (unsigned int)(unsigned char)b[i_hex], (unsigned int)(unsigned char)b[i_hex+1], \
                (unsigned int)(unsigned char)b[i_hex+2], (unsigned int)(unsigned char)b[i_hex+3]); \
        for (; i_hex < (size_t)(s); i_hex++) \
            snprintf(HEX_STR + i_hex * 2, sizeof(HEX_STR) - i_hex * 2, "%02x", (unsigned int)(unsigned char)b[i_hex]); \
    } while (0)

#endif
