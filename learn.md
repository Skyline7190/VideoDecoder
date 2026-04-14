# VideoDecoder 项目学习笔记

## 项目概述

这是一个Android平台的视频解码器项目，结合Java UI层和C++ native层实现高性能视频解码。项目使用FFmpeg库进行音视频编解码，OpenGL ES进行视频渲染，AAudio进行音频播放。

## 1. 项目结构

### 1.1 目录结构

```
VideoDecoder/
├── .gradle/                    # Gradle缓存目录
├── .idea/                     # Android Studio项目配置
├── app/                       # 主应用模块
│   ├── .cxx/                  # C++编译配置
│   ├── build/                 # 构建输出目录
│   ├── src/                   # 源代码
│   │   ├── main/
│   │   │   ├── cpp/           # C++源代码
│   │   │   ├── java/          # Java源代码
│   │   │   │   └── com/example/videodecoder/
│   │   │   │       └── MainActivity.java
│   │   │   ├── res/           # 资源文件
│   │   │   │   ├── drawable/  # 图片资源
│   │   │   │   ├── layout/    # 布局文件
│   │   │   │   │   └── activity_main.xml
│   │   │   │   ├── mipmap-*/ # 应用图标
│   │   │   │   ├── values/    # 字符串、颜色等值
│   │   │   │   ├── values-night/ # 夜间模式资源
│   │   │   │   └── xml/       # XML配置文件
│   │   │   └── AndroidManifest.xml
│   │   └── androidTest/       # Android测试代码
│   ├── build.gradle           # 应用模块构建配置
│   ├── proguard-rules.pro     # 代码混淆规则
│   └── .gitignore
├── build/                     # 项目级构建输出
├── gradle/                    # Gradle wrapper目录
├── settings.gradle            # 项目设置
├── build.gradle               # 项目级构建配置
├── gradle.properties          # Gradle属性配置
├── gradlew                    # Gradle wrapper Unix脚本
├── gradlew.bat               # Gradle wrapper Windows脚本
└── local.properties          # 本地配置(SDK路径)
```

### 1.2 核心组件说明

#### Java层

- **MainActivity.java**: 主活动，包含视频解码和渲染的业务逻辑

#### C++层 (位于 app/src/main/cpp/)

- **native-lib.cpp**: JNI入口文件，连接Java和C++代码
- **Demuxer.h/cpp**: 解复用器，负责从视频文件中分离音视频流
- **Decoder.h/cpp**: 解码器，使用FFmpeg进行音视频解码
- **queue.h/cpp**: 线程安全队列，用于音视频数据传递
- **videoRender.h/cpp**: 视频渲染器，使用OpenGL ES进行视频渲染
- **AudioRender.h/cpp**: 音频渲染器，使用AAudio进行音频播放

## 2. 编译方式

### 2.1 Gradle构建系统

项目使用Gradle作为构建系统，版本管理通过 `gradle/libs.versions.toml` 文件进行统一管理：

#### 版本配置 (libs.versions.toml)

- **Android Gradle Plugin**: 8.9.1
- **compileSdk**: 35 (Android 14)
- **minSdk**: 26 (Android 8.0 Oreo)
- **targetSdk**: 35 (Android 14)
- **Java版本**: 11

#### 应用依赖 (app/build.gradle)

```gradle
dependencies {
    implementation libs.appcompat          // AndroidX AppCompat
    implementation libs.material           # Material Design组件
    implementation libs.constraintlayout  # ConstraintLayout布局
    testImplementation libs.junit         # JUnit测试框架
    androidTestImplementation libs.ext.junit      # Android扩展JUnit
    androidTestImplementation libs.espresso.core # Espresso UI测试
}
```

### 2.2 CMake Native构建

项目通过CMake构建C++代码，配置文件位于 `app/src/main/cpp/CMakeLists.txt`：

#### C++标准设置

```cmake
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=gnu++11")
```

#### FFmpeg库集成


1. **库路径配置**:
   - FFmpeg so库: `${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libffmpeg.so`
   - FFmpeg头文件: `${CMAKE_SOURCE_DIR}/include`
2. **源文件列表**:
   - native-lib.cpp (JNI入口)
   - Demuxer.cpp (解复用器)
   - Decoder.cpp (解码器)
   - queue.cpp (线程安全队列)
   - videoRender.cpp (视频渲染器)
   - audioRender.cpp (音频渲染器)
3. **链接库**:
   - FFmpeg (libffmpeg.so)
   - OpenGL ES (GLESv3)
   - EGL
   - Android (android-lib)
   - AAudio (低延迟音频)
   - Log (Android日志)

### 2.3 CPU架构配置

项目专门针对ARM64架构进行优化：

```gradle
defaultConfig {
    externalNativeBuild {
        cmake {
            abiFilters "arm64-v8a"
        }
    }
    ndk {
        abiFilters("arm64-v8a")
    }
}
```

### 2.4 构建特性

- **View Binding**: 启用视图绑定，替代findViewById
- **代码混淆**: Release版本默认关闭混淆
- **非传递性R类**: 启用AndroidX的非传递性资源类

### 2.5 编译命令

在项目根目录下执行：

```bash
# Debug版本构建
./gradlew assembleDebug

# Release版本构建
./gradlew assembleRelease

# 安装到设备
./gradlew installDebug
```

