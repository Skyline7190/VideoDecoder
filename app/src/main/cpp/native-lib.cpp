#include <jni.h>
#include <string>
#include <iostream>
#include <android/log.h>
#include <thread>
#include <vector>
#include <cmath>
#include <inttypes.h>
#include <cstring>
#include <cerrno>
#include <cstdlib>
#include <memory>
#include <mutex>
#include <unistd.h>
#include "Demuxer.h"
#include "Decoder.h"
#include "queue.h"
#include "videoRender.h"
#include "AudioRender.h"
#include "libswresample/swresample.h"
#include <atomic>
//渲染相关头文件
#include <EGL/egl.h>
#include <GLES3/gl3.h>
#include <EGL/eglext.h>
#include <android/native_window.h>
#include <android/native_window_jni.h> //用于ANativeWindow_fromSurface
std::atomic<bool> g_isSeeking{false};
std::atomic<bool> g_seekApplied{false};
std::atomic<int64_t> g_seekPositionMs{0};
std::atomic<bool> g_paused{false};
std::atomic<bool> g_stopRequested{false};
extern "C" {
//日志宏定义
#define LOG_TAG "NativeLib"

#include <libavformat/avformat.h>
#include <libavutil/avutil.h>
#include <libavcodec/avcodec.h>
#include <libavutil/time.h>
#include <libswresample/swresample.h>
#include <libavutil/opt.h>
#include <libavfilter/avfilter.h>
#include <libavfilter/buffersrc.h>
#include <libavfilter/buffersink.h>
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

/* ========================== 全局变量声明 ========================== */
struct NativeWindowDeleter {
    void operator()(ANativeWindow* window) const {
        if (window) {
            ANativeWindow_release(window);
        }
    }
};

using NativeWindowPtr = std::shared_ptr<ANativeWindow>;

// 全局变量，用于保存 Java 层传入的 Surface 对应的 NativeWindow
NativeWindowPtr g_nativeWindow;
std::mutex g_nativeWindowMutex;
std::atomic<bool> g_decodingFinished{false};// 解码完成标志

// 全局暂停控制变量
//音频相关变量
std::atomic<bool> g_audioDecodingFinished{false};
// 全局变量添加
std::atomic<int64_t> g_audioClock{0};
std::atomic<int64_t> g_videoClock{0};
// 在全局变量区域添加
std::atomic<int64_t> g_durationMs{0};
std::atomic<int64_t> g_currentPositionMs{0};
// 播放速度控制变量
std::atomic<float> g_playbackSpeed{1.0f};
constexpr float kPlaybackSpeedDefault = 1.0f;
constexpr float kPlaybackSpeedMin = 0.5f;
constexpr float kPlaybackSpeedMax = 3.0f;

struct PlaybackSession {
    PacketQueue videoPacketQueue;
    PacketQueue audioPacketQueue;
    FrameQueue frameQueue;
    std::unique_ptr<AudioRenderer> audioRenderer;
};

inline float sanitizePlaybackSpeed(float speed) {
    if (!std::isfinite(speed)) return kPlaybackSpeedDefault;
    if (speed < kPlaybackSpeedMin) return kPlaybackSpeedMin;
    if (speed > kPlaybackSpeedMax) return kPlaybackSpeedMax;
    return speed;
}

struct FdAvioSource {
    int fd = -1;
};

int readFdPacket(void* opaque, uint8_t* buffer, int bufferSize) {
    auto* source = static_cast<FdAvioSource*>(opaque);
    if (!source || source->fd < 0) {
        return AVERROR(EINVAL);
    }
    ssize_t bytesRead = read(source->fd, buffer, static_cast<size_t>(bufferSize));
    if (bytesRead == 0) {
        return AVERROR_EOF;
    }
    if (bytesRead < 0) {
        return AVERROR(errno);
    }
    return static_cast<int>(bytesRead);
}

int64_t seekFdPacket(void* opaque, int64_t offset, int whence) {
    auto* source = static_cast<FdAvioSource*>(opaque);
    if (!source || source->fd < 0) {
        return AVERROR(EINVAL);
    }
    if (whence == AVSEEK_SIZE) {
        off_t current = lseek(source->fd, 0, SEEK_CUR);
        if (current < 0) {
            return AVERROR(errno);
        }
        off_t size = lseek(source->fd, 0, SEEK_END);
        if (size < 0) {
            return AVERROR(errno);
        }
        if (lseek(source->fd, current, SEEK_SET) < 0) {
            return AVERROR(errno);
        }
        return static_cast<int64_t>(size);
    }

    int origin = SEEK_SET;
    if (whence == SEEK_CUR) {
        origin = SEEK_CUR;
    } else if (whence == SEEK_END) {
        origin = SEEK_END;
    } else if (whence != SEEK_SET) {
        return AVERROR(EINVAL);
    }

    off_t position = lseek(source->fd, static_cast<off_t>(offset), origin);
    if (position < 0) {
        return AVERROR(errno);
    }
    return static_cast<int64_t>(position);
}

/* ========================== EGL 相关函数 ========================== */
// 初始化 EGLDisplay
EGLDisplay initEGLDisplay() {
    EGLDisplay display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (display == EGL_NO_DISPLAY) {
        LOGE("eglGetDisplay failed");
        return EGL_NO_DISPLAY;
    }

    EGLint major, minor;
    if (eglInitialize(display, &major, &minor) != EGL_TRUE) {
        LOGE("eglInitialize failed");
        return EGL_NO_DISPLAY;
    }

    return display;
}
//创建 EGL 配置属性
EGLSurface createEGLSurface(EGLDisplay display, ANativeWindow* window) {
    EGLint configAttribs[] = {
            EGL_SURFACE_TYPE, EGL_WINDOW_BIT,
            EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT, // 尝试使用 OpenGL ES 2.x
            EGL_BLUE_SIZE, 8,
            EGL_GREEN_SIZE, 8,
            EGL_RED_SIZE, 8,
            EGL_ALPHA_SIZE, 8,
            EGL_DEPTH_SIZE, 16,
            EGL_NONE
    };

    EGLConfig config;
    EGLint numConfigs;
    if (eglChooseConfig(display, configAttribs, &config, 1, &numConfigs) != EGL_TRUE || numConfigs == 0) {
        LOGE("eglChooseConfig failed");
        return EGL_NO_SURFACE;
    }

    EGLSurface surface = eglCreateWindowSurface(display, config, window, nullptr);
    if (surface == EGL_NO_SURFACE) {
        LOGE("eglCreateWindowSurface failed");
        return EGL_NO_SURFACE;
    }

    return surface;
}
//创建 EGL 上下文
EGLContext createEGLContext(EGLDisplay display, EGLSurface surface) {
    EGLint configAttribs[] = {
            EGL_CONTEXT_CLIENT_VERSION, 2, // 使用 OpenGL ES 2.x
            EGL_NONE
    };

    EGLint numConfigs;
    EGLConfig config;
    EGLint configSpec[] = {
            EGL_SURFACE_TYPE, EGL_WINDOW_BIT,
            EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
            EGL_RED_SIZE, 8,
            EGL_GREEN_SIZE, 8,
            EGL_BLUE_SIZE, 8,
            EGL_ALPHA_SIZE, 8,
            EGL_DEPTH_SIZE, 24,
            EGL_STENCIL_SIZE, 8,
            EGL_NONE
    };

    // 获取符合要求的配置数量
    if (eglChooseConfig(display, configSpec, nullptr, 0, &numConfigs) == EGL_FALSE) {
        LOGE("eglChooseConfig failed to get number of configs");
        return EGL_NO_CONTEXT;
    }

    // 获取符合要求的配置列表
    EGLConfig* configs = new EGLConfig[numConfigs];
    if (eglChooseConfig(display, configSpec, configs, numConfigs, &numConfigs) == EGL_FALSE) {
        LOGE("eglChooseConfig failed to get configs");
        delete[] configs;
        return EGL_NO_CONTEXT;
    }

    // 选择第一个配置
    config = configs[0];
    delete[] configs;
    // 定义上下文属性
    EGLint contextAttribs[] = {
            EGL_CONTEXT_CLIENT_VERSION, 2, // 使用 OpenGL ES 2.x
            EGL_NONE
    };
    // 创建 EGL 上下文
    EGLContext context = eglCreateContext(display, config, EGL_NO_CONTEXT, contextAttribs);
    if (context == EGL_NO_CONTEXT) {
        LOGE("eglCreateContext failed");
        return EGL_NO_CONTEXT;
    }

    // 绑定上下文
    if (eglMakeCurrent(display, surface, surface, context) != EGL_TRUE) {
        LOGE("eglMakeCurrent failed");
        return EGL_NO_CONTEXT;
    }

    return context;
}
//清理EGL资源
void cleanupEGL(EGLDisplay display, EGLSurface surface, EGLContext context) {
    eglMakeCurrent(display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    eglDestroyContext(display, context);
    eglDestroySurface(display, surface);
    eglTerminate(display);
}
// 设置Surface接口
JNIEXPORT void JNICALL
Java_com_example_videodecoder_MainActivity_setSurface(JNIEnv* env, jobject thiz, jobject surface) {
    std::lock_guard<std::mutex> lock(g_nativeWindowMutex);
    g_nativeWindow.reset();
    if (surface == nullptr) {
        return;
    }
    ANativeWindow* window = ANativeWindow_fromSurface(env, surface);
    if (!window) {
        LOGE("Failed to create NativeWindow from Surface");
        return;
    }
    g_nativeWindow = NativeWindowPtr(window, NativeWindowDeleter());
}
//视频解码主函数
JNIEXPORT void JNICALL
Java_com_example_videodecoder_MainActivity_decodeVideo(JNIEnv *env, jobject thiz,
                                                       jstring video_path, jstring output_path) {
    /* ------------------------- 初始化阶段 ------------------------- */
    //获取输入输出路径
    const char *path = env->GetStringUTFChars(video_path, nullptr);
    const char *outPath = output_path ? env->GetStringUTFChars(output_path, nullptr) : nullptr;
    auto releaseJniStrings = [&]() {
        env->ReleaseStringUTFChars(video_path, path);
        if (outPath) {
            env->ReleaseStringUTFChars(output_path, outPath);
        }
    };
    g_isSeeking.store(false);
    g_seekApplied.store(false);
    g_paused.store(false);
    g_stopRequested.store(false);
    g_decodingFinished.store(false);
    g_audioDecodingFinished.store(false);
    g_currentPositionMs.store(0);
    g_durationMs.store(0);

    LOGD("开始解析视频: %s", path);

    // 1. 打开输入文件
    AVFormatContext *fmt_ctx = nullptr;
    AVIOContext* avio_ctx = nullptr;
    uint8_t* avioBuffer = nullptr;
    FdAvioSource* fdSource = nullptr;
    int duplicatedFd = -1;

    auto closeInput = [&]() {
        if (fmt_ctx) {
            avformat_close_input(&fmt_ctx);
        }
        if (avio_ctx) {
            av_freep(&avio_ctx->buffer);
            avio_context_free(&avio_ctx);
        } else if (avioBuffer) {
            av_freep(&avioBuffer);
        }
        if (duplicatedFd >= 0) {
            close(duplicatedFd);
            duplicatedFd = -1;
        }
        delete fdSource;
        fdSource = nullptr;
    };

    int openInputRet = 0;
    if (strncmp(path, "fd:", 3) == 0) {
        char* end = nullptr;
        long javaFd = strtol(path + 3, &end, 10);
        if (!end || *end != '\0' || javaFd < 0) {
            openInputRet = AVERROR(EINVAL);
        } else {
            duplicatedFd = dup(static_cast<int>(javaFd));
            if (duplicatedFd < 0) {
                openInputRet = AVERROR(errno);
            } else {
                if (lseek(duplicatedFd, 0, SEEK_SET) < 0) {
                    LOGD("Input fd is not seekable at start: %d", errno);
                }
                fdSource = new FdAvioSource();
                fdSource->fd = duplicatedFd;
                constexpr int avioBufferSize = 64 * 1024;
                avioBuffer = static_cast<uint8_t*>(av_malloc(avioBufferSize));
                if (!avioBuffer) {
                    openInputRet = AVERROR(ENOMEM);
                } else {
                    avio_ctx = avio_alloc_context(avioBuffer,
                                                  avioBufferSize,
                                                  0,
                                                  fdSource,
                                                  readFdPacket,
                                                  nullptr,
                                                  seekFdPacket);
                    if (!avio_ctx) {
                        openInputRet = AVERROR(ENOMEM);
                    } else {
                        avioBuffer = nullptr;
                        fmt_ctx = avformat_alloc_context();
                        if (!fmt_ctx) {
                            openInputRet = AVERROR(ENOMEM);
                        } else {
                            fmt_ctx->pb = avio_ctx;
                            fmt_ctx->flags |= AVFMT_FLAG_CUSTOM_IO;
                            openInputRet = avformat_open_input(&fmt_ctx, nullptr, nullptr, nullptr);
                        }
                    }
                }
            }
        }
    } else {
        openInputRet = avformat_open_input(&fmt_ctx, path, nullptr, nullptr);
    }

    if (openInputRet != 0) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "Could not open input file: %s", path);
        closeInput();
        releaseJniStrings();
        return;
    }

    // 2. 找到视频流和音频流
    int video_stream_index = av_find_best_stream(fmt_ctx, AVMEDIA_TYPE_VIDEO, -1, -1, nullptr, 0);
    int audio_stream_index = av_find_best_stream(fmt_ctx, AVMEDIA_TYPE_AUDIO, -1, -1, nullptr, 0);
    if (video_stream_index < 0 && audio_stream_index < 0) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "No video or audio stream found");
        closeInput();
        releaseJniStrings();
        return;
    }
    if (video_stream_index < 0) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "No video stream found");
        closeInput();
        releaseJniStrings();
        return;
    }
    if (fmt_ctx->duration != AV_NOPTS_VALUE && fmt_ctx->duration > 0) {
        g_durationMs.store(static_cast<int64_t>(fmt_ctx->duration / 1000));
    }

    // 3. 初始化解码器
    AVCodec *codec = avcodec_find_decoder(fmt_ctx->streams[video_stream_index]->codecpar->codec_id);
    if (!codec) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "Decoder not found");
        closeInput();
        releaseJniStrings();
        return;
    }
    AVCodecContext *codec_ctx = avcodec_alloc_context3(codec);
    avcodec_parameters_to_context(codec_ctx, fmt_ctx->streams[video_stream_index]->codecpar);
    if (avcodec_open2(codec_ctx, codec, nullptr) < 0) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "Could not open codec");
        avcodec_free_context(&codec_ctx);
        closeInput();
        releaseJniStrings();
        return;
    }
    PlaybackSession session;
    //初始化音频解码器
    AVCodecContext *audio_codec_ctx = nullptr;
    if (audio_stream_index >= 0) {
        AVCodec *audio_codec = avcodec_find_decoder(fmt_ctx->streams[audio_stream_index]->codecpar->codec_id);
        if (!audio_codec) {
            __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "Audio decoder not found");
        } else {
            audio_codec_ctx = avcodec_alloc_context3(audio_codec);
            avcodec_parameters_to_context(audio_codec_ctx, fmt_ctx->streams[audio_stream_index]->codecpar);
            if (avcodec_open2(audio_codec_ctx, audio_codec, nullptr) < 0) {
                __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "Could not open audio codec");
                avcodec_free_context(&audio_codec_ctx);
            } else {
                // 初始化音频渲染器
                session.audioRenderer.reset(new AudioRenderer());
                if (!session.audioRenderer->init(audio_codec_ctx->sample_rate,
                                                 audio_codec_ctx->channels,
                                                 AAUDIO_FORMAT_PCM_I16)) {
                    __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "Failed to initialize audio renderer");
                    session.audioRenderer.reset();
                } else {
                    int64_t cacheTimeMs = 300;
                    session.audioRenderer->setSyncThreshold(100);
                    session.audioRenderer->setAudioStartTime(av_gettime_relative() / 1000 + cacheTimeMs);
                    session.audioRenderer->start();
                }
            }
        }
    }

    // 4. 按需打开调试 YUV 输出文件
    FILE *yuv_file = nullptr;
    bool yuvExportEnabled = outPath && outPath[0] != '\0';
    if (yuvExportEnabled) {
        yuv_file = fopen(outPath, "wb");
        if (!yuv_file) {
            __android_log_print(ANDROID_LOG_ERROR, LOG_TAG,
                                "Failed to open YUV output file at %s, continue without export",
                                outPath);
            yuvExportEnabled = false;
        }
    }
    /* ------------------------- 线程创建阶段 ------------------------- */
    // 1. 创建队列
    PacketQueue& video_packet_queue = session.videoPacketQueue;//视频数据包队列
    PacketQueue& audio_packet_queue = session.audioPacketQueue;//音频数据包队列
    FrameQueue& frameQueue = session.frameQueue;//帧队列

    Demuxer demuxer;//解复用器
    Decoder decoder;//解码器

    //2.解复用线程
    std::thread demuxThread([&]() {
        demuxer.demux(fmt_ctx,
                      video_stream_index, video_packet_queue,
                      audio_stream_index, audio_packet_queue);
    });

    //3.视频解码线程
    std::thread decodeThread([&]() {
        decoder.decode(codec_ctx, yuv_file, video_packet_queue,frameQueue);
        // 通知 Java 解码完成
        g_decodingFinished = true; // 设置解码完成标志
        frameQueue.terminate();

    });
    //音频解码线程
    std::thread audioDecodeThread([&]() {
        if (!audio_codec_ctx || !session.audioRenderer) return;

        AVFrame *frame = av_frame_alloc();
        AVFrame *tempoFrame = av_frame_alloc();
        AVFilterGraph* tempoGraph = nullptr;
        AVFilterContext* tempoSrc = nullptr;
        AVFilterContext* tempoSink = nullptr;
        float currentTempo = 1.0f;

        auto releaseTempoGraph = [&]() {
            if (tempoGraph) {
                avfilter_graph_free(&tempoGraph);
                tempoGraph = nullptr;
            }
            tempoSrc = nullptr;
            tempoSink = nullptr;
            currentTempo = 1.0f;
        };

        auto buildAtempoChain = [](float speed) -> std::string {
            speed = sanitizePlaybackSpeed(speed);
            if (fabsf(speed - 1.0f) < 0.0001f) return "";

            std::string chain;
            float remain = speed;
            while (remain > 2.0f + 0.0001f) {
                if (!chain.empty()) chain += ",";
                chain += "atempo=2.0";
                remain /= 2.0f;
            }
            while (remain < 0.5f - 0.0001f) {
                if (!chain.empty()) chain += ",";
                chain += "atempo=0.5";
                remain /= 0.5f;
            }
            if (!chain.empty()) chain += ",";
            chain += "atempo=" + std::to_string(remain);
            return chain;
        };

        auto ensureTempoGraph = [&](float speed) -> bool {
            speed = sanitizePlaybackSpeed(speed);

            if (fabsf(speed - 1.0f) < 0.0001f) {
                releaseTempoGraph();
                return true;
            }
            if (tempoGraph && fabsf(speed - currentTempo) < 0.0001f) {
                return true;
            }

            releaseTempoGraph();
            tempoGraph = avfilter_graph_alloc();
            if (!tempoGraph) return false;

            const AVFilter* abuffer = avfilter_get_by_name("abuffer");
            const AVFilter* atempo = avfilter_get_by_name("atempo");
            const AVFilter* abuffersink = avfilter_get_by_name("abuffersink");
            if (!abuffer || !atempo || !abuffersink) {
                releaseTempoGraph();
                return false;
            }

            int64_t channelLayout = av_get_default_channel_layout(audio_codec_ctx->channels);
            char args[256];
            snprintf(args, sizeof(args),
                     "time_base=1/%d:sample_rate=%d:sample_fmt=%s:channel_layout=0x%" PRIx64,
                     audio_codec_ctx->sample_rate,
                     audio_codec_ctx->sample_rate,
                     av_get_sample_fmt_name(AV_SAMPLE_FMT_S16),
                     static_cast<uint64_t>(channelLayout));

            if (avfilter_graph_create_filter(&tempoSrc, abuffer, "tempo_src", args, nullptr, tempoGraph) < 0) {
                releaseTempoGraph();
                return false;
            }
            if (avfilter_graph_create_filter(&tempoSink, abuffersink, "tempo_sink", nullptr, nullptr, tempoGraph) < 0) {
                releaseTempoGraph();
                return false;
            }

            AVFilterContext* last = tempoSrc;
            std::string chain = buildAtempoChain(speed);
            size_t pos = 0;
            int index = 0;
            while (pos < chain.size()) {
                size_t comma = chain.find(',', pos);
                std::string token = chain.substr(pos, comma == std::string::npos ? std::string::npos : comma - pos);
                size_t eq = token.find('=');
                const std::string opt = (eq == std::string::npos) ? "" : token.substr(eq + 1);
                AVFilterContext* tempoNode = nullptr;
                std::string name = "atempo_" + std::to_string(index++);
                if (avfilter_graph_create_filter(&tempoNode, atempo, name.c_str(), opt.c_str(), nullptr, tempoGraph) < 0) {
                    releaseTempoGraph();
                    return false;
                }
                if (avfilter_link(last, 0, tempoNode, 0) < 0) {
                    releaseTempoGraph();
                    return false;
                }
                last = tempoNode;
                if (comma == std::string::npos) break;
                pos = comma + 1;
            }

            if (avfilter_link(last, 0, tempoSink, 0) < 0) {
                releaseTempoGraph();
                return false;
            }
            if (avfilter_graph_config(tempoGraph, nullptr) < 0) {
                releaseTempoGraph();
                return false;
            }
            currentTempo = speed;
            return true;
        };

        SwrContext* swr_ctx = swr_alloc_set_opts(nullptr,
                                                 av_get_default_channel_layout(audio_codec_ctx->channels),
                                                 AV_SAMPLE_FMT_S16,
                                                 audio_codec_ctx->sample_rate,
                                                 av_get_default_channel_layout(audio_codec_ctx->channels),
                                                 audio_codec_ctx->sample_fmt,
                                                 audio_codec_ctx->sample_rate,
                                                 0, nullptr);
        swr_init(swr_ctx);

        int seekWaitLoops = 0;
        while (true) {
            if (g_stopRequested.load()) {
                break;
            }
            if (g_isSeeking.load()) {
                if (!g_seekApplied.load()) {
                    if (seekWaitLoops < 1000) {
                        seekWaitLoops++;
                        std::this_thread::sleep_for(std::chrono::milliseconds(1));
                        continue;
                    }
                    LOGD("Seek wait timed out, forcing queue/decoder cleanup");
                } else {
                    seekWaitLoops = 0;
                }
                if (!g_seekApplied.load()) {
                    std::this_thread::sleep_for(std::chrono::milliseconds(1));
                }
                // 1. 清空所有的旧数据队列
                video_packet_queue.clear();
                audio_packet_queue.clear();
                video_packet_queue.notifyAll();
                audio_packet_queue.notifyAll();
                frameQueue.clear();
                
                // 2. 清空 AAudio 积压
                if (session.audioRenderer) {
                    session.audioRenderer->cleanup(); // 销毁底层缓冲
                    session.audioRenderer->init(audio_codec_ctx->sample_rate, audio_codec_ctx->channels, AAUDIO_FORMAT_PCM_I16);
                    session.audioRenderer->start();
                }
                releaseTempoGraph();

                // 3. 清空解码器内部的历史缓存帧
                // 视频解码器由视频解码线程自行 flush，避免跨线程并发访问 codec_ctx
                if (audio_codec_ctx) {
                    avcodec_flush_buffers(audio_codec_ctx);
                }
                
                // 4. 重置时钟基准
                int64_t target_pts_us = g_seekPositionMs.load() * 1000;
                g_videoClock.store(target_pts_us);
                g_audioClock.store(target_pts_us);
                if (session.audioRenderer) {
                    session.audioRenderer->setFirstAudioPts(target_pts_us);
                }
                
                // 结束 Seek 状态，恢复正常播放
                g_seekApplied.store(false);
                g_isSeeking.store(false);
                seekWaitLoops = 0;
                continue;
            }

            if (g_paused) {
                if (g_stopRequested.load()) {
                    break;
                }
                std::this_thread::sleep_for(std::chrono::milliseconds(10));
                continue;
            }

            // 【重磅修复】：防止音频解码过快导致时钟跑飞
            // 控制音频队列的积压时长最高不超过 500ms
            if (session.audioRenderer && session.audioRenderer->getPendingAudioDurationUs() > 500000) {
                std::this_thread::sleep_for(std::chrono::milliseconds(10));
                continue;
            }

            AVPacket *pkt = audio_packet_queue.pop();
            if (pkt == nullptr) {
                if (audio_packet_queue.isDemuxFinished() || g_stopRequested.load()) break;
                continue;
            }

            if (avcodec_send_packet(audio_codec_ctx, pkt) == 0) {
                while (avcodec_receive_frame(audio_codec_ctx, frame) == 0) {
                    if (g_stopRequested.load()) {
                        av_frame_unref(frame);
                        break;
                    }
                    
                    // 重采样
                    uint8_t* output;
                    int out_samples = swr_get_out_samples(swr_ctx, frame->nb_samples);
                    av_samples_alloc(&output, nullptr, audio_codec_ctx->channels,
                                     out_samples, AV_SAMPLE_FMT_S16, 0);
                    out_samples = swr_convert(swr_ctx, &output, out_samples,
                                              (const uint8_t**)frame->data, frame->nb_samples);

                    if (!g_paused && out_samples > 0) {
                        float speed = sanitizePlaybackSpeed(g_playbackSpeed.load());
                        if (!ensureTempoGraph(speed)) {
                            session.audioRenderer->writeData(output, out_samples);
                        } else if (!tempoGraph) {
                            session.audioRenderer->writeData(output, out_samples);
                        } else {
                            av_frame_unref(tempoFrame);
                            tempoFrame->format = AV_SAMPLE_FMT_S16;
                            tempoFrame->channel_layout = av_get_default_channel_layout(audio_codec_ctx->channels);
                            tempoFrame->sample_rate = audio_codec_ctx->sample_rate;
                            tempoFrame->nb_samples = out_samples;

                            if (av_frame_get_buffer(tempoFrame, 0) == 0) {
                                int dataBytes = out_samples * audio_codec_ctx->channels * sizeof(int16_t);
                                memcpy(tempoFrame->data[0], output, dataBytes);
                                if (av_buffersrc_add_frame_flags(tempoSrc, tempoFrame, AV_BUFFERSRC_FLAG_KEEP_REF) >= 0) {
                                    AVFrame* filtered = av_frame_alloc();
                                    while (av_buffersink_get_frame(tempoSink, filtered) >= 0) {
                                        if (filtered->nb_samples > 0) {
                                            session.audioRenderer->writeData(filtered->data[0], filtered->nb_samples);
                                        }
                                        av_frame_unref(filtered);
                                    }
                                    av_frame_free(&filtered);
                                } else {
                                    session.audioRenderer->writeData(output, out_samples);
                                }
                            } else {
                                session.audioRenderer->writeData(output, out_samples);
                            }
                        }
                    }
                    
                    // 必须在写进队列之后更新时钟，代表最后放进队列的帧PTS
                    if (frame->pts != AV_NOPTS_VALUE) {
                        g_audioClock.store(av_rescale_q(frame->pts,
                                                        fmt_ctx->streams[audio_stream_index]->time_base,
                                                        AV_TIME_BASE_Q));
                    }

                    av_freep(&output);
                }
            }
            av_packet_free(&pkt);
        }

        av_frame_free(&frame);
        av_frame_free(&tempoFrame);
        releaseTempoGraph();
        swr_free(&swr_ctx);
        g_audioDecodingFinished = true;
    });

    //4.视频渲染线程
    std::thread renderThread([&]() {
        auto abortRender = [&](const char* message) {
            LOGE("%s", message);
            g_stopRequested.store(true);
            video_packet_queue.notifyAll();
            audio_packet_queue.notifyAll();
            frameQueue.terminate();
        };

        // 1. 检查NativeWindow有效性
        NativeWindowPtr renderWindow;
        {
            std::lock_guard<std::mutex> lock(g_nativeWindowMutex);
            renderWindow = g_nativeWindow;
        }

        if (!renderWindow) {
            abortRender("RenderThread: NativeWindow is not valid");
            return;
        }

        // 2. 初始化EGL环境
        EGLDisplay display = initEGLDisplay();
        if (display == EGL_NO_DISPLAY) {
            abortRender("RenderThread: EGL display initialization failed");
            return;
        }

        EGLSurface surface = createEGLSurface(display, renderWindow.get());
        if (surface == EGL_NO_SURFACE) {
            eglTerminate(display);
            abortRender("RenderThread: EGL surface creation failed");
            return;
        }

        EGLContext context = createEGLContext(display, surface);
        if (context == EGL_NO_CONTEXT) {
            eglDestroySurface(display, surface);
            eglTerminate(display);
            abortRender("RenderThread: EGL context creation failed");
            return;
        }

        // 3. 设置窗口缓冲几何
        int width = ANativeWindow_getWidth(renderWindow.get());
        int height = ANativeWindow_getHeight(renderWindow.get());
        ANativeWindow_setBuffersGeometry(renderWindow.get(), width, height, WINDOW_FORMAT_RGBA_8888);

        // 4. 初始化渲染器
        Renderer renderer;
        if (!renderer.init(width, height)) {
            cleanupEGL(display, surface, context);
            abortRender("RenderThread: Renderer initialization failed");
            return;
        }

        // 主渲染循环
        auto lastFrameTime = std::chrono::high_resolution_clock::now();
        AVRational frameRate = av_guess_frame_rate(fmt_ctx,
                                                   fmt_ctx->streams[video_stream_index], nullptr);

        // 计算帧间隔（微秒），比如 30fps 就是 33333 微秒
        int64_t frameDuration = (frameRate.num && frameRate.den) ?
                                (1000000 * frameRate.den / frameRate.num) :
                                (1000000 / 30); // 默认30fps

        while (true) {
            if (g_stopRequested.load()) {
                break;
            }
            // 检查暂停状态
            if (g_paused) {
                if (g_stopRequested.load()) {
                    break;
                }
                std::this_thread::sleep_for(std::chrono::milliseconds(100));
                continue;
            }
            AVFrame* frame = frameQueue.pop(); // 阻塞式获取
            if (!frame) {
                if (g_decodingFinished || g_stopRequested.load()) break; // 终止信号
                continue;
            }
            // 更新视频时钟
            if (frame->pts != AV_NOPTS_VALUE) {
                g_videoClock.store(av_rescale_q(frame->pts,
                                                fmt_ctx->streams[video_stream_index]->time_base,
                                                AV_TIME_BASE_Q));
                g_currentPositionMs.store(g_videoClock.load() / 1000);
            }
            // 音画同步核心机制
            if (session.audioRenderer && g_audioClock.load() > 0) {
                // 当前听到的声音时间 = 最新放进队列的音频时间 - 队列里还没播的延迟
                int64_t current_audio_clock = g_audioClock.load();
                int64_t pending_delay = session.audioRenderer->getPendingAudioDurationUs();
                int64_t exact_audio_pts = current_audio_clock - pending_delay;
                
                int64_t video_pts = g_videoClock.load();
                
                if (exact_audio_pts > 0 && video_pts > 0) {
                    int64_t diff = video_pts - exact_audio_pts;

                    // 核心双向对齐逻辑
                    if (diff > 0) {  
                        // 视频比音频快了，睡眠等待
                        // 【增加倍速支持】：等待时间需要除以播放速度
                        float speed = g_playbackSpeed.load();
                        speed = sanitizePlaybackSpeed(speed);
                        int64_t sleep_time = static_cast<int64_t>(diff / speed);
                        if (sleep_time > 100000) sleep_time = 100000; // 单次最多只睡 100ms
                        std::this_thread::sleep_for(std::chrono::microseconds(sleep_time));
                    }
                    // 完全移除所有的 drop frame 逻辑！
                }
            } else {
                // 没有音频时的降级逻辑（基于帧率）
                auto now = std::chrono::high_resolution_clock::now();
                auto elapsed = std::chrono::duration_cast<std::chrono::microseconds>(now - lastFrameTime).count();
                float speed = sanitizePlaybackSpeed(g_playbackSpeed.load());
                int64_t targetFrameDuration = static_cast<int64_t>(frameDuration / speed);
                if (targetFrameDuration < 1000) targetFrameDuration = 1000;
                if (elapsed < targetFrameDuration) {
                    std::this_thread::sleep_for(std::chrono::microseconds(targetFrameDuration - elapsed));
                }
            }

            // 渲染帧
            renderer.renderFrame(frame);
            eglSwapBuffers(display, surface);
            av_frame_free(&frame);

            lastFrameTime = std::chrono::high_resolution_clock::now();
        }

        // 6. 资源清理
        renderer.cleanup();
        cleanupEGL(display, surface, context);
        LOGD("RenderThread: Exit gracefully");
    });

    // 6. 等待线程结束
    demuxThread.join();
    decodeThread.join();
    audioDecodeThread.join();
    renderThread.join();//之所以放在这里等待进程结束，是为了观察yuv文件何时生成

    if (yuvExportEnabled) {
        LOGD("视频解析完成，输出文件: %s", outPath);
    } else {
        LOGD("视频解析完成，未导出YUV文件");
    }

    if (session.audioRenderer) {
        session.audioRenderer->stop();
        session.audioRenderer.reset();
    }

    // 7. 关闭文件
    if (yuv_file) {
        fclose(yuv_file);
    }
    avcodec_free_context(&audio_codec_ctx);
    avcodec_free_context(&codec_ctx);
    closeInput();

    // 8. 通知 Java 解码完成（只通知一次）
    jclass cls = env->GetObjectClass(thiz);
    if (cls != nullptr) {
        jmethodID method = env->GetMethodID(cls, "onVideoDecoded", "(Ljava/lang/String;)V");
        if (method != nullptr) {
            jstring result = env->NewStringUTF(yuvExportEnabled ? outPath : "播放完成");
            env->CallVoidMethod(thiz, method, result);
            env->DeleteLocalRef(result);
        }
        env->DeleteLocalRef(cls);
    }

    releaseJniStrings();
}

