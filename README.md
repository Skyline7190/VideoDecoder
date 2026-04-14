# VideoDecoder

一个基于 **Android + JNI + FFmpeg + OpenGL ES + AAudio** 的原生播放器实验项目。  
核心目标是构建一条可控、可恢复、可扩展的多线程音视频处理链路。



## 1. 核心能力

- 本地视频文件选择与播放
- FFmpeg 解复用 + 软解码（音频/视频）
- OpenGL ES 渲染 YUV 视频帧到 `SurfaceView`
- AAudio 低延迟音频输出
- 播放控制：播放 / 暂停 / 倍速（变速不变调）
- 进度控制：SeekBar 拖动跳转（Seek）
- 导出 YUV 文件用于调试分析

## 2. 技术架构（重点）

项目采用 **Java UI 层 + Native 媒体内核** 的分层架构，Native 采用**多线程生产者-消费者管线**。

```text
┌──────────────────────────────────────────────────────────────────┐
│ Java UI 层 (MainActivity)                                       │
│ - 权限、文件选择、Surface 生命周期、控制事件、进度条轮询         │
└───────────────┬──────────────────────────────────────────────────┘
                │ JNI
┌───────────────▼──────────────────────────────────────────────────┐
│ Native 编排层 (native-lib.cpp)                                 │
│ - 全局状态: paused / seeking / clocks / playbackSpeed           │
│ - 线程拉起与协同: Demux / VideoDecode / AudioDecode / Render     │
│ - 状态握手: g_isSeeking + g_seekApplied                         │
└───────────────┬──────────────────────────────────────────────────┘
                │
  ┌─────────────┼───────────────────────────────────────────────────────┐
  │             │                                                       │
┌─▼──────────┐ ┌▼──────────────┐ ┌──────────────────┐ ┌───────────────▼──┐
│ Demux线程   │ │ VideoDecode线程 │ │ AudioDecode线程  │ │ Render线程         │
│ av_read_frame│ │ avcodec(video) │ │ avcodec(audio)+  │ │ EGL + OpenGL ES   │
│ -> packetQ   │ │ -> frameQ      │ │ swr + atempo     │ │ frameQ -> swap    │
└──────┬──────┘ └──────┬──────────┘ └──────────┬───────┘ └──────────────────┘
       │               │                       │
       │               │                       │
       │               └──────────────┐        │
       │                              │        │
       └───────────────PacketQueue────┘   AudioRenderer(AAudio)
```

### 2.1 分层职责

**Java 层（`MainActivity.java`）**
- 管理权限与 URI 文件选择，复制到可读路径
- 维护 `SurfaceView`，将 `Surface` 交给 native
- 处理播放控制：pause/resume/speed/seek
- 通过 `getDurationMs/getCurrentPositionMs` 轮询更新进度条与时间

**Native 编排层（`native-lib.cpp`）**
- 初始化 FFmpeg / EGL / AAudio
- 管理全局原子状态：
  - `g_paused`：暂停开关
  - `g_isSeeking`：seek 请求中
  - `g_seekApplied`：demux 已执行底层 seek
  - `g_audioClock/g_videoClock`：A/V 时钟
  - `g_playbackSpeed`：播放速度
- 负责线程生命周期、状态握手和资源释放

**功能模块层**
- `Demuxer.*`：文件拆包，分发音视频 packet
- `Decoder.*`：视频解码与帧队列生产
- `AudioRender.*`：AAudio 输出、硬件缓冲估算
- `videoRender.*`：GL 纹理更新与帧绘制
- `queue.*`：线程安全阻塞队列（含背压）

## 3. 线程模型与数据流

### 3.1 线程职责

1. **Demux 线程**  
   读取 `AVPacket`，分别推入视频/音频 `PacketQueue`。

2. **Video Decode 线程**  
   从视频 `PacketQueue` 取包，解码为 `AVFrame`，推入 `FrameQueue`。

3. **Audio Decode 线程**  
   从音频 `PacketQueue` 取包，解码 + `Swr` 重采样到 S16，再进入 `atempo`（变速不变调）后写入 `AudioRenderer`。

4. **Render 线程**  
   从 `FrameQueue` 取视频帧，按 A/V 差值进行节奏控制，执行 GL 渲染与 `eglSwapBuffers`。

### 3.2 队列与背压

`PacketQueue` 与 `FrameQueue` 基于 `mutex + condition_variable`：

- 上游快于下游时触发阻塞，避免无限堆积
- `pop()` 使用短周期等待，避免永久阻塞造成状态不可见
- `clear()/notifyAll()` 在 seek 场景主动唤醒等待线程

## 4. 时钟体系与音画同步