## 3. 程序入口点

### 3.1 Android应用入口

#### AndroidManifest.xml配置

```xml
<application
    android:allowBackup="true"
    android:dataExtractionRules="@xml/data_extraction_rules"
    android:fullBackupContent="@xml/backup_rules"
    android:icon="@mipmap/ic_launcher"
    android:label="@string/app_name"
    android:roundIcon="@mipmap/ic_launcher_round"
    android:supportsRtl="true"
    android:theme="@style/Theme.VideoDecoder"
    tools:targetApi="31">
    <activity
        android:name=".MainActivity"
        android:exported="true">
        <intent-filter>
            <action android:name="android.intent.action.MAIN" />
            <category android:name="android.intent.category.LAUNCHER" />
        </intent-filter>
    </activity>
</application>
```

**关键点：**

- `MainActivity` 是应用的启动Activity
- `android:exported="true"` 允许Activity被其他应用启动
- `LAUNCHER` 类别使应用出现在应用列表中

#### 权限声明

```xml
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"/>
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"/>
```

### 3.2 Java层入口 - MainActivity

#### 关键成员变量

```java
// UI组件
private TextView tv;
private SurfaceView surfaceView;
private SeekBar seekBar;
private Button selectVideoButton;
private Button decodeVideoButton;
private Button playButton;
private Button pauseButton;

// 播放控制
private volatile boolean isPlaying = true;
private Thread decodeThread;
private String videoPath;

// JNI方法声明
public native void decodeVideo(String videoPath, String outputPath);
public native void setSurface(Surface surface);
public native String stringFromJNI();
public native void pauseDecoding();
public native void resumeDecoding();
public native void nativeReleaseAudio();
```

#### onCreate()方法执行流程


1. **加载布局**: `setContentView(R.layout.activity_main)`
2. **初始化UI组件**:
   - SeekBar设置监听器（拖动进度条控制）
   - 速度控制按钮（0.5x, 1x, 2x, 3x）
   - 播放/暂停按钮
3. **SurfaceView配置**:
   - 设置SurfaceHolder.Callback
   - surfaceCreated时将Surface传递给native层
4. **按钮事件监听**:
   - 选择视频按钮：权限检查 + 打开文件选择器
   - 解码视频按钮：权限检查 + 启动解码线程
5. **加载native库**: `System.loadLibrary("native-lib")`

### 3.3 JNI层入口 - native-lib.cpp

#### 核心头文件引入

```cpp
#include <jni.h>
#include <android/log.h>
#include <thread>
#include "Demuxer.h"
#include "Decoder.h"
#include "queue.h"
#include "videoRender.h"
#include "AudioRender.h"
// FFmpeg相关
#include <libavformat/avformat.h>
#include <libavcodec/avcodec.h>
// OpenGL ES相关
#include <EGL/egl.h>
#include <GLES3/gl3.h>
#include <android/native_window.h>
```

#### 全局变量声明

```cpp
// Surface相关
ANativeWindow* g_nativeWindow = nullptr;

// 播放控制
std::atomic<bool> g_decodingFinished{false};
std::atomic<bool> g_paused{false};
std::atomic<int64_t> g_pauseTime{0};

// 音频相关
AudioRenderer* g_audioRenderer = nullptr;
std::atomic<bool> g_audioDecodingFinished{false};
std::atomic<int64_t> g_audioClock{0};
std::atomic<int64_t> g_videoClock{0};

// 进度控制
std::atomic<int64_t> g_durationMs{0};
std::atomic<int64_t> g_currentPositionMs{0};
```

#### EGL初始化流程


1. **初始化EGLDisplay**: `eglGetDisplay(EGL_DEFAULT_DISPLAY)`
2. **创建EGL配置**: 指定RGB、Alpha、深度缓冲等属性
3. **创建EGLSurface**: 基于ANativeWindow创建渲染表面
4. **创建EGLContext**: 创建OpenGL ES 2.x上下文

### 3.4 应用启动流程

```
1. 用户点击应用图标
   ↓
2. Android系统启动MainActivity
   ↓
3. MainActivity.onCreate()执行
   ├─ 加载布局文件
   ├─ 初始化UI组件
   ├─ 设置SurfaceView回调
   ├─ 注册按钮事件监听器
   └─ 加载native-lib库
   ↓
4. 用户选择视频文件
   ├─ 检查存储权限
   ├─ 打开文件选择器
   └─ 复制文件到临时目录
   ↓
5. 用户点击解码按钮
   ├─ 检查写入权限
   ├─ 创建解码线程
   └─ 调用native decodeVideo方法
   ↓
6. Native层处理
   ├─ 初始化EGL
   ├─ 创建Demuxer
   ├─ 创建Decoder
   ├─ 创建Renderer
   └─ 开始解码循环
```

### 3.5 关键初始化函数

#### JNI_OnLoad（如果存在）

负责在加载库时进行初始化工作，如：

- 注册JNI方法
- 初始化FFmpeg
- 设置日志系统

#### stringFromJNI()

简单的JNI方法示例，返回字符串到Java层。

#### setSurface(Surface surface)

将Java层的Surface对象转换为Native层的ANativeWindow，用于视频渲染。

## 4. 核心业务逻辑

### 4.1 整体架构

项目采用生产者-消费者模式的多线程架构，将视频解码流程分为以下几个核心模块：

