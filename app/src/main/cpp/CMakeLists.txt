cmake_minimum_required(VERSION 3.22.1)
project("nozapret")

enable_language(ASM)

set(HEV_SOCKS5_DIR ${CMAKE_CURRENT_SOURCE_DIR}/hev-socks5-tunnel)
set(HEV_CORE_DIR ${HEV_SOCKS5_DIR}/src/core)
set(LWIP_DIR ${HEV_SOCKS5_DIR}/third-part/lwip)
set(TASK_SYSTEM_DIR ${HEV_SOCKS5_DIR}/third-part/hev-task-system)
set(YAML_DIR ${HEV_SOCKS5_DIR}/third-part/yaml)

# ByeDPI Sources
set(BYEDPI_SOURCES
    byedpi/main.c
    byedpi/conev.c
    byedpi/desync.c
    byedpi/extend.c
    byedpi/mpool.c
    byedpi/packets.c
    byedpi/proxy.c
)

# HevSocks5Tunnel Sources
set(TUNNEL_SOURCES
    ${HEV_SOCKS5_DIR}/src/hev-main.c
    ${HEV_SOCKS5_DIR}/src/hev-config.c
    ${HEV_SOCKS5_DIR}/src/hev-socks5-tunnel.c
    ${HEV_SOCKS5_DIR}/src/hev-socks5-session.c
    ${HEV_SOCKS5_DIR}/src/hev-socks5-session-tcp.c
    ${HEV_SOCKS5_DIR}/src/hev-socks5-session-udp.c
    ${HEV_SOCKS5_DIR}/src/hev-mapped-dns.c
    ${HEV_SOCKS5_DIR}/src/hev-tunnel-linux.c
    ${HEV_SOCKS5_DIR}/src/misc/hev-logger.c
    ${HEV_SOCKS5_DIR}/src/misc/hev-utils.c
    ${HEV_SOCKS5_DIR}/src/misc/hev-exec.c
    ${HEV_SOCKS5_DIR}/src/misc/hev-ring-buffer.c
)

# HevSocks5Core Sources
set(TUNNEL_CORE_SOURCES
    ${HEV_CORE_DIR}/src/hev-socks5.c
    ${HEV_CORE_DIR}/src/hev-socks5-tcp.c
    ${HEV_CORE_DIR}/src/hev-socks5-udp.c
    ${HEV_CORE_DIR}/src/hev-socks5-client.c
    ${HEV_CORE_DIR}/src/hev-socks5-client-tcp.c
    ${HEV_CORE_DIR}/src/hev-socks5-client-udp.c
    ${HEV_CORE_DIR}/src/hev-socks5-server.c
    ${HEV_CORE_DIR}/src/hev-socks5-authenticator.c
    ${HEV_CORE_DIR}/src/hev-socks5-logger.c
    ${HEV_CORE_DIR}/src/hev-socks5-misc.c
    ${HEV_CORE_DIR}/src/hev-socks5-user.c
)

