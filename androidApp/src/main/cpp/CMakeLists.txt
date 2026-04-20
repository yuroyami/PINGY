cmake_minimum_required(VERSION 3.22.1)
project(pingy_icmp C)

# Shared C source for unprivileged ICMP ping — same file the JVM build uses.
# Path is relative to this CMakeLists.txt:
#   androidApp/src/main/cpp/CMakeLists.txt  →  ../../../../shared/native/icmp_ping.c
set(ICMP_SRC "${CMAKE_CURRENT_SOURCE_DIR}/../../../../shared/native/icmp_ping.c")

add_library(pingy_icmp SHARED ${ICMP_SRC})

target_compile_options(pingy_icmp PRIVATE
    -O2
    -Wall
    -Wextra
    -Wno-unused-parameter
    -fvisibility=hidden
)

# The JNI symbol is marked JNIEXPORT in the source, so it remains visible
# even with -fvisibility=hidden.
target_link_libraries(pingy_icmp PRIVATE)