```
视频文件
    ↓
[解复用线程] → 分离 → 视频数据包队列 → 视频解码线程 → YUV帧队列 → 渲染线程 → 显示
                ↓
               音频数据包队列 → 音频解码线程 → 重采样 → AudioRenderer → 扬声器
```

### 4.2 解复用逻辑 (Demuxer)

#### 功能职责

- 从视频文件中分离视频和音频数据包
- 使用FFmpeg的`av_read_frame`读取数据包
- 对数据包进行引用计数拷贝后压入对应队列

#### 关键实现

```cpp
class Demuxer {
private:
    AVFormatContext* formatContext;
    int videoStreamIndex;
    int audioStreamIndex;

public:
    // 单队列模式：只提取视频流
    int demux(const char* url, PacketQueue* videoQueue);

    // 双队列模式：同时提取视频流和音频流
    int demux(const char* url, PacketQueue* videoQueue, PacketQueue* audioQueue);
};
```

#### 数据流向

```
视频文件 → AVFormatContext → av_read_frame() →
视频包 → videoPacketQueue
音频包 → audioPacketQueue
```

### 4.3 解码逻辑 (Decoder)

#### 功能职责

- 将编码后的数据包解码为原始帧数据
- 使用FFmpeg的`avcodec_send_packet`和`avcodec_receive_frame`
- 支持暂停功能，通过全局原子变量控制
- 解码后的YUV数据同时写入文件和推送到帧队列

#### 关键流程

```cpp
int Decoder::decode(PacketQueue* packetQueue, FrameQueue* frameQueue, const char* outputPath) {
    while (!g_decodingFinished) {
        // 暂停控制
        while (g_paused && !g_decodingFinished) {
            std::this_thread::sleep_for(std::chrono::milliseconds(10));
        }

        // 从队列获取数据包
        AVPacket* packet = packetQueue->pop();
        if (!packet) break; // nullptr表示结束

        // 发送到解码器
        avcodec_send_packet(codecContext, packet);

        // 接收解码后的帧
        while (avcodec_receive_frame(codecContext, frame) == 0) {
            // 推送到帧队列
            frameQueue->push(frame);

            // 写入YUV文件
            if (outputPath) {
                writeYUVToFile(frame, outputPath);
            }
        }
    }
}
```

### 4.4 数据队列管理 (Queue)

#### PacketQueue（数据包队列）

```cpp
class PacketQueue {
private:
    std::queue<AVPacket*> queue;
    std::mutex mutex;
    std::condition_variable condVar;
    bool demuxFinished = false;

public:
    void push(AVPacket* packet);        // 线程安全压入
    AVPacket* pop();                    // 阻塞式弹出
    void setDemuxFinished(bool finished);
};
```

#### FrameQueue（帧队列）

```cpp
class FrameQueue {
private:
    std::queue<AVFrame*> queue;
    std::mutex mutex;
    std::condition_variable condVar;
    size_t maxSize = 10;                // 防止内存溢出
    bool terminated = false;

public:
    void push(AVFrame* frame);         // 带大小限制的压入
    AVFrame* pop();                     // 阻塞式弹出
    void setMaxSize(size_t size);
};
```

### 4.5 视频渲染流程 (VideoRender)

#### OpenGL ES渲染管线


1. **EGL环境初始化**
   - 获取Display、配置、Surface、Context
   - 绑定ANativeWindow
2. **着色器程序**
   - 顶点着色器：传递纹理坐标
   - 片段着色器：YUV到RGB转换

#### 关键渲染步骤

```cpp
void VideoRenderer::renderFrame(AVFrame* frame) {
    // 1. 清除屏幕
    glClear(GL_COLOR_BUFFER_BIT);

    // 2. 使用着色器程序
    glUseProgram(shaderProgram);

    // 3. 更新YUV纹理
    updateYUVTextures(frame);

    // 4. 设置uniform变量
    glUniform1i(yTextureLoc, 0);
    glUniform1i(uTextureLoc, 1);
    glUniform1i(vTextureLoc, 2);

    // 5. 绘制四边形
    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);

    // 6. 交换缓冲区
    eglSwapBuffers(display, surface);
}
```

#### YUV转RGB算法（片段着色器）

```glsl
const vec3 YUVtoRGB = vec3(
    1.0, 1.0, 1.0,
    0.0, -0.344, 1.772,
    1.402, -0.714, 0.0
);

vec3 yuv2rgb(vec3 yuv) {
    return yuv * YUVtoRGB;
}
```

### 4.6 音频播放流程 (AudioRender)

#### AAudio低延迟配置

```cpp
AAudioStreamBuilder* builder;
AAudio_createStreamBuilder(&builder);

// 设置低延迟模式
AAudioStreamBuilder_setPerformanceMode(builder, AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
AAudioStreamBuilder_setDirection(builder, AAUDIO_DIRECTION_OUTPUT);
AAudioStreamBuilder_setSharingMode(builder, AAUDIO_SHARING_MODE_EXCLUSIVE);
```

#### 音视频同步机制