# HevTaskSystem Sources
set(TASK_SYSTEM_SOURCES
    ${TASK_SYSTEM_DIR}/src/kern/task/hev-task.c
    ${TASK_SYSTEM_DIR}/src/kern/task/hev-task-executer.c
    ${TASK_SYSTEM_DIR}/src/kern/task/hev-task-call.c
    ${TASK_SYSTEM_DIR}/src/kern/task/hev-task-stack-mmap.c
    ${TASK_SYSTEM_DIR}/src/kern/core/hev-task-system.c
    ${TASK_SYSTEM_DIR}/src/kern/core/hev-task-system-schedule.c
    ${TASK_SYSTEM_DIR}/src/kern/itc/hev-task-channel.c
    ${TASK_SYSTEM_DIR}/src/kern/itc/hev-task-channel-select.c
    ${TASK_SYSTEM_DIR}/src/kern/sync/hev-task-cond.c
    ${TASK_SYSTEM_DIR}/src/kern/sync/hev-task-mutex.c
    ${TASK_SYSTEM_DIR}/src/kern/io/hev-task-io-reactor-epoll.c
    ${TASK_SYSTEM_DIR}/src/kern/time/hev-task-timer.c
    ${TASK_SYSTEM_DIR}/src/kern/aide/hev-task-aide.c
    ${TASK_SYSTEM_DIR}/src/mem/api/hev-memory-allocator-api.c
    ${TASK_SYSTEM_DIR}/src/mem/base/hev-memory-allocator.c
    ${TASK_SYSTEM_DIR}/src/mem/simple/hev-memory-allocator-simple.c
    ${TASK_SYSTEM_DIR}/src/mem/slice/hev-memory-allocator-slice.c
    ${TASK_SYSTEM_DIR}/src/lib/io/basic/hev-task-io.c
    ${TASK_SYSTEM_DIR}/src/lib/io/pipe/hev-task-io-pipe.c
    ${TASK_SYSTEM_DIR}/src/lib/io/socket/hev-task-io-socket.c
    ${TASK_SYSTEM_DIR}/src/lib/list/hev-list.c
    ${TASK_SYSTEM_DIR}/src/lib/rbtree/hev-rbtree.c
    ${TASK_SYSTEM_DIR}/src/lib/rbtree/hev-rbtree-cached.c
    ${TASK_SYSTEM_DIR}/src/lib/object/hev-object.c
    ${TASK_SYSTEM_DIR}/src/lib/object/hev-object-atomic.c
    ${TASK_SYSTEM_DIR}/src/lib/misc/hev-task-stack-detector.c
    ${TASK_SYSTEM_DIR}/src/lib/dns/hev-task-dns.c
    ${TASK_SYSTEM_DIR}/src/lib/dns/hev-task-dns-proxy.c
)

# Handle Architecture-specific task system execute
list(APPEND TASK_SYSTEM_SOURCES ${TASK_SYSTEM_DIR}/src/kern/task/hev-task-execute.S)

# LWIP Sources
set(LWIP_SOURCES
    ${LWIP_DIR}/src/core/init.c
    ${LWIP_DIR}/src/core/def.c
    ${LWIP_DIR}/src/core/dns.c
    ${LWIP_DIR}/src/core/inet_chksum.c
    ${LWIP_DIR}/src/core/ip.c
    ${LWIP_DIR}/src/core/mem.c
    ${LWIP_DIR}/src/core/memp.c
    ${LWIP_DIR}/src/core/netif.c
    ${LWIP_DIR}/src/core/pbuf.c
    ${LWIP_DIR}/src/core/raw.c
    ${LWIP_DIR}/src/core/stats.c
    ${LWIP_DIR}/src/core/sys.c
    ${LWIP_DIR}/src/core/tcp.c
    ${LWIP_DIR}/src/core/tcp_in.c
    ${LWIP_DIR}/src/core/tcp_out.c
    ${LWIP_DIR}/src/core/timeouts.c
    ${LWIP_DIR}/src/core/udp.c
    ${LWIP_DIR}/src/core/ipv4/autoip.c
    ${LWIP_DIR}/src/core/ipv4/dhcp.c
    ${LWIP_DIR}/src/core/ipv4/etharp.c
    ${LWIP_DIR}/src/core/ipv4/icmp.c
    ${LWIP_DIR}/src/core/ipv4/igmp.c
    ${LWIP_DIR}/src/core/ipv4/ip4_frag.c
    ${LWIP_DIR}/src/core/ipv4/ip4.c
    ${LWIP_DIR}/src/core/ipv4/ip4_addr.c
    ${LWIP_DIR}/src/core/ipv6/dhcp6.c
    ${LWIP_DIR}/src/core/ipv6/ethip6.c
    ${LWIP_DIR}/src/core/ipv6/icmp6.c
    ${LWIP_DIR}/src/core/ipv6/inet6.c
    ${LWIP_DIR}/src/core/ipv6/ip6_frag.c
    ${LWIP_DIR}/src/core/ipv6/ip6.c
    ${LWIP_DIR}/src/core/ipv6/ip6_addr.c
    ${LWIP_DIR}/src/core/ipv6/mld6.c
    ${LWIP_DIR}/src/core/ipv6/nd6.c
    ${LWIP_DIR}/src/ports/unix/lib/sys_arch.c
)