系统采用**音频主时钟**策略：

```cpp
exact_audio_pts = g_audioClock - pending_audio_duration
diff = video_pts - exact_audio_pts
```

- `g_audioClock`：最近写入音频链路的解码 PTS（us）
- `pending_audio_duration`：AAudio 硬件 + 软件队列尚未真正发声的延迟
- `g_videoClock`：当前视频帧 PTS（us）

同步策略：
- `diff > 0`：视频快，短睡等待（带上限）
- `diff <= 0`：视频慢，直接渲染追赶

该策略避免“长期压速”与“激进丢帧抖动”。

## 5. 播放控制架构

### 5.1 Seek（可恢复握手）

Seek 采用两阶段握手机制，避免跨线程竞态：

1. Java 调用 `seekToPosition(ms)`  
2. Native 置位：`g_isSeeking=true, g_seekApplied=false`  
3. Demux 执行底层 seek：`avformat_seek_file`（失败回退 `av_seek_frame`）  
4. Demux 置位：`g_seekApplied=true`  
5. Audio 线程检测握手完成后执行：
   - 清队列（audio/video/frame）
   - flush 解码器（音频，视频由视频线程自 flush）
   - 重置时钟到目标点
   - 清理 seek 标志恢复播放

并带有 seek 等待超时兜底，避免死等。

### 5.2 暂停/恢复（非阻塞主线程）

- `pauseDecoding/resumeDecoding` 以状态切换为主，不做重阻塞操作
- AAudio 回调在 `g_paused=true` 时直接静音返回
- 避免 UI 线程在 JNI 内等待音频状态而产生 ANR

### 5.3 倍速（变速不变调）

- 视频侧：同步等待时长按 `g_playbackSpeed` 缩放
- 音频侧：使用 FFmpeg `atempo` 滤镜链实现时间拉伸
  - 0.5x~3.0x 范围
  - 超过单级范围时自动拆分多级 `atempo`
- seek 时会重建 tempo graph，避免滤镜状态污染

## 6. 关键实现细节

### 6.1 视频渲染（`videoRender.cpp`）

- YUV 三平面纹理更新
- shader 做 YUV->RGB 转换
- 双缓冲交换输出到 `SurfaceView`

### 6.2 音频输出（`AudioRender.cpp`）

- AAudio 低延迟输出
- 内部 PCM 队列消费
- 暂停时静音输出
- 维护排队字节数用于延迟估算与同步

## 7. 关键文件地图

```text
app/
├─ src/main/java/com/example/videodecoder/MainActivity.java
├─ src/main/res/layout/activity_main.xml
├─ src/main/cpp/
│  ├─ native-lib.cpp          # JNI入口 + 编排层 + 音频解码/atempo
│  ├─ Demuxer.cpp/.h          # 拆包与seek应用
│  ├─ Decoder.cpp/.h          # 视频解码
│  ├─ queue.cpp/.h            # 线程安全队列
│  ├─ videoRender.cpp/.h      # EGL/OpenGL渲染
│  ├─ AudioRender.cpp/.h      # AAudio输出
│  └─ include/                # FFmpeg headers
└─ src/main/jniLibs/arm64-v8a/libffmpeg.so
```

## 8. JNI 接口清单

- `decodeVideo(String videoPath, String outputPath)`
- `setSurface(Surface surface)`
- `pauseDecoding() / resumeDecoding()`
- `setPlaybackSpeed(float speed)`
- `seekToPosition(int progressMs)`
- `getDurationMs() / getCurrentPositionMs()`
- `nativeReleaseAudio()`

## 9. 构建与运行

### 9.1 环境要求

- Android Studio
- Android SDK 35
- NDK + CMake（Gradle externalNativeBuild）
- Java 11

### 9.2 构建命令（Windows）

```powershell
.\gradlew.bat clean
.\gradlew.bat assembleDebug
.\gradlew.bat installDebug
```

## 10. 平台限制（重要）

项目仅支持 **`arm64-v8a`**：

- `app/build.gradle` 已固定 `abiFilters "arm64-v8a"`
- Native 依赖 `app/src/main/jniLibs/arm64-v8a/libffmpeg.so`

请在 arm64 真机或兼容环境运行，x86/x86_64 模拟器不在当前支持范围。

## 11. 后续演进建议

- 将控制逻辑升级为显式播放器状态机（Idle/Prepared/Playing/Paused/Seeking/Stopped）
- 为 seek/pause/speed 增加统一事件日志与指标
- 抽离时钟模块（AudioClock/VideoClock）提升可测试性
- 对比 `atempo` 与专业 time-stretch 库在音质与延迟上的权衡