```cpp
AAudioCallbackResult dataCallback(AAudioStream* stream, void* userData, void* audioData, int32_t numFrames) {
    // 获取当前时间
    int64_t currentTime = getTimestamp();

    // 基于PTS的同步逻辑
    if (currentTime < framePTS - threshold) {
        // 还没到播放时间，填充静音
        return AAUDIO_CALLBACK_RESULT_CONTINUE;
    }

    // 播放音频数据
    memcpy(audioData, frameData, frameSize);
    return AAUDIO_CALLBACK_RESULT_CONTINUE;
}
```

### 4.7 音视频同步机制

#### 同步策略

- **时钟基准**：使用PTS（Presentation Timestamp）
- **全局时钟变量**：

  ```cpp
  std::atomic<int64_t> g_audioClock{0};
  std::atomic<int64_t> g_videoClock{0};
  std::atomic<int64_t> g_currentPositionMs{0};
  ```

#### 同步算法实现

```cpp
void synchronizeAudioVideo() {
    int64_t videoPTS = g_videoClock.load();
    int64_t audioPTS = g_audioClock.load();
    int64_t diff = videoPTS - audioPTS;

    if (diff > 40000) {  // 视频比音频快超过40ms
        // 视频等待
        std::this_thread::sleep_for(std::chrono::microseconds(diff));
    } else if (diff < -40000) {  // 音频比视频快超过40ms
        // 丢弃视频帧
        dropVideoFrame();
    }
}
```

### 4.8 线程模型和并发控制

#### 线程分工


1. **解复用线程**：读取文件，分发数据包
2. **视频解码线程**：解码视频数据包
3. **音频解码线程**：解码音频数据包并播放
4. **渲染线程**：渲染视频帧

#### 并发控制机制

- **原子变量**：`std::atomic`控制播放状态
- **互斥锁**：保护队列等共享资源
- **条件变量**：实现线程间通信
- **队列大小限制**：防止内存溢出

#### 暂停/恢复实现

```cpp
// 暂停
void pauseDecoding() {
    g_paused = true;
    g_pauseTime = getCurrentTime();

    // 暂停音频流
    if (g_audioRenderer) {
        g_audioRenderer->pause();
    }
}

// 恢复
void resumeDecoding() {
    int64_t pauseDuration = getCurrentTime() - g_pauseTime;

    // 调整时钟补偿暂停时间
    g_audioClock += pauseDuration;
    g_videoClock += pauseDuration;

    g_paused = false;

    // 恢复音频流
    if (g_audioRenderer) {
        g_audioRenderer->resume();
    }
}
```

### 4.9 关键设计亮点


1. **模块化设计**：各组件职责单一，低耦合高内聚
2. **线程安全**：使用C++11线程原语确保数据安全
3. **内存管理**：智能指针和引用计数管理FFmpeg对象
4. **性能优化**：
   - 队列缓冲避免阻塞
   - 纹理复用减少OpenGL调用
   - AAudio低延迟音频
5. **错误处理**：完善的资源清理机制

## 5. 具体函数实现细节

### 5.1 Demuxer::demux() - 解复用实现

#### 函数签名

```cpp
// 单队列版本
void Demuxer::demux(AVFormatContext* fmt_ctx, int video_stream_index, PacketQueue& queue);

// 双队列版本
void Demuxer::demux(AVFormatContext* fmt_ctx,
                    int video_stream_index, PacketQueue& video_queue,
                    int audio_stream_index, PacketQueue& audio_queue);
```

#### 实现逻辑

```cpp
void Demuxer::demux(AVFormatContext* fmt_ctx, int video_stream_index, PacketQueue& queue) {
    AVPacket* packet = av_packet_alloc();

    while (av_read_frame(fmt_ctx, packet) >= 0) {
        if (packet->stream_index == video_stream_index) {
            // 创建包引用，避免原始包被释放
            AVPacket* pkt = av_packet_alloc();
            av_packet_ref(pkt, packet);

            // 推入队列并通知等待线程
            queue.push(pkt);
            queue.notifyAll();
        }
        av_packet_unref(packet);
    }

    // 发送结束信号
    queue.push(nullptr);
    queue.notifyAll();
    av_packet_free(&packet);
}
```

#### 关键API说明

- **av_read_frame()**: 读取下一个数据包，返回0表示成功
- **av_packet_ref()**: 创建包的引用，增加引用计数
- **av_packet_unref()**: 减少引用计数，为0时释放资源

### 5.2 Decoder::decode() - 解码实现

#### 函数签名

```cpp
void Decoder::decode(AVCodecContext* codec_ctx, FILE* yuv_file,
                     PacketQueue& queue, FrameQueue& frameQueue);
```

#### 实现逻辑

```cpp
void Decoder::decode(AVCodecContext* codec_ctx, FILE* yuv_file,
                     PacketQueue& queue, FrameQueue& frameQueue) {
    AVFrame* frame = av_frame_alloc();

    while (!g_decodingFinished) {
        // 暂停处理
        while (g_paused && !g_decodingFinished) {
            std::this_thread::sleep_for(std::chrono::milliseconds(10));
        }

        // 从队列获取数据包
        AVPacket* packet = queue.pop();
        if (!packet) break; // nullptr表示结束

        // 发送到解码器
        int ret = avcodec_send_packet(codec_ctx, packet);
        if (ret < 0) continue;

        // 循环接收解码后的帧
        while (avcodec_receive_frame(codec_ctx, frame) == 0) {
            // 写入YUV文件
            if (yuv_file) {
                fwrite(frame->data[0], 1, frame->linesize[0] * frame->height, yuv_file); // Y
                fwrite(frame->data[1], 1, frame->linesize[1] * frame->height/2, yuv_file); // U
                fwrite(frame->data[2], 1, frame->linesize[2] * frame->height/2, yuv_file); // V
            }

            // 推送到渲染队列（非暂停状态）
            if (!g_paused) {
                AVFrame* clonedFrame = av_frame_clone(frame);
                frameQueue.push(clonedFrame);
            }

            g_videoClock = frame->pts * av_q2d(codec_ctx->time_base) * 1000000;
        }

        av_packet_free(&packet);
    }

    av_frame_free(&frame);
}
```

