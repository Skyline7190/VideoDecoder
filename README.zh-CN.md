# VideoDecoder

[English](README.md) | **中文**

VideoDecoder 是一个基于 **Android + JNI + FFmpeg + OpenGL ES + AAudio** 的原生播放器实验项目。它的重点不是封装系统播放器，而是把解复用、音视频解码、音频输出、OpenGL 渲染和播放控制拆开，形成一条可观察、可调试、可继续演进的 native 播放链路。

---

## 多 Agent 协作演进系统

本项目的演进过程由一个多 Agent 协作驱动的跨域全栈系统支撑，目标是解决割裂技术栈下的架构协同难题：从底层 C++ 交叉编译、严格面向 `arm64-v8a` 的硬件级构建、多线程 `FFmpeg` 帧队列解析、`OpenGL / AAudio` 强时钟同步，到表层 `Jetpack Compose` UI、动态 Shader 光影和 Liquid Glass 流体玻璃质感，系统将这些原本相互割裂的技术层打通为一条可连续推理、修改和验证的工程链路。

在核心逻辑流上，系统依赖长链推理和跨栈追踪能力：前端 Agent 负责拆解开源动效库，分析其空间数学模型、弹性阻尼、光影折射和交互形变；跨栈 Agent 则深入 JNI 状态锁、native 播放时钟、异步队列消费、seek 唤醒和 EGL 渲染流水线。当接收到“倍速切换生硬”“拖拽阻滞”“视频黑边过大”“按钮不像液态玻璃”等模糊体感反馈时，系统会从顶层 Compose `Spring` 阻尼、gesture 状态机和 backdrop 渲染，一路追踪到底层 native 队列、Surface 生命周期、OpenGL viewport 和 AAudio 时钟同步，完成对齐调优。

这套协作方式把跨环境代码修改、NDK 构建链适配、Gradle 构建验证和 UI 体感迭代组织成闭环，使原本需要资深全栈架构师反复试错的 Android mixed-stack 开发流程，压缩为可快速迭代的分钟级工程演进过程。

---

## 核心能力

- 本地视频文件选择与播放
- FFmpeg 解复用与软解码，支持音频和视频流
- OpenGL ES 将 YUV 视频帧渲染到 `SurfaceView`
- 当前可见播放窗口已迁移到 `TextureView`，并同步 `SurfaceTexture` buffer 尺寸，保证 native 渲染窗口与 UI 区域一致
- AAudio 低延迟音频输出
- 播放、暂停、恢复、Seek、倍速播放
- 倍速播放使用 FFmpeg `atempo` filter，实现变速不变调
- 通过进度条轮询 native 播放进度
- **Liquid Glass** 风格的现代化 UI 交互（基于 Jetpack Compose 与 AndroidLiquidGlass 库）
- Material 3 卡片化 UI（24dp 圆角、语义化状态色、轻量动效）
- 四区域播放器排版：顶部文本/状态区、视频区、进度条区、按钮控制区
- Liquid Glass 进度条支持拖拽形变、松手 seek 和拖拽状态稳定释放

---

## 技术架构

项目采用 **Java UI 层 + Native 媒体内核** 的结构。Java 负责界面、文件选择和用户交互；C++ 负责媒体处理、线程编排、同步和渲染。

```mermaid
flowchart TB
    subgraph Java["Java UI 层"]
        Activity["MainActivity"]
        SurfaceView["SurfaceView"]
        TextureView["TextureView"]
        Controls["播放 / 暂停 / Seek / 倍速 / 进度"]
        PFD["ParcelFileDescriptor"]
    end

    subgraph JNI["JNI 边界"]
        DecodeApi["decodeVideo(videoPath, outputPath)"]
        SurfaceApi["setSurface(surface)"]
        ControlApi["pause / resume / seek / speed / release"]
    end

    subgraph Native["Native 媒体内核"]
        Session["PlaybackSession"]
        State["PlaybackState"]
        Window["NativeWindowHolder"]
        Demuxer["Demuxer"]
        VideoDecoder["Video Decoder"]
        AudioDecoder["Audio Decoder"]
        Renderer["OpenGL ES Renderer"]
        AudioRenderer["AudioRenderer (AAudio)"]
    end

    subgraph FFmpeg["FFmpeg"]
        Avio["Custom AVIO(fd)"]
        Format["AVFormatContext"]
        Codec["AVCodecContext"]
        Sws["sws_scale"]
        Swr["SwrConvert"]
        Atempo["atempo filter"]
    end

    Activity --> DecodeApi
    SurfaceView --> SurfaceApi
    TextureView --> SurfaceApi
    Controls --> ControlApi
    PFD --> DecodeApi

    DecodeApi --> Session
    SurfaceApi --> Window
    ControlApi --> State

    Session --> State
    Session --> Demuxer
    Session --> VideoDecoder
    Session --> AudioDecoder
    Session --> Renderer
    Session --> AudioRenderer

    Demuxer --> Avio --> Format
    VideoDecoder --> Codec --> Sws
    AudioDecoder --> Codec --> Swr --> Atempo
    Renderer --> Window
    AudioRenderer --> State
```

