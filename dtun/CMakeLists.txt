cmake_minimum_required(VERSION 3.20)
project(dtun C)

set(CMAKE_C_STANDARD 99)

SET(CMAKE_BUILD_TYPE MinSizeRel)

set(MODULE_FILES
        dtun.h
        dtun.c
        )

add_library(dtun SHARED ${MODULE_FILES})

target_link_libraries(dtun iphlpapi ws2_32)

target_include_directories(dtun PUBLIC .)

# List files that install and/or package should provide.
# Each library should know what it want to distribute,
# which files are internal or intermediate and which are public library export.
install(TARGETS dtun DESTINATION dtun-bin)
install(FILES dtun.h DESTINATION dtun-include)