#### 解码流程说明


1. **发送-接收模式**: 使用新API `avcodec_send_packet/avcodec_receive_frame`
2. **暂停控制**: 通过全局原子变量 `g_paused` 实现
3. **PTS处理**: 将时间基准转换为微秒存储

### 5.3 VideoRenderer::renderFrame() - OpenGL ES渲染

#### 着色器源码

```glsl
// 顶点着色器
const char* vertexShaderSource =
    "attribute vec4 a_position;\n"
    "attribute vec2 a_texCoord;\n"
    "varying vec2 v_texCoord;\n"
    "void main() {\n"
    "    gl_Position = a_position;\n"
    "    v_texCoord = a_texCoord;\n"
    "}\n";

// 片段着色器（YUV转RGB）
const char* fragmentShaderSource =
    "precision mediump float;\n"
    "varying vec2 v_texCoord;\n"
    "uniform sampler2D y_texture;\n"
    "uniform sampler2D u_texture;\n"
    "uniform sampler2D v_texture;\n"
    "void main() {\n"
    "    float y = texture2D(y_texture, v_texCoord).r;\n"
    "    float u = texture2D(u_texture, v_texCoord).r - 0.5;\n"
    "    float v = texture2D(v_texture, v_texCoord).r - 0.5;\n"
    "    float r = y + 1.402 * v;\n"
    "    float g = y - 0.344 * u - 0.714 * v;\n"
    "    float b = y + 1.772 * u;\n"
    "    gl_FragColor = vec4(r, g, b, 1.0);\n"
    "}\n";
```

#### 渲染实现

```cpp
void Renderer::renderFrame(AVFrame* frame) {
    if (!eglContext || !frame) return;

    // 清除屏幕
    glClear(GL_COLOR_BUFFER_BIT);
    glUseProgram(shaderProgram);

    // 更新YUV纹理
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, yTexture);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_LUMINANCE, frame->width, frame->height,
                 0, GL_LUMINANCE, GL_UNSIGNED_BYTE, frame->data[0]);

    glActiveTexture(GL_TEXTURE1);
    glBindTexture(GL_TEXTURE_2D, uTexture);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_LUMINANCE, frame->width/2, frame->height/2,
                 0, GL_LUMINANCE, GL_UNSIGNED_BYTE, frame->data[1]);

    glActiveTexture(GL_TEXTURE2);
    glBindTexture(GL_TEXTURE_2D, vTexture);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_LUMINANCE, frame->width/2, frame->height/2,
                 0, GL_LUMINANCE, GL_UNSIGNED_BYTE, frame->data[2]);

    // 设置纹理采样器
    glUniform1i(yTextureLoc, 0);
    glUniform1i(uTextureLoc, 1);
    glUniform1i(vTextureLoc, 2);

    // 绘制
    glBindBuffer(GL_ARRAY_BUFFER, vbo);
    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);

    // 交换缓冲区
    eglSwapBuffers(display, surface);
}
```

### 5.4 AudioRenderer数据回调 - AAudio播放

#### 回调函数实现

```cpp
aaudio_data_callback_result_t AudioRenderer::dataCallback(
    AAudioStream* stream, void* userData, void* audioData, int32_t numFrames) {

    AudioRenderer* renderer = static_cast<AudioRenderer*>(userData);

    // 获取音频参数
    int32_t channelCount = AAudioStream_getChannelCount(stream);
    int32_t samplesPerFrame = AAudioStream_getSamplesPerFrame(stream);
    int32_t bytesPerFrame = channelCount * samplesPerFrame * sizeof(int16_t);
    int32_t totalBytes = numFrames * bytesPerFrame;

    // 清零输出缓冲区
    memset(audioData, 0, totalBytes);

    // 音频同步：检查是否到了播放时间
    if (renderer->startTime > 0) {
        int64_t currentTime = getTimestampInNanos();
        if (currentTime < renderer->startTime - 1000000) { // 提前1ms
            return AAUDIO_CALLBACK_RESULT_CONTINUE;
        }
    }

    // 填充音频数据
    int32_t bytesFilled = 0;
    while (bytesFilled < totalBytes) {
        AVPacket* packet = renderer->audioQueue.front();
        if (!packet) break;

        int32_t packetSize = packet->size - renderer->audioOffset;
        int32_t bytesToCopy = std::min(packetSize, totalBytes - bytesFilled);

        // 复制数据并应用音量
        int16_t* src = (int16_t*)(packet->data + renderer->audioOffset);
        int16_t* dst = (int16_t*)((uint8_t*)audioData + bytesFilled);
        int32_t samples = bytesToCopy / sizeof(int16_t);

        for (int i = 0; i < samples; i++) {
            dst[i] = (int16_t)(src[i] * renderer->volume);
        }

        bytesFilled += bytesToCopy;
        renderer->audioOffset += bytesToCopy;

        // 包处理完成，从队列移除
        if (renderer->audioOffset >= packet->size) {
            renderer->audioQueue.pop();
            renderer->audioOffset = 0;
        }
    }

    return AAUDIO_CALLBACK_RESULT_CONTINUE;
}
```