---

## UI 现代化改造 (Liquid Glass)

本项目已深度集成 [AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass) 库，实现 iOS/visionOS 风格的液态玻璃 UI。

### 核心集成点

1.  **混合布局架构**：
   - `activity_main.xml` 保留传统 View 体系的必要 ID，供 Java 层复用事件、状态和 JNI 调用。
   - 视频区域使用 `TextureView` 承载 native 渲染 Surface，并在尺寸变化时同步 `SurfaceTexture.setDefaultBufferSize(width, height)`。
   - `ComposeView` 作为全屏液态玻璃控制层，覆盖在播放器内容之上，负责顶部信息区、进度条区和按钮区。
   - 页面按四个区域稳定排版：顶部文本/状态区、视频区、进度条区、按钮控制区。

2.  **Liquid Glass 交互**：
   - `LiquidControls.kt`：基于 `rememberLayerBackdrop()` 和 `drawBackdrop` 实现液态按钮。
   - `LiquidSlider.kt`：实现液态玻璃进度条，拖拽时直接跟手，松手后提交 seek。
   - `DampedDragAnimation.kt`：管理进度条拖拽形变、按压/释放动画和稳定释放。
   - `InteractiveHighlight.kt`：实现按压高光、拖拽尾迹和弹性形变（非线性位移 `tanh`、方向相关缩放 `atan2/cos/sin`）。
   - `DragGestureInspector.kt`：提供流畅的手势解析。

3.  **视觉统一**：
   - **全局配色**：`colors.xml` 和 `themes.xml` 引入 `liquid_*` 调色板，统一背景、卡片、描边。
   - **全页风格**：页面背景 (`bg_deadliner_surface.xml`)、Chip (`bg_deadliner_chip.xml`)、所有卡片和按钮 tint 统一到液态玻璃语言。
   - **呼吸脉冲**：Active 按钮带有 `rememberInfiniteTransition` 驱动的微弱呼吸动效，增强“活着的玻璃”质感。
   - **状态联动**：播放状态（PLAYING/PAUSED/IDLE/READY）精准映射到 `LiquidGlassHelper` 的 `isPlayingState` 和 `selectedSpeedState`，动态高亮当前激活按钮。
   - **透明玻璃按钮**：选择、解析、播放/暂停、倍速按钮统一为透明液态玻璃质感，避免蓝/红色块破坏播放器观感。

### 文件地图

```
app/
├─ src/main/java/com/example/videodecoder/
│  ├─ MainActivity.java                 # Java 入口，JNI 调用，旧 UI 控制
│  ├─ LiquidControls.kt              # Compose 液态玻璃控制面板（Select/Decode/Play/Pause/倍速）
│  ├─ LiquidSlider.kt                # 液态玻璃进度条
│  ├─ DampedDragAnimation.kt         # 拖拽形变与释放动画
│  ├─ LiquidGlassHelper.kt           # Java -> Compose 桥接层，状态同步
│  ├─ InteractiveHighlight.kt          # 按压高光、拖拽形变逻辑
│  ├─ DragGestureInspector.kt        # 手势解析工具
│  ├─ PlaybackInputPolicy.java
│  ├─ PlaybackTimeFormatter.java
│  └─ PlaybackUiPolicy.java
├─ src/main/res/layout/
│  └─ activity_main.xml              # 主布局，包含 ComposeView 容器
├─ src/main/res/values/
│  ├─ colors.xml                    # 全局配色（含 liquid_* 调色板）
│  ├─ themes.xml                   # 主题（统一到 liquid 语言）
│  └─ drawable/
│     ├─ bg_deadliner_surface.xml     # 液态渐变背景
│     ├─ bg_deadliner_chip.xml       # 玻璃感 Chip 背景
│     └─ bg_liquid_player_surface.xml # 深色播放器背景
└─ src/main/cpp/
    ├─ native-lib.cpp
    ├─ MediaInput.cpp/.h
    ├─ NativeEgl.cpp/.h
    ├─ NativeWindowHolder.cpp/.h
    ├─ ScopeExit.h
    ├─ JniStringChars.h
    ├─ Demuxer.cpp/.h
    ├─ Decoder.cpp/.h
    ├─ queue.cpp/.h
    ├─ videoRender.cpp/.h
    └─ AudioRender.cpp/.h
```

