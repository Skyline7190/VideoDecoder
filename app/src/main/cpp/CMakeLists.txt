cmake_minimum_required(VERSION 3.4.1)

# 支持gnu++11
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=gnu++11")
project(native-lib)

# 1. 定义so库和头文件所在目录，方面后面使用
set(ffmpeg_lib_dir ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI})
set(ffmpeg_head_dir ${CMAKE_SOURCE_DIR}/)
# OpenGL ES和EGL库
find_library(egl-lib EGL)
find_library(gles-lib GLESv3)
find_library(android-lib android)
# 2. 添加头文件目录
include_directories(${ffmpeg_head_dir}/include)

# 添加 native-lib.cpp 作为主要的 JNI 文件
add_library(
        native-lib
        SHARED
        native-lib.cpp    # JNI入口
        Demuxer.cpp      # 解复用器
        Decoder.cpp      # 解码器
        queue.cpp        # 线程安全队列
        videoRender.cpp  # 视频渲染器
        AudioRender.cpp  # 音频渲染器

)

# 3. 添加ffmpeg相关的so库
add_library( ffmpeg
        SHARED
        IMPORTED )
set_target_properties( ffmpeg
        PROPERTIES IMPORTED_LOCATION
        ${ffmpeg_lib_dir}/libffmpeg.so )

# 查找代码中使用到的系统库
find_library( # Sets the name of the path variable.
        log-lib

        # Specifies the name of the NDK library that
        # you want CMake to locate.
        log )

# 指定编译目标库时，cmake要链接的库
target_link_libraries(
        # 指定目标库，native-lib 是在上面 add_library 中配置的目标库
        native-lib

        # 4. 连接 FFmpeg 相关的库

        ffmpeg
        ${egl-lib}
        ${gles-lib}
        ${android-lib}  # 添加 android 库
        android
        aaudio

        # Links the target library to the log library
        # included in the NDK.
        ${log-lib} )
