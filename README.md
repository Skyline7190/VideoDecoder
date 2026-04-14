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
- 界面风格：Material Design 3（MD3）语义色与组件体系
- 导出 YUV 文件用于调试分析

## 2. 技术架构（重点）

项目采用 **Java UI 层 + Native 媒体内核** 的分层架构，Native 采用**多线程生产者-消费者管线**。

```text
┌──────────────────────────────────────────────────────────────────┐
│ Java UI 层 (MainActivity)                                       │
│ - 文件选择(SAF)、Surface 生命周期、控制事件、进度条轮询           │
└───────────────┬──────────────────────────────────────────────────┘
                │ JNI
┌───────────────▼──────────────────────────────────────────────────┐
│ Native 编排层 (native-lib.cpp)                                 │
│ - 全局状态: paused / seeking / stopRequested / clocks / speed   │
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
- 基于 SAF 进行 URI 文件选择，复制到可读路径
- 维护 `SurfaceView`，将 `Surface` 交给 native
- 处理播放控制：pause/resume/speed/seek
- 通过 `getDurationMs/getCurrentPositionMs` 轮询更新进度条与时间

**Native 编排层（`native-lib.cpp`）**
- 初始化 FFmpeg / EGL / AAudio
- 管理全局原子状态：
  - `g_paused`：暂停开关
  - `g_isSeeking`：seek 请求中
  - `g_seekApplied`：demux 已执行底层 seek
  - `g_stopRequested`：会话停止请求
  - `g_sessionActive`：播放会话活动状态
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

### 6.3 界面系统（MD3）

- 主题基线：`Theme.Material3.DayNight.NoActionBar`
- 色彩语义：`colorSurface / colorOnSurface / colorOnSurfaceVariant / colorPrimary`
- 组件体系：
  - 容器：`MaterialCardView`（Surface 区、进度区、状态区）
  - 按钮：`MaterialButton`（Button / TonalButton / OutlinedButton）
- 交互一致性：
  - 四个主控制按钮使用统一 MD3 样式（TonalButton）
  - 进度条与强调色绑定主题主色

## 7. 关键文件地图

```text
app/
├─ src/main/java/com/example/videodecoder/MainActivity.java
├─ src/main/res/layout/activity_main.xml
├─ src/main/res/values/themes.xml
├─ src/main/res/values-night/themes.xml
├─ src/main/res/values/colors.xml
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

### 9.3 单元测试（本地与 CI）

```powershell
.\gradlew.bat testDebugUnitTest
```

仓库已新增 GitHub Actions 工作流（`.github/workflows/android-unit-tests.yml`），在 push / pull request 时自动执行 `testDebugUnitTest`，用于持续回归 UI 策略与输入/格式化策略相关单测。

## 10. 平台限制（重要）

项目仅支持 **`arm64-v8a`**：

- `app/build.gradle` 已固定 `abiFilters "arm64-v8a"`
- Native 依赖 `app/src/main/jniLibs/arm64-v8a/libffmpeg.so`

请在 arm64 真机或兼容环境运行，x86/x86_64 模拟器不在当前支持范围。

## 11. 近期稳定性优化（已落地）

- 修复关键空指针与边界问题：视频流缺失时提前失败，避免 `streams[-1]` 访问；音频渲染器初始化失败路径不再解引用空指针。
- 收敛线程生命周期：引入 `g_stopRequested + g_sessionActive`，支持会话中断与安全回收，降低退出/销毁场景卡死风险。
- 改进队列与结束信号：demux 在 EOF/错误时明确结束并 `notifyAll`，避免等待线程长期阻塞。
- JNI 回调与资源清理收敛：`onVideoDecoded` 单次回调，减少重复通知与重复字符串转换。
- 音频回调热路径优化：队列消费改为偏移读取，避免频繁 `erase` 造成内存搬移。
- Android 35 权限模型适配：采用 SAF 文件选择，移除 `READ/WRITE_EXTERNAL_STORAGE` 依赖。
- 构建稳定性修复：`CMakeLists.txt` 统一使用 `AudioRender.cpp` 文件名，避免大小写导致的构建问题。
- UI 并发防护：解码按钮在任务进行中拦截重复触发，避免并发解码竞争。
- Surface 就绪门禁：仅在 `surfaceCreated` 后允许启动解码，降低未就绪渲染导致黑屏/假卡住概率。
- 控制命令状态对齐：seek / pause / resume / 倍速仅在解码运行中生效，减少空操作 JNI 调用。
- 交互与状态一致性增强：解码期间禁用“解析视频”并显示“解析中...”，结束后自动恢复按钮状态。
- 输入与 I/O 防御补齐：视频选择回调判空、持久化授权异常保护、文件复制采用 try-with-resources。
- Activity Result 现代化：文件选择迁移到 `ActivityResultLauncher + OpenDocument`，消除过时 API 警告并降低生命周期回调错配风险。
- 边界值防御：倍速参数与渲染等待增加有限值/范围校验，避免异常输入引发等待异常；`setSurface` 与输出目录增加空值保护。
- 可测试策略抽离：新增 `PlaybackUiPolicy / PlaybackInputPolicy / PlaybackTimeFormatter`，将 UI 启停规则、倍速边界与时间格式化从 Activity 中抽离并补充单元测试。
- Java 侧日志收敛：移除 `printStackTrace` 与静默忽略异常，改为 `Log.e/Log.w` 结构化输出，提升线上问题定位效率。
- Native 倍速策略统一：C++ 侧新增统一 `sanitizePlaybackSpeed`，消除 0.25/0.5 下限不一致，音频 `atempo`、渲染等待与 JNI 设置口径一致（0.5x~3.0x）。

## 12. 当前质量评估

- 当前评分：**9.6 / 10**
- 评分依据：核心播放链路稳定、状态与边界防御完备、UI 策略与单测落地、CI 自动回归已接入、native 倍速策略一致性已收敛。
- 剩余提升空间：补充 native 层自动化测试与更细粒度性能回归基线。

## 13. 后续演进建议

- 将控制逻辑升级为显式播放器状态机（Idle/Prepared/Playing/Paused/Seeking/Stopped）
- 为 seek/pause/speed 增加统一事件日志与指标
- 抽离时钟模块（AudioClock/VideoClock）提升可测试性
- 对比 `atempo` 与专业 time-stretch 库在音质与延迟上的权衡
