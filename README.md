# VideoDecoder

一个基于 **Android + JNI + FFmpeg + OpenGL ES + AAudio** 的原生播放器实验项目。  
核心目标是构建一条稳定的多线程音视频处理链路，并在 Android 端完成可控的音画同步、渲染与交互。

## 1. 核心能力

- 本地视频文件选择与播放
- FFmpeg 解复用 + 软解码（音频/视频）
- OpenGL ES 渲染 YUV 视频帧到 `SurfaceView`
- AAudio 低延迟音频输出
- 播放控制：播放 / 暂停 / 倍速
- 进度控制：SeekBar 拖动跳转（Seek）
- 输出 YUV 文件用于调试分析

## 2. 技术架构总览

项目采用 **Java UI 层 + Native 媒体内核** 的双层架构：

```text
UI/交互层 (Java)
  MainActivity
    │ JNI
    ▼
Native 控制层 (native-lib.cpp)
  ├─ Demux 线程：Demuxer.cpp
  ├─ Video Decode 线程：Decoder.cpp
  ├─ Audio Decode 线程：native-lib.cpp + Swr
  ├─ Render 线程：videoRender.cpp + EGL/OpenGL ES
  └─ Audio Output：AudioRender.cpp + AAudio
```

### 2.1 Java 层职责

`app/src/main/java/com/example/videodecoder/MainActivity.java`

- 管理权限与视频 URI 选择
- 创建 `SurfaceView` 并将 `Surface` 传给 Native
- 驱动播放控制（暂停/恢复/倍速/Seek）
- 接收 Native 回调结果并更新 UI

### 2.2 Native 层职责

`app/src/main/cpp/native-lib.cpp` 是核心编排器，负责：

- 初始化 FFmpeg/解码器/渲染器
- 管理全局播放状态（暂停、倍速、时钟、Seek 标志）
- 拉起并协调多线程生产者-消费者管线
- 实现音视频同步策略与资源释放

## 3. 媒体处理管线（关键设计）

### 3.1 线程模型

1. **Demux 线程**  
   调用 `av_read_frame` 拆包，分别投递到视频包队列与音频包队列。

2. **视频解码线程**  
   从视频包队列取 `AVPacket`，解码成 `AVFrame`，写入帧队列。

3. **音频解码线程**  
   从音频包队列取包，解码后用 `SwrContext` 重采样为 S16，再交给 `AudioRenderer`。

4. **渲染线程**  
   创建 EGL 上下文，循环消费视频帧并执行 OpenGL ES 渲染，再 `eglSwapBuffers`。

### 3.2 队列与背压（Backpressure）

`queue.h / queue.cpp` 使用 `std::mutex + std::condition_variable` 实现阻塞队列：

- `PacketQueue`：限制队列上限，避免 Demux 过快导致内存膨胀
- `FrameQueue`：队列满时阻塞解码线程，避免“解码跑飞”挤掉可渲染帧

这套机制是稳定播放的基础，保证上游不会无限制冲垮下游。

## 4. 音画同步策略

同步以**音频时钟**为主时钟，视频按差值动态等待：

1. 音频侧维护解码时钟 `g_audioClock`
2. 通过 `AudioRenderer::getPendingAudioDurationUs()` 估算尚未真实发声的积压时长
3. 计算近似“正在发声”的音频时间：

```cpp
exact_audio_pts = g_audioClock - pending_audio_duration
diff = video_pts - exact_audio_pts
```

4. 同步决策：
- `diff > 0`：视频超前，短暂 sleep 等待音频（含单次等待上限）
- `diff <= 0`：视频落后，直接渲染追赶，避免激进丢帧造成观感抖动

该策略兼顾了同步精度与流畅性。

## 5. Seek / 暂停 / 倍速机制

### 5.1 Seek

- Java 层调用 `seekToPosition(progressMs)`
- Native 设置 `g_isSeeking` 与目标时间
- Demux 执行 `av_seek_frame`
- 各队列清空、解码器 flush、时钟重置后恢复播放

### 5.2 暂停 / 恢复

- `pauseDecoding()`：暂停标志 + 暂停 AAudio 输出
- `resumeDecoding()`：恢复标志 + 恢复 AAudio，并修正暂停期间时钟偏移

### 5.3 倍速

- `setPlaybackSpeed(float speed)` 更新全局倍速
- 同步等待时长按倍速缩放（高倍速下减少等待）

## 6. 渲染与音频实现细节

### 6.1 视频渲染

`videoRender.cpp` 负责：

- 构建 OpenGL ES Shader 程序（YUV -> RGB）
- 创建 Y/U/V 三平面纹理
- 每帧更新纹理并绘制到全屏四边形

### 6.2 音频输出

`AudioRender.cpp` 负责：

- 初始化 AAudio 流（低延迟模式）
- 在数据回调中从内部队列拉取 PCM 数据
- 维护队列积压字节计数，用于同步估算

## 7. 关键目录

```text
app/
├─ src/main/java/com/example/videodecoder/MainActivity.java
├─ src/main/cpp/
│  ├─ native-lib.cpp
│  ├─ Demuxer.cpp / Demuxer.h
│  ├─ Decoder.cpp / Decoder.h
│  ├─ queue.cpp / queue.h
│  ├─ videoRender.cpp / videoRender.h
│  ├─ audioRender.cpp / AudioRender.h
│  └─ include/ (FFmpeg headers)
└─ src/main/jniLibs/arm64-v8a/libffmpeg.so
```

## 8. 构建与运行

### 8.1 环境要求

- Android Studio
- Android SDK 35
- NDK + CMake（由 Gradle externalNativeBuild 驱动）
- Java 11

### 8.2 构建命令（Windows）

```powershell
.\gradlew.bat clean
.\gradlew.bat assembleDebug
.\gradlew.bat installDebug
```

## 9. 平台限制（重要）

项目当前仅支持 **`arm64-v8a`**：

- `app/build.gradle` 中已固定 `abiFilters "arm64-v8a"`
- Native 依赖 `app/src/main/jniLibs/arm64-v8a/libffmpeg.so`

请在 `arm64-v8a` 真机（或兼容环境）运行，x86/x86_64 模拟器不在当前支持范围。

## 10. JNI 接口清单

`MainActivity` 对应 Native 接口：

- `decodeVideo(String videoPath, String outputPath)`
- `setSurface(Surface surface)`
- `pauseDecoding() / resumeDecoding()`
- `setPlaybackSpeed(float speed)`
- `seekToPosition(int progressMs)`
- `nativeReleaseAudio()`

## 11. 可扩展方向

- 硬解路径（MediaCodec）与软解路径统一调度
- 更精细的 A/V 漂移统计与自适应同步参数
- 音频独立时钟对象与视频时钟对象封装
- 帧丢弃策略、重复帧策略与场景化切换
- 播放器状态机（Idle/Prepared/Playing/Paused/Seeking/Stopped）工程化