---

## 构建要求

- **Android Studio** 或命令行 Gradle
- **Android SDK 36** (compileSdk 36)
- **Kotlin 2.3.10**
- **Android NDK + CMake**
- **Java 11**
- **Gradle Wrapper** 使用仓库内 `gradlew` / `gradlew.bat`

### Windows
```powershell
.\gradlew.bat clean
.\gradlew.bat assembleDebug
```

### Unix/macOS
```bash
./gradlew clean
./gradlew assembleDebug
```

---

## 平台限制

项目当前仅支持 **`arm64-v8a`**：
- `app/build.gradle` 中固定了 `abiFilters "arm64-v8a"`。
- FFmpeg 预编译库位于 `app/src/main/jniLibs/arm64-v8a/libffmpeg.so`。
- **不要在 x86/x86_64 模拟器上测试**，运行时会找不到或无法加载 native FFmpeg 库。
- 请使用 arm64 真机或兼容的 arm64 环境验证播放。

---

## 播放线程模型

运行播放时会启动四条主要 native 线程：

1. Demux 线程：调用 `av_read_frame` 读取封装包，按 stream index 分发到音频/视频 `PacketQueue`。
2. Video Decode 线程：从视频队列取包解码，使用 `sws_scale` 转为紧密 `YUV420P`，按需写入调试 YUV 文件并推入 `FrameQueue`。
3. Audio Decode 线程：从音频队列取包解码，使用 `Swr` 转为 S16，再按播放速度进入 `atempo` 滤镜链，最后写入 AAudio 队列。
4. Render 线程：从 `FrameQueue` 取视频帧，根据音频时钟节奏控制渲染并执行 `eglSwapBuffers`。

```mermaid
flowchart LR
    Input["SAF Uri / fd"] --> DemuxThread["Demux 线程"]

    subgraph Session["PlaybackSession"]
        VideoPackets["video PacketQueue"]
        AudioPackets["audio PacketQueue"]
        Frames["FrameQueue"]
        AudioOut["AudioRenderer"]
    end

    DemuxThread --> VideoPackets
    DemuxThread --> AudioPackets

    VideoPackets --> VideoThread["Video Decode 线程"]
    VideoThread --> Convert["转换为紧密 YUV420P"]
    Convert --> Frames
    Convert -. "调试开关开启时" .-> YuvFile["output.yuv"]

    AudioPackets --> AudioThread["Audio Decode 线程"]
    AudioThread --> Resample["S16 重采样"]
    Resample --> Tempo["倍速 atempo"]
    Tempo --> AudioOut

    Frames --> RenderThread["Render 线程"]
    RenderThread --> Sync["根据音频时钟等待"]
    Sync --> GLES["YUV 纹理上传 + shader"]
    GLES --> Surface["TextureView / Surface"]

    AudioOut --> Speaker["设备音频输出"]
```

`PacketQueue` 和 `FrameQueue` 都带背压控制，避免 demux 或 decode 过快造成内存无限增长。Seek 或停止时会清空队列并唤醒等待线程，保证线程可以退出或恢复。

---

## 音画同步

当前同步策略以音频播放进度作为主参考。视频渲染时根据音频已提交 PTS 和 AAudio/软件队列中尚未播放的延迟，估算当前真实音频位置：

```cpp
exact_audio_pts = g_audioClock - pending_audio_duration
diff = video_pts - exact_audio_pts
```

```mermaid
sequenceDiagram
    participant A as Audio Decoder
    participant R as AudioRenderer
    participant V as Render Thread
    participant C as Clocks
    
    A->>R: writeData(pcm, samples)
    A->>C: 更新 g_audioClock
    V->>C: 读取 g_videoClock
    V->>R: getPendingAudioDurationUs()
    R-->>V: 尚未播放的音频延迟
    V->>V: exact_audio_pts = g_audioClock - pending_delay
    V->>V: diff = video_pts - exact_audio_pts
    alt 视频快于音频
        V->>V: 按倍速缩放 sleep
    else 视频不快于音频
        V->>V: 立即渲染追赶
    end
```

