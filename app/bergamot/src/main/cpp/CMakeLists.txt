# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html.
# For more examples on how to use CMake, see https://github.com/android/ndk-samples.

# Sets the minimum CMake version required for this project.
cmake_minimum_required(VERSION 3.22.1)
add_compile_options("-ffile-prefix-map=${CMAKE_CURRENT_SOURCE_DIR}=.")
# Declares the project name. The project name can be accessed via ${ PROJECT_NAME},
# Since this is the top level CMakeLists.txt, the project name is also accessible
# with ${CMAKE_PROJECT_NAME} (both CMake variables are in-sync within the top level
# build script scope).
project("bergamot-sys")

# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds them for you.
# Gradle automatically packages shared libraries with your APK.
#
# In this top level CMakeLists.txt, ${CMAKE_PROJECT_NAME} is used to define
# the target library name; in the sub-module's CMakeLists.txt, ${PROJECT_NAME}
# is preferred for the same purpose.
#
# In order to load a library into your app from Java/Kotlin, you must call
# System.loadLibrary() and pass the name of the library defined here;
# for GameActivity/NativeActivity derived applications, the same library name must be
# used in the AndroidManifest.xml file.
add_library(${CMAKE_PROJECT_NAME} SHARED
        # List C/C++ source files with relative paths to this CMakeLists.txt.
        bergamot.cpp)
set(CMAKE_INTERPROCEDURAL_OPTIMIZATION TRUE)

set(USE_WASM_COMPATIBLE_SOURCES OFF CACHE BOOL "" FORCE)
set(CMAKE_BUILD_TYPE Release CACHE STRING "" FORCE)
set(COMPILE_WASM OFF CACHE BOOL "" FORCE)
set(COMPILE_TEST OFF CACHE BOOL "" FORCE)
set(USE_THREADS OFF CACHE BOOL "" FORCE)

set(INTGEMM_DONT_BUILD_TESTS ON)

set(USE_DOXYGEN OFF CACHE BOOL "" FORCE)
set(COMPILE_WITHOUT_EXCEPTIONS ON)
set(USE_RUY ON CACHE BOOL "" FORCE)
set(USE_RUY_SGEMM ON CACHE BOOL "" FORCE)
set(SSPLIT_COMPILE_LIBRARY_ONLY ON)
set(SSPLIT_USE_INTERNAL_PCRE2 ON CACHE BOOL "" FORCE)
set(AUTO_CPU_DETECT OFF CACHE BOOL "Disable CPU feature detection" FORCE)
message(STATUS "ANDROID_ABI: ${ANDROID_ABI}")
message(STATUS "CMAKE_SYSTEM_PROCESSOR: ${CMAKE_SYSTEM_PROCESSOR}")
message(STATUS "CMAKE_ANDROID_ARCH_ABI: ${CMAKE_ANDROID_ARCH_ABI}")
message(STATUS "CMAKE_SYSTEM_NAME: ${CMAKE_SYSTEM_NAME}")

if(ANDROID_ABI STREQUAL "arm64-v8a")
	set(BUILD_ARCH "armv8-a" CACHE STRING "Build architecture" FORCE)
endif()
# marian-dev builds some other targets
set(COMPILE_LIBRARY_ONLY ON CACHE BOOL "" FORCE)
set(COMPILE_UNIT_TESTS OFF CACHE BOOL "" FORCE)
set(ENABLE_CACHE_STATS OFF CACHE BOOL "" FORCE)
set(USE_MKL OFF CACHE BOOL "" FORCE)

# Reproducible builds
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -s")  # strip
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -s")
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -fdebug-prefix-map=${CMAKE_CURRENT_SOURCE_DIR}=.")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fdebug-prefix-map=${CMAKE_CURRENT_SOURCE_DIR}=.")
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -Wno-builtin-macro-redefined")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -Wno-builtin-macro-redefined")
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -ffunction-sections -fdata-sections -fvisibility=hidden")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fdata-sections -ffunction-sections -fvisibility=hidden -fvisibility-inlines-hidden")

# For reproducible builds
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,--build-id=none")
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,--hash-style=gnu")
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,--gc-sections")
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,--strip-all")
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,--exclude-libs,ALL")
# sort strings table
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,--sort-section=name")
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,--sort-common")
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,relro")
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,now")
# Marian stamps its git revision into a header with `git log`, remade whenever its submodule's
# .git/logs/HEAD changes. The build box receives the sources without .git and has no git at
# all, so the rule can never run there. Give it the file it watches and a header newer than it,
# naming the revision by the submodule pin instead.
set(MARIAN_SRC ${CMAKE_CURRENT_SOURCE_DIR}/bergamot-translator/3rd_party/marian-dev)
set(MARIAN_GIT ${MARIAN_SRC}/.git)
if(EXISTS ${MARIAN_GIT} AND NOT IS_DIRECTORY ${MARIAN_GIT})
    file(READ ${MARIAN_GIT} MARIAN_GIT)
    string(REGEX REPLACE "gitdir: (.*)\n" "\\1" MARIAN_GIT ${MARIAN_GIT})
    if(NOT IS_ABSOLUTE ${MARIAN_GIT})
        get_filename_component(MARIAN_GIT "${MARIAN_SRC}/${MARIAN_GIT}" ABSOLUTE)
    endif()
endif()
if(NOT EXISTS ${MARIAN_GIT}/logs/HEAD)
    file(WRITE ${MARIAN_GIT}/logs/HEAD "")
    file(WRITE ${MARIAN_SRC}/src/common/git_revision.h "#define GIT_REVISION \"submodule pin\"\n")
endif()

add_subdirectory("bergamot-translator/")


include(${CMAKE_SOURCE_DIR}/cmake/cld2.cmake)

# Specifies libraries CMake should link to your target library. You
# can link libraries from various origins, such as libraries defined in this
# build script, prebuilt third-party libraries, or Android system libraries.
target_link_libraries(${CMAKE_PROJECT_NAME}
        PRIVATE
        bergamot-translator
        android
        log
        cld2
)
