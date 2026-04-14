# VideoDecoder

一个基于 **Android + FFmpeg + OpenGL ES + AAudio** 的原生视频解码与播放项目。  
项目采用 Java UI 层 + C++ JNI 媒体内核的分层架构，支持本地视频选择、音视频解码、YUV 渲染、播放控制、倍速与 Seek。

## 项目特性

- FFmpeg 解复用与软解码（音频/视频双流）
- OpenGL ES 渲染 YUV 视频帧到 `SurfaceView`
- AAudio 低延迟音频播放
- 播放控制：播放/暂停、0.5x/1x/2x/3x 倍速
- 进度控制：SeekBar 拖动跳转（Seek）
- 输出解码后的 `output.yuv` 到应用私有目录
- 多线程生产者-消费者管线（Demux -> Decode -> Render）

## 技术架构

### Java 层

- 入口：`app/src/main/java/com/example/videodecoder/MainActivity.java`
- 负责：
  - 权限申请与视频文件选择
  - `Surface` 传递给 Native
  - UI 交互（播放/暂停、倍速、Seek）
  - 触发 JNI 解码流程

### Native C++ 层

- 入口：`app/src/main/cpp/native-lib.cpp`
- 关键模块：
  - `Demuxer.cpp`：音视频分离并投递包队列
  - `Decoder.cpp`：视频解码并投递帧队列
  - `queue.h / queue.cpp`：线程安全阻塞队列
  - `videoRender.cpp`：OpenGL ES YUV 渲染
  - `audioRender.cpp`：AAudio 回调播放与音频缓冲管理

### 音画同步策略

当前实现以音频时钟为基准，视频根据与音频的时间差动态等待（支持倍速影响等待时长），并结合 AAudio 积压缓冲时长估算“正在发声”的真实音频时间戳。

相关设计文档：

- `问题.md`
- `音画同步解决方案.md`
- `最终解决方案.md`

## 目录结构（核心）

```text
VideoDecoder
├─ app
│  ├─ src/main/java/com/example/videodecoder/MainActivity.java
│  ├─ src/main/cpp/
│  │  ├─ native-lib.cpp
│  │  ├─ Demuxer.cpp
│  │  ├─ Decoder.cpp
│  │  ├─ queue.h / queue.cpp
│  │  ├─ videoRender.h / videoRender.cpp
│  │  └─ AudioRender.h / audioRender.cpp
│  ├─ src/main/jniLibs/arm64-v8a/libffmpeg.so
│  └─ build.gradle
├─ gradle/libs.versions.toml
└─ AGENTS.md
```

## 环境要求

- Android Studio（建议较新稳定版）
- Android SDK（`compileSdk = 35`）
- NDK + CMake（项目已配置 `externalNativeBuild`）
- Java 11

## 重要限制（务必先看）

项目当前 **只支持 `arm64-v8a`**：

- `app/build.gradle` 中已配置 `abiFilters "arm64-v8a"`
- 预编译 FFmpeg 库路径：`app/src/main/jniLibs/arm64-v8a/libffmpeg.so`

> 请不要使用 x86/x86_64 模拟器测试，否则会因找不到匹配的 native 库导致运行失败。

## 构建与运行

在项目根目录执行（Windows）：

```powershell
.\gradlew.bat clean
.\gradlew.bat assembleDebug
```

安装到设备：

```powershell
.\gradlew.bat installDebug
```

## 使用说明

1. 启动 App，点击“选择视频”导入本地视频。
2. 点击“解析视频”开始 Native 解码播放流程。
3. 使用“播放/暂停”、倍速按钮与进度条进行控制。
4. 解码输出 `output.yuv` 位于应用外部私有目录（`getExternalFilesDir`）。

## JNI 接口（MainActivity）

- `decodeVideo(String videoPath, String outputPath)`
- `setSurface(Surface surface)`
- `pauseDecoding() / resumeDecoding()`
- `setPlaybackSpeed(float speed)`
- `seekToPosition(int progressMs)`
- `nativeReleaseAudio()`

## 备注

- 项目中包含 `androidTest` / `test` 示例测试文件，但当前主要验证方式是 **真机（arm64）构建与运行**。
- 若要继续优化同步逻辑，建议先完整阅读上述三份同步文档，再改动播放时钟、线程节奏或 sleep 策略。