- `diff > 0`：视频快于音频，Render 线程短暂 sleep 等待。
- `diff <= 0`：视频不等待，继续渲染追赶音频。

倍速播放时，视频等待时长会按 `g_playbackSpeed` 缩放；音频侧通过 `atempo` 处理时间拉伸。

---

## 音画同步

当前同步策略以音频播放进度作为主参考。视频渲染时根据音频已提交 PTS 和 AAudio/软件队列中尚未播放的延迟，估算当前真实音频位置：

```cpp
exact_audio_pts = g_audioClock - pending_audio_duration
diff = video_pts - exact_audio_pts
```

```mermaid
sequenceDiagram
    participant A as Audio Decoder
    participant R as AudioRenderer
    participant V as Render Thread
    participant C as Clocks
    
    A->>R: writeData(pcm, samples)
    A->>C: 更新 g_audioClock
    V->>C: 读取 g_videoClock
    V->>R: getPendingAudioDurationUs()
    R-->>V: 尚未播放的音频延迟
    V->>V: exact_audio_pts = g_audioClock - pending_delay
    V->>V: diff = video_pts - exact_audio_pts
    alt 视频快于音频
        V->>V: 按倍速缩放 sleep
    else 视频不快于音频
        V->>V: 立即渲染追赶
    end
```

- `diff > 0`：视频快于音频，Render 线程短暂 sleep 等待。
- `diff <= 0`：视频不等待，继续渲染追赶音频。

倍速播放时，视频等待时长会按 `g_playbackSpeed` 缩放；音频侧通过 `atempo` 处理时间拉伸。

---

## Seek 与播放控制

Seek 使用两阶段握手机制：

1. Java 调用 `seekToPosition(ms)`。
2. Native 设置 `g_isSeeking=true` 和目标时间。
3. Demux 线程执行 `avformat_seek_file`，失败时回退到 `av_seek_frame`。
4. Demux 设置 `g_seekApplied=true`。
5. Audio/Video 链路清空旧队列、flush 解码器、重置时钟后恢复播放。

```mermaid
stateDiagram-v2
    [*] --> Playing
    Playing --> Seeking: seekToPosition(ms)
    Seeking --> DemuxSeek: Demuxer 执行 seek
    DemuxSeek --> FlushQueues: 清空 PacketQueue / FrameQueue
    FlushQueues --> FlushDecoders: avcodec_flush_buffers
    FlushDecoders --> ResetClocks: 重置音视频时钟
    ResetClocks --> Playing

    Playing --> Paused: pauseDecoding()
    Paused --> Playing: resumeDecoding()

    Playing --> Stopping: nativeReleaseAudio() / Surface 销毁
    Paused --> Stopping: nativeReleaseAudio() / Surface 销毁
    Seeking --> Stopping: 停止请求
    Stopping --> JoinThreads: 唤醒队列并终止 FrameQueue
    JoinThreads --> ReleaseSession: join 后释放 AudioRenderer / EGL / AVIO
    ReleaseSession --> [*]
```

暂停/恢复通过原子状态控制，避免 UI 线程等待 native 阻塞操作。AAudio callback 在暂停时输出静音。

---

## Native 资源所有权

```mermaid
flowchart TB
    DecodeCall["decodeVideo 调用"] --> LocalSession["栈上 PlaybackSession"]
    DecodeCall --> Input["MediaInput"]
    Input --> InputResource["AVFormatContext / AVIOContext / fd"]
    LocalSession --> State["shared_ptr<PlaybackState>"]
    LocalSession --> Queues["PacketQueue / FrameQueue"]
    LocalSession --> AudioPtr["unique_ptr<AudioRenderer>"]
    State --> DemuxerState["Demuxer / Decoder / PacketQueue 显式读取状态"]

    SurfaceCall["setSurface 调用"] --> WindowHolder["NativeWindowHolder"]
    WindowHolder --> SharedWindow["shared_ptr<ANativeWindow>"]
    SharedWindow --> RenderSnapshot["Render 线程复制窗口快照"]
    RenderSnapshot --> NativeEgl["NativeEgl"]
    NativeEgl --> EGLSurface["EGLSurface / EGLContext"]

    Stop["停止请求"] --> Wake["notifyAll / FrameQueue terminate"]
    Wake --> Join["join demux / decode / audio / render"]
    Join --> Cleanup["ScopeExit 释放 session、EGL、AVCodec、AVIO、fd"]
```