### 5.5 JNI主函数 - Java_com_example_videodecoder_MainActivity_decodeVideo

#### 函数签名

```cpp
JNIEXPORT void JNICALL
Java_com_example_videodecoder_MainActivity_decodeVideo(
    JNIEnv *env, jobject thiz, jstring video_path, jstring output_path);
```

#### 主要实现步骤

```cpp
JNIEXPORT void JNICALL
Java_com_example_videodecoder_MainActivity_decodeVideo(
    JNIEnv *env, jobject thiz, jstring video_path, jstring output_path) {

    // 1. 参数转换
    const char* videoPath = env->GetStringUTFChars(video_path, 0);
    const char* outputPath = env->GetStringUTFChars(output_path, 0);

    // 2. 打开视频文件
    AVFormatContext* formatContext = nullptr;
    if (avformat_open_input(&formatContext, videoPath, nullptr, nullptr) != 0) {
        // 错误处理
        return;
    }

    // 3. 查找音视频流
    int videoStreamIndex = av_find_best_stream(formatContext, AVMEDIA_TYPE_VIDEO, -1, -1, nullptr, 0);
    int audioStreamIndex = av_find_best_stream(formatContext, AVMEDIA_TYPE_AUDIO, -1, -1, nullptr, 0);

    // 4. 初始化解码器
    AVCodecContext* videoCodecContext = avcodec_alloc_context3(nullptr);
    avcodec_parameters_to_context(videoCodecContext, formatContext->streams[videoStreamIndex]->codecpar);
    avcodec_open2(videoCodecContext, avcodec_find_decoder(videoCodecContext->codec_id), nullptr);

    // 5. 初始化队列
    PacketQueue videoQueue, audioQueue;
    FrameQueue frameQueue;

    // 6. 启动工作线程
    std::thread demuxThread(Demuxer::demux, formatContext, videoStreamIndex, audioStreamIndex,
                           std::ref(videoQueue), std::ref(audioQueue));

    std::thread videoDecodeThread(Decoder::decode, videoCodecContext, nullptr,
                                 std::ref(videoQueue), std::ref(frameQueue));

    // 7. 初始化渲染环境
    initEGL();
    Renderer renderer;
    renderer.init(videoCodecContext->width, videoCodecContext->height);

    // 8. 渲染循环
    while (!g_decodingFinished) {
        AVFrame* frame = frameQueue.pop();
        if (!frame) break;

        // 音视频同步
        synchronizeAudioVideo(frame->pts);

        // 渲染帧
        renderer.renderFrame(frame);

        av_frame_free(&frame);
    }

    // 9. 清理资源
    demuxThread.join();
    videoDecodeThread.join();
    avformat_close_input(&formatContext);
    avcodec_free_context(&videoCodecContext);

    env->ReleaseStringUTFChars(video_path, videoPath);
    env->ReleaseStringUTFChars(output_path, outputPath);
}
```

### 5.6 错误处理和资源管理最佳实践

#### FFmpeg资源管理

```cpp
// 使用RAII包装FFmpeg对象
struct AVFormatContextPtr {
    AVFormatContext* ptr;
    AVFormatContextPtr() : ptr(nullptr) {}
    ~AVFormatContextPtr() { if (ptr) avformat_close_input(&ptr); }
};
```

#### 线程安全退出

```cpp
// 设置退出标志
g_decodingFinished = true;

// 唤醒所有等待线程
videoQueue.notifyAll();
frameQueue.notifyAll();

// 等待线程结束
demuxThread.join();
decodeThread.join();
renderThread.join();
```

## 6. 项目总结

### 6.1 技术栈总结

**核心技术**：

- **FFmpeg**: 音视频编解码库，处理解复用和解码
- **OpenGL ES**: GPU加速视频渲染，支持YUV到RGB转换
- **AAudio**: Android低延迟音频API
- **EGL**: OpenGL ES与Native窗口的桥梁
- **JNI**: Java与C++之间的通信桥梁

**开发语言**：

- **Java层**: Android UI和用户交互
- **C++层**: 核心音视频处理逻辑

### 6.2 架构优势


1. **模块化设计**
   - 解复用、解码、渲染功能独立
   - 便于维护和扩展
   - 符合单一职责原则
2. **多线程并行处理**
   - 充分利用多核CPU性能
   - 音视频并行处理提高效率
   - 生产者-消费者模式避免阻塞
3. **高性能渲染**
   - GPU硬件加速
   - 低延迟音频输出
   - 帧缓冲减少内存拷贝
4. **完善的同步机制**
   - 基于PTS的音视频同步
   - 支持暂停/恢复功能
   - 线程安全的数据交换

### 6.3 学习要点


1. **FFmpeg使用技巧**
   - 新版API使用：avcodec_send_packet/avcodec_receive_frame
   - 时间基转换：PTS与播放时间的换算
   - 资源管理：及时释放AVPacket和AVFrame