# YAML Sources
set(YAML_SOURCES
    ${YAML_DIR}/src/api.c
    ${YAML_DIR}/src/dumper.c
    ${YAML_DIR}/src/emitter.c
    ${YAML_DIR}/src/loader.c
    ${YAML_DIR}/src/parser.c
    ${YAML_DIR}/src/reader.c
    ${YAML_DIR}/src/scanner.c
    ${YAML_DIR}/src/writer.c
)

add_library(byedpi SHARED
    native-lib.c
    ${BYEDPI_SOURCES}
    ${TUNNEL_SOURCES}
    ${TUNNEL_CORE_SOURCES}
    ${TASK_SYSTEM_SOURCES}
    ${LWIP_SOURCES}
    ${YAML_SOURCES}
)

find_library(log-lib log)
find_library(android-lib android)

target_include_directories(byedpi PRIVATE
    .
    byedpi
    ${HEV_SOCKS5_DIR}/src
    ${HEV_SOCKS5_DIR}/src/misc
    ${HEV_CORE_DIR}/include
    ${HEV_CORE_DIR}/src
    ${TASK_SYSTEM_DIR}/include
    ${TASK_SYSTEM_DIR}/src
    ${TASK_SYSTEM_DIR}/src/kern/task
    ${TASK_SYSTEM_DIR}/src/kern/core
    ${TASK_SYSTEM_DIR}/src/kern/itc
    ${TASK_SYSTEM_DIR}/src/kern/sync
    ${TASK_SYSTEM_DIR}/src/lib/list
    ${TASK_SYSTEM_DIR}/src/lib/rbtree
    ${TASK_SYSTEM_DIR}/src/lib/io
    ${LWIP_DIR}/src/include
    ${LWIP_DIR}/src/ports/include
    ${LWIP_DIR}/src/ports/unix/include
    ${YAML_DIR}/src
)

if(CMAKE_ANDROID_ARCH_ABI MATCHES "arm")
    target_include_directories(byedpi PRIVATE ${TASK_SYSTEM_DIR}/src/arch/arm)
elseif(CMAKE_ANDROID_ARCH_ABI MATCHES "x86")
    target_include_directories(byedpi PRIVATE ${TASK_SYSTEM_DIR}/src/arch/x86)
endif()


target_compile_definitions(byedpi PRIVATE
    ANDROID_APP
    ENABLE_LIBRARY
    YAML_VERSION_MAJOR=0
    YAML_VERSION_MINOR=2
    YAML_VERSION_PATCH=5
    YAML_VERSION_STRING="0.2.5"
    COMMIT_ID="unknown"
    CONFIG_STACK_BACKEND=STACK_MMAP
    CONFIG_STACK_OVERFLOW_DETECTION=1
    CONFIG_MEMALLOC_SLICE_ALIGN=64
    CONFIG_MEMALLOC_SLICE_MAX_SIZE=4096
    CONFIG_MEMALLOC_SLICE_MAX_COUNT=1000
    CONFIG_SCHED_CLOCK=CLOCK_NONE
    ENABLE_STACK_OVERFLOW_DETECTION
    ENABLE_MEMALLOC_SLICE
    ENABLE_IO_SPLICE_SYSCALL
)

target_compile_options(byedpi PRIVATE -O3 -Wall -std=gnu99)

target_link_libraries(byedpi
    ${log-lib}
    ${android-lib}
)