---

## 视频窗口适配

当前视频显示优先保证播放器窗口被填满，减少黑边：

```mermaid
flowchart LR
    Texture["TextureView 尺寸"] --> Buffer["SurfaceTexture.setDefaultBufferSize"]
    Buffer --> Surface["Surface"]
    Surface --> NativeWindow["ANativeWindow"]
    NativeWindow --> Geometry["ANativeWindow_setBuffersGeometry"]
    Geometry --> EGL["EGLSurface"]
    EGL --> Renderer["Renderer.init(width, height)"]
    Frame["AVFrame + sample_aspect_ratio"] --> Viewport["AspectFill viewport"]
    Renderer --> Viewport
    Viewport --> Stage["铺满 video_stage，必要时裁切边缘"]
```

---

## 最近稳定性优化

近期已修复几类关键 native 风险：

- 视频显示链路从可见 `SurfaceView` 调整为 `TextureView`，并同步 `SurfaceTexture` buffer 尺寸，减少控件尺寸与 native window 尺寸不一致的问题。
- OpenGL viewport 从完整显示的 `AspectFit` 调整为铺满窗口的 `AspectFill / CenterCrop`，保持比例的同时减少大面积黑边。
- 创建 `EGLSurface` 前先调用 `ANativeWindow_setBuffersGeometry`，避免 EGL 初始化时拿到旧窗口尺寸。
- `LiquidSlider` 拖拽过程改为直接 `snapToValue` 跟手，松手只提交一次 `seekToPosition(ms)`，避免动画积压导致进度条偶发卡住。
- `DampedDragAnimation` 的 `press/release` 使用同一个 `pressJob`，新的释放会取消旧的按压动画，避免松手后仍停留在拖拽态。
- `PacketQueue::push()` 在队列满等待时会周期性检查 `isSeeking`，seek 请求可以打断等待，降低拖拽 seek 后需要再次点击才恢复的概率。
- Render 线程初始化失败时会设置停止标志、唤醒 packet 队列并终止 `FrameQueue`，避免 Video Decode 线程卡死在 `FrameQueue::push()`。
- `g_audioRenderer` 的释放顺序调整为等待 Render 线程结束之后，避免 Render 线程读取已释放对象。
- `FrameQueue::clear()` 清空后会通知等待线程，避免 seek/清队列后生产者继续阻塞。
- Video Decode 不再假设源帧一定是紧密 `YUV420P`；现在统一用 `sws_scale` 转换后再写 YUV 和送渲染。
- OpenGL 上传 U/V 平面时按 `(width + 1) / 2` 和 `(height + 1) / 2` 计算，兼容奇数宽高。
- YUV 调试导出改为按需启用，默认播放路径不再持续写 `output.yuv`，降低长视频播放时的 I/O 和存储压力。
- `SurfaceView` 销毁时会请求 native 会话停止，并在播放线程结束后释放 native window，避免旧 Surface 被继续使用。
- 视频输入不再完整复制到 cache；Java 层通过 `ParcelFileDescriptor` 打开 SAF Uri，并把 `fd:<number>` 交给 native 自定义 AVIO，避免大视频启动前的整文件复制成本。
- Native 播放队列和 `AudioRenderer` 已收敛进 `PlaybackSession`，移除了全局 `g_audioRenderer` 和 `g_sessionActive`，降低跨会话裸指针和释放顺序风险。
- `ANativeWindow` 已改为带释放器的共享句柄，Render 线程使用窗口快照，避免 Surface 生命周期变化时继续读写悬空全局指针。
- Surface 到 `ANativeWindow` 的转换、锁保护、引用快照和释放已抽到 `NativeWindowHolder`，`native-lib.cpp` 不再直接维护 window 全局锁和 deleter。
- 播放控制、Seek、时钟、进度和倍速状态已收敛进 `PlaybackState`，`Demuxer`、`Decoder`、`PacketQueue`、`AudioRenderer` 不再通过 `extern` 读取全局播放状态。
- `decodeVideo()` 的 JNI 字符串、AVIO/fd、codec context 和 YUV 文件清理改为 `ScopeExit` 管理，减少初始化失败或早退分支漏释放资源的风险。
- JNI 字符串获取与释放已封装为 `JniStringChars`，避免 `decodeVideo()` 手动维护 `ReleaseStringUTFChars` 分支。
- 清理未使用的单队列 `Demuxer::demux()` 重载和 `Decoder` 内部冗余 `FrameQueue` 成员，缩小 native 模块维护面。
- EGL display、surface、context 初始化与清理已抽到 `NativeEgl`，进一步减薄 `native-lib.cpp` 的平台渲染细节。

