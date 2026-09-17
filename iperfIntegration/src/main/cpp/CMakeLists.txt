cmake_minimum_required(VERSION 4.1.2)

project("traffic_light" C)

set(IPERF_SRC_DIR "${CMAKE_CURRENT_SOURCE_DIR}/iperf/src")
set(JNI_SRC_FILE "${CMAKE_CURRENT_SOURCE_DIR}/iperf_jni.c")

set(PACKAGE_VERSION "3.21")

configure_file(
    "${CMAKE_CURRENT_SOURCE_DIR}/iperf_config_android.h"
    "${IPERF_SRC_DIR}/iperf_config.h"
    COPYONLY
)
configure_file(
    "${IPERF_SRC_DIR}/version.h.in"
    "${IPERF_SRC_DIR}/version.h"
    @ONLY
)

add_library(traffic_light SHARED
    "${JNI_SRC_FILE}"
    "${IPERF_SRC_DIR}/cjson.c"
    "${IPERF_SRC_DIR}/dscp.c"
    "${IPERF_SRC_DIR}/iperf_api.c"
    "${IPERF_SRC_DIR}/iperf_auth.c"
    "${IPERF_SRC_DIR}/iperf_client_api.c"
    "${IPERF_SRC_DIR}/iperf_error.c"
    "${IPERF_SRC_DIR}/iperf_locale.c"
    "${IPERF_SRC_DIR}/iperf_sctp.c"
    "${IPERF_SRC_DIR}/iperf_server_api.c"
    "${IPERF_SRC_DIR}/iperf_tcp.c"
    "${IPERF_SRC_DIR}/iperf_time.c"
    "${IPERF_SRC_DIR}/iperf_udp.c"
    "${IPERF_SRC_DIR}/iperf_util.c"
    "${IPERF_SRC_DIR}/iperf_pthread.c"
    "${IPERF_SRC_DIR}/net.c"
    "${IPERF_SRC_DIR}/tcp_info.c"
    "${IPERF_SRC_DIR}/timer.c"
    "${IPERF_SRC_DIR}/units.c"
)

target_include_directories(traffic_light PRIVATE
    "${IPERF_SRC_DIR}"
    "${CMAKE_CURRENT_SOURCE_DIR}"
)

target_compile_definitions(traffic_light PRIVATE HAVE_CONFIG_H HAVE_PTHREAD)

find_package(Threads REQUIRED)
target_link_libraries(traffic_light Threads::Threads log android)