JNIEXPORT jstring JNICALL
Java_com_example_videodecoder_MainActivity_stringFromJNI(JNIEnv *env, jobject thiz) {
    return env->NewStringUTF("欢迎使用视频解码器");
}
JNIEXPORT void JNICALL
Java_com_example_videodecoder_MainActivity_setPlaybackSpeed(JNIEnv *env, jobject thiz, jfloat speed) {
    g_playbackSpeed.store(sanitizePlaybackSpeed(speed));
}

JNIEXPORT void JNICALL
Java_com_example_videodecoder_MainActivity_seekToPosition(JNIEnv *env, jobject thiz, jint progressMs) {
    int64_t targetMs = progressMs;
    int64_t durationMs = g_durationMs.load();
    if (targetMs < 0) targetMs = 0;
    if (durationMs > 0 && targetMs > durationMs) targetMs = durationMs;
    g_seekPositionMs.store(targetMs);
    g_currentPositionMs.store(targetMs);
    g_seekApplied.store(false);
    g_isSeeking.store(true);
}

JNIEXPORT jint JNICALL
Java_com_example_videodecoder_MainActivity_getDurationMs(JNIEnv*, jobject) {
    return static_cast<jint>(g_durationMs.load());
}

JNIEXPORT jint JNICALL
Java_com_example_videodecoder_MainActivity_getCurrentPositionMs(JNIEnv*, jobject) {
    return static_cast<jint>(g_currentPositionMs.load());
}

JNIEXPORT void JNICALL
Java_com_example_videodecoder_MainActivity_pauseDecoding(JNIEnv*, jobject) {
    if (!g_paused.exchange(true)) {  // 原子性地设置为true，并返回之前的值
        __android_log_print(ANDROID_LOG_DEBUG, "PauseResume", "Pausing playback");
    }
}

JNIEXPORT void JNICALL
Java_com_example_videodecoder_MainActivity_resumeDecoding(JNIEnv*, jobject) {
    if (g_paused.exchange(false)) {  // 原子性地设置为false，并返回之前的值
        __android_log_print(ANDROID_LOG_DEBUG, "PauseResume", "Resuming playback");
    }
}
JNIEXPORT void JNICALL
Java_com_example_videodecoder_MainActivity_nativeReleaseAudio(JNIEnv*, jobject) {
    g_stopRequested.store(true);
    g_isSeeking.store(false);
    g_seekApplied.store(false);
    g_paused.store(false);
}


} // extern "C"