---

## UI 设计与交互（基于 Liquid Glass）

当前首页 UI 已完成 Liquid Glass 风格改造，重点如下：

- **四区域排版**：页面稳定分为顶部文本/状态区、视频区、进度条区、按钮控制区，避免顶部文本撑高后压到视频区域。
- **顶部信息区**：顶部文本框也改为 Liquid Glass 面板，状态文案通过 `LiquidGlassHelper.setStatusText()` 与 native/Java 状态同步。
- **进度条贴近视频**：进度条区域位于视频下方，按钮区紧跟进度条，形成连续播放器控制簇。
- **统一透明玻璃按钮**：选择、解析、播放/暂停、倍速按钮共享同一玻璃 backdrop，取消突兀的蓝色/红色按钮配色。
- **卡片语言**：主要信息区统一为 24dp 圆角卡片，使用 `surfaceContainer*` 分层而不是重阴影。
- **状态语义色**：引入四态色并做浅色/深色资源分离，避免在布局中硬编码颜色。
- **状态联动**：`MainActivity` 会根据播放状态映射并联动更新 chip、卡片底色、解码按钮、播放/暂停/倍速/选择视频按钮，以及 SeekBar 强调色。
- **动效节奏**：页面首屏采用 staggered `fade + slight slide` 入场；状态切换使用 180ms fade；状态文案（如“已选择视频”“正在解析视频”“解析结束”）采用统一 fade + text swap。

### 状态映射

- `PLAYING -> UNDERGO`
- `PAUSED -> NEAR`
- `IDLE -> PASSED`
- `READY -> COMPLETED`

### 状态色资源

- 日间：`app/src/main/res/values/colors.xml`
- 夜间：`app/src/main/res/values-night/colors.xml`

已定义四组 token（每组含 chip、card、button 前景/背景）：

- `state_undergo_*`
- `state_near_*`
- `state_passed_*`
- `state_completed_*`

### Liquid Glass 统一调色

- **容器背景**：`liquid_surface_start/mid/end` 冷色流动渐变。
- **卡片**：`liquid_card_bg`, `liquid_card_bg_strong`, `liquid_card_stroke`, `liquid_card_stroke_subtle`。
- **按钮与交互**：`LiquidButton` 使用 `tint` 和 `surfaceColor` 结合 `Highlight`, `Shadow`, `InnerShadow` 形成液态玻璃质感。
- **呼吸动效**：Active 按钮带有 `rememberInfiniteTransition` 驱动的微弱正弦波呼吸脉冲（`sin(phase)`），振幅仅为 `1.0 -> 1.014`。

---

## 构建验证

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest
```

---

## JNI 接口

- `decodeVideo(String videoPath, String outputPath)`
- `setSurface(Surface surface)`
- `pauseDecoding()`
- `resumeDecoding()`
- `setPlaybackSpeed(float speed)`
- `seekToPosition(int progressMs)`
- `getDurationMs()`
- `getCurrentPositionMs()`
- `nativeReleaseAudio()`

---

## 已知限制与后续方向

- 视频输入当前通过 native 自定义 AVIO 直接读取已授权 fd。少数内容提供方如果返回不可 seek 的 fd，Seek 能力可能受限。
- YUV 导出当前保留为 native 调试能力，默认 UI 播放路径关闭；后续可补一个显式调试开关。
- 当前主要验证方式是构建、单元测试和 arm64 设备手动播放；native 同步链路仍需要更多端到端场景测试。
- `native-lib.cpp` 仍承担 JNI、线程编排、音画同步和资源释放等多重职责；后续可继续拆分为更独立的 session/controller 模块。
- native 同步链路仍需要更多设备级回归测试，尤其是连续 seek、倍速切换、Surface 销毁重建和长视频播放。

---

## 致谢

- **FFmpeg**：行业标准的音视频处理瑞士军刀。
- **Android NDK**：提供 native 层与 Android 系统的桥梁。
- **AndroidLiquidGlass**：提供令人惊叹的液态玻璃 UI 效果。
- **Jetpack Compose**：现代声明式 UI 工具包。