2. **OpenGL ES优化**
   - 着色器编程：YUV到RGB的GPU转换
   - 纹理管理：避免频繁创建和销毁
   - 状态管理：减少OpenGL状态切换
3. **Android Native开发**
   - JNI最佳实践：正确处理字符串转换
   - AAudio配置：低延迟模式设置
   - NDK线程管理：std::thread与Android生命周期
4. **并发编程**
   - 原子操作：无锁状态管理
   - 条件变量：线程间高效通信
   - 队列设计：缓冲区大小平衡

### 6.4 可扩展方向


1. **功能扩展**
   - 支持更多视频格式（H.265/VP9）
   - 添加字幕渲染功能
   - 实现倍速播放
   - 添加视频滤镜效果
2. **性能优化**
   - 使用MediaCodec硬件解码
   - 实现SurfaceView直接渲染
   - 优化内存池管理
   - 添加预加载机制
3. **代码改进**
   - 使用智能指针管理资源
   - 添加完整的错误处理
   - 实现日志系统
   - 增加单元测试

### 6.5 常见问题解决方案


1. **音视频不同步**
   - 检查PTS时间戳
   - 调整同步阈值
   - 考虑解码延迟
2. **内存泄漏**
   - 使用RAII管理资源
   - 检查FFmpeg对象释放
   - 监控线程退出
3. **性能问题**
   - 使用profiler定位瓶颈
   - 优化队列大小
   - 考虑异步操作
4. **兼容性问题**
   - 检查Android API版本
   - 处理不同设备差异
   - 添加权限检查

### 6.6 参考资料

- [FFmpeg官方文档](https://ffmpeg.org/documentation.html)
- [Android NDK开发指南](https://developer.android.com/ndk)
- [OpenGL ES 2.0编程指南](https://www.khronos.org/opengles/sdk/docs/man/)
- [AAudio API文档](https://developer.android.com/ndk/reference/group/audio)


---

## 结语

通过深入学习VideoDecoder项目，我们掌握了Android平台上音视频播放的核心技术。项目展示了如何整合FFmpeg、OpenGL ES和AAudio等强大的库来构建高性能的播放器。理解这些概念和实现细节，将帮助我们开发更复杂的多媒体应用。

记住，音视频开发是一个持续学习的领域，新的技术和标准不断涌现。保持好奇心，不断实践，才能在这个领域不断进步。


---



## 7. 音频渲染与音画同步核心机制深挖

本节专门聚焦于 VideoDecoder 项目中的音频渲染器（AudioRender.cpp）底层实现，以及 C++ Native 层中如何处理最复杂的音画同步（A/V Sync）逻辑。我们将深度剖析当前的实现方式、存在的缺陷以及未来的演进方案。

### 7.1 音频渲染底层架构 (基于 AAudio)

项目采用 Android 平台提供的高性能、低延迟音频 API —— **AAudio**。相较于 OpenSL ES，AAudio 在架构上更贴近底层驱动，能够显著降低音频缓冲延迟，非常适合音画同步要求高的场景。

#### 7.1.1 核心组件初始化
在 AudioRender.cpp 的 init() 中，主要配置了以下核心参数：
- **格式配置**：动态支持采样率（SampleRate）、声道数（Channels）和采样格式（通常重采样为 16-bit PCM，AAUDIO_FORMAT_PCM_I16）。
- **低延迟模式**：AAudioStreamBuilder_setPerformanceMode(builder, AAUDIO_PERFORMANCE_MODE_LOW_LATENCY)。这指示系统分配一个尽可能短的缓冲区，减少从写入到实际发声的时间差。
- **数据流向**：AAUDIO_DIRECTION_OUTPUT (播放设备)。
- **数据回调机制**：AAudioStreamBuilder_setDataCallback(builder, dataCallback, this)。这是整个音频播放的引擎。

#### 7.1.2 消费者模型：AAudio 数据回调 (dataCallback)
有别于阻塞式地向硬件写数据，AAudio 推荐使用 **异步回调机制（Pull 模型）**。
系统底层音频线程会定期触发 dataCallback 请求数据：
1. **静音填充补齐**：如果初始化未完成或者还没有到达配置的 udioStartTimeMs，回调会向 udioData 中填充 0 (memset) 以输出静音，保证音频流的持续运转不中断。
2. **从队列获取数据**：由于底层回调函数对性能要求极高（不能阻塞），代码中使用了 std::lock_guard 快速从 udioQueue 中读取 FFmpeg 解码后的 PCM 数据，然后将其 memcpy 到 AAudio 提供的缓冲区中。
3. **音量控制**：在输出前，遍历并乘以 olume 系数。
4. **消耗与截断**：如果缓冲队列中的数据（ytesToCopy）超过了系统当前需要的长度，代码会使用 uffer.erase() 切片，保留剩下的数据到下次回调。

### 7.2 当前的音视频同步机制 (以音频为基准)

音画同步的核心原则是“寻找一个基准时钟（Master Clock）”，通常有三种策略：视频同步到音频、音频同步到视频、两者同步到外部系统时钟。**本代码库目前采取的是“视频同步到音频”策略。**

#### 7.2.1 PTS（Presentation Time Stamp）的获取
在 
ative-lib.cpp 的解码循环中：
- **音频 PTS**：在音频解码线程中，拿到一帧音频后，将其 PTS 通过时间基（	ime_base）换算为微秒（AV_TIME_BASE_Q），存入全局原子变量 g_audioClock。
  `cpp
  g_audioClock.store(av_rescale_q(frame->pts, ...));
  `
- **视频 PTS**：在视频渲染线程中，从队列拿到视频帧后，同样换算为其微秒级 PTS 存入 g_videoClock。

#### 7.2.2 视频渲染线程的对齐逻辑
视频渲染线程 (enderThread) 在调用 OpenGL eglSwapBuffers 绘制之前，执行同步比对：
`cpp
int64_t audio_pts = g_audioClock.load();
int64_t video_pts = g_videoClock.load();
int64_t diff = video_pts - audio_pts;
if (diff > 0) { // 视频比音频快，需要等待
    std::this_thread::sleep_for(std::chrono::microseconds(diff));
}
`
**逻辑解释**：如果视频的时间戳大于当前音频的时间戳（视频跑得太快了），则让视频渲染线程休眠（Sleep） diff 微秒，强行等音频追上来。

### 7.3 现有同步逻辑的缺陷剖析

尽管初步实现了视频等待音频的功能，但当前代码（参考 问题.md）存在几个致命的体验缺陷，会导致“越播越卡”、“微小抖动”以及“视频慢慢落后”：

1. **单向同步的灾难**：
   当前代码只处理了 diff > 0（视频超前需要等）。但如果 diff < 0（视频解码或渲染太慢，落后于音频），代码中**完全没有处理**！这就导致视频不会去“跳帧（Drop Frame）”追赶，只能慢慢播，最终音画差距越来越大。
2. **错误的音频时钟基准 (Buffer Delay 丢失)**：
   g_audioClock 是在**解码线程**解析出音频数据时就立即更新的。但这些音频数据需要先放进 udioQueue，然后再被 AAudio 的底层缓冲区拿走，最后才从扬声器发出声音。这中间存在巨大的“缓冲延迟”。解码 PTS 并不等于“当前人耳听到的时间”。
3. **双重 Sleep 累积误差**：
   在源码中，除了 diff 的 sleep 外，后面还跟着一段计算 rameDuration（帧率间隔）的 sleep：
   `cpp
   if (elapsed < frameDuration) {
       sleep_for(frameDuration - elapsed);
   }
   `
   这就造成了“同步等待”和“帧率等待”的错误叠加。视频每帧实际消耗的时间大于了其理应停留的时间，进一步导致视频落后。
4. **无死区（Threshold）导致的微抖动**：
   diff > 0 就严格 sleep。实际上系统调度和解码都有几毫秒的自然波动。没有设定阈值（比如 ±20ms 视为同步）会导致线程不断微小休眠，画面产生抖动感。

### 7.4 进阶音画同步演进方案

为了解决上述问题，项目规划了更完善的音画同步重构方案（参考 音画同步解决方案.md），这是多媒体播放器的必经之路：

#### 方案一：动态音频缓冲时钟补偿
彻底纠正音频时钟。利用 AAudio 提供的内部 API 减去底层缓冲堆积的耗时：
`cpp
// 实际播放出去的帧 = 写入队列的帧 - AAudio底层还没读的帧
int64_t framesWritten = AAudioStream_getFramesWritten(stream);
int64_t framesRead = AAudioStream_getFramesRead(stream);
int64_t framesPending = framesWritten - framesRead;
int64_t bufferDuration = (framesPending * 1000000) / sampleRate;

// 当前真正发出声音的精准时间戳
int64_t exact_audio_clock = g_audioClock.load() - bufferDuration;
`

#### 方案二：完善的阈值（Threshold）与丢帧（Drop）机制
替换掉原有的无脑 Sleep，引入双向比对：
`cpp
int64_t diff = frame_pts - exact_audio_clock;

if (abs(diff) < 40000) {  
    // [死区] 相差在40ms内，人耳无法察觉，直接渲染，不纠偏
    renderFrame();
} else if (diff > 40000) {  
    // 视频太快了
    std::this_thread::sleep_for(std::chrono::microseconds(diff - 40000));
    renderFrame();
} else {  
    // diff < -40000，视频太慢了（落后超过40ms）
    if (diff < -100000) {
        // 落后超过100ms，必须马上丢帧（丢弃当前画面，不渲染，直接解码下一帧）
        av_frame_free(&frame); 
        continue; // 跳过渲染逻辑
    } else {
        // 介于 -40ms 到 -100ms 之间，立即渲染这一帧，不产生额外等待
        renderFrame();
    }
}
`

#### 方案三：动态调整 AAudio 缓冲区 (Dynamic Buffer)
在高负载时，如果检测到音频解码速度跟不上，导致 ufferDuration 过小（出现破音爆音），可以通过 AAudioStream_setBufferSizeInFrames() 动态扩大硬件缓冲区；而在正常状态下维持较小的 buffer 以确保超低延迟。

### 7.5 小结
音视频同步是一个动态的**PID控制系统**。视频与音频在不同的线程跑，速度完全受制于当时系统的 CPU、GPU 负载和文件本身的时间基。通过引入“基准修正”、“死区容错”和“丢帧止损”三大机制，才能真正搭建出一个商业级的稳定播放器引擎。


