#include <jni.h>
#include <string>
#include <iostream>
#include <android/log.h>
#include <thread>
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
std::atomic<int64_t> g_seekPositionMs{0};
std::atomic<bool> g_paused{false};
extern "C" {
//日志宏定义
#define LOG_TAG "NativeLib"

#include <libavformat/avformat.h>
#include <libavutil/avutil.h>
#include <libavcodec/avcodec.h>
#include <libavutil/time.h>
#include <libswresample/swresample.h>
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "YourTag", __VA_ARGS__)

/* ========================== 全局变量声明 ========================== */
// 全局变量，用于保存 Java 层传入的 Surface 对应的 NativeWindow
ANativeWindow* g_nativeWindow = nullptr;
std::atomic<bool> g_decodingFinished{false};// 解码完成标志

// 全局暂停控制变量

std::atomic<int64_t> g_pauseTime{0};
//音频相关变量
AudioRenderer* g_audioRenderer = nullptr;
std::atomic<bool> g_audioDecodingFinished{false};
// 全局变量添加
std::atomic<int64_t> g_audioClock{0};
std::atomic<int64_t> g_videoClock{0};
// 在全局变量区域添加
std::atomic<int64_t> g_durationMs{0};
std::atomic<int64_t> g_currentPositionMs{0};
// 播放速度控制变量
std::atomic<float> g_playbackSpeed{1.0f};

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
    if (g_nativeWindow) {
        ANativeWindow_release(g_nativeWindow);
        g_nativeWindow = nullptr;
    }
    g_nativeWindow = ANativeWindow_fromSurface(env, surface);
}
//视频解码主函数
JNIEXPORT void JNICALL
Java_com_example_videodecoder_MainActivity_decodeVideo(JNIEnv *env, jobject thiz,
                                                       jstring video_path, jstring output_path) {
    /* ------------------------- 初始化阶段 ------------------------- */
    //获取输入输出路径
    const char *path = env->GetStringUTFChars(video_path, nullptr);
    const char *outPath = env->GetStringUTFChars(output_path, nullptr);

    const char* native_video_path = env->GetStringUTFChars(video_path, nullptr);
    LOGD("开始解析视频: %s", native_video_path);
    // 使用完后释放内存
    env->ReleaseStringUTFChars(video_path, native_video_path);

    // 1. 打开输入文件
    AVFormatContext *fmt_ctx = nullptr;
    if (avformat_open_input(&fmt_ctx, path, nullptr, nullptr) != 0) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "Could not open input file: %s", path);
        env->ReleaseStringUTFChars(video_path, path);
        env->ReleaseStringUTFChars(output_path, outPath);
        return;
    }

    // 2. 找到视频流和音频流
    int video_stream_index = av_find_best_stream(fmt_ctx, AVMEDIA_TYPE_VIDEO, -1, -1, nullptr, 0);
    int audio_stream_index = av_find_best_stream(fmt_ctx, AVMEDIA_TYPE_AUDIO, -1, -1, nullptr, 0);
    if (video_stream_index < 0 && audio_stream_index < 0) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "No video stream found or audio stream found");
        avformat_close_input(&fmt_ctx);
        env->ReleaseStringUTFChars(video_path, path);
        env->ReleaseStringUTFChars(output_path, outPath);
        return;
    }

    // 3. 初始化解码器
    AVCodec *codec = avcodec_find_decoder(fmt_ctx->streams[video_stream_index]->codecpar->codec_id);
    if (!codec) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "Decoder not found");
        avformat_close_input(&fmt_ctx);
        env->ReleaseStringUTFChars(video_path, path);
        env->ReleaseStringUTFChars(output_path, outPath);
        return;
    }
    AVCodecContext *codec_ctx = avcodec_alloc_context3(codec);
    avcodec_parameters_to_context(codec_ctx, fmt_ctx->streams[video_stream_index]->codecpar);
    if (avcodec_open2(codec_ctx, codec, nullptr) < 0) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "Could not open codec");
        avcodec_free_context(&codec_ctx);
        avformat_close_input(&fmt_ctx);
        env->ReleaseStringUTFChars(video_path, path);
        env->ReleaseStringUTFChars(output_path, outPath);
        return;
    }
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
                g_audioRenderer = new AudioRenderer();
                if (!g_audioRenderer->init(audio_codec_ctx->sample_rate,
                                           audio_codec_ctx->channels,
                                           AAUDIO_FORMAT_PCM_I16)) {
                    __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "Failed to initialize audio renderer");
                    delete g_audioRenderer;
                    g_audioRenderer = nullptr;
                    // === 新增同步设置 ===
                    int64_t cacheTimeMs = 300; // 自定义缓存时间
                    g_audioRenderer->setSyncThreshold(100); // 同步阈值（单位毫秒）
                    g_audioRenderer->setAudioStartTime(av_gettime_relative() / 1000 + cacheTimeMs);
                    // === 新增结束 ===

                    g_audioRenderer->start();
                } else {
                    g_audioRenderer->start();
                }
            }
        }
    }

    // 4. 打开输出文件
    FILE *yuv_file = fopen(outPath, "wb");
    if (!yuv_file) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "Failed to open output file at %s", outPath);
        avcodec_free_context(&codec_ctx);
        avformat_close_input(&fmt_ctx);
        env->ReleaseStringUTFChars(video_path, path);
        env->ReleaseStringUTFChars(output_path, outPath);
        return;
    }
    /* ------------------------- 线程创建阶段 ------------------------- */
    // 1. 创建队列
    PacketQueue video_packet_queue;//视频数据包队列
    PacketQueue audio_packet_queue;//音频数据包队列
    FrameQueue frameQueue;//帧队列
    FrameQueue audio_frame_queue;//音频帧队列

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
        if (!audio_codec_ctx || !g_audioRenderer) return;

        AVFrame *frame = av_frame_alloc();
        SwrContext* swr_ctx = swr_alloc_set_opts(nullptr,
                                                 av_get_default_channel_layout(audio_codec_ctx->channels),
                                                 AV_SAMPLE_FMT_S16,
                                                 audio_codec_ctx->sample_rate,
                                                 av_get_default_channel_layout(audio_codec_ctx->channels),
                                                 audio_codec_ctx->sample_fmt,
                                                 audio_codec_ctx->sample_rate,
                                                 0, nullptr);
        swr_init(swr_ctx);

        while (true) {
            if (g_paused) {
                std::this_thread::sleep_for(std::chrono::milliseconds(10));
                continue;
            }

            // 处理 Seek 请求的资源清理
            if (g_isSeeking.load()) {
                // 等待 Demuxer 线程执行完底层 av_seek_frame 操作
                // (我们在 Demuxer.cpp 中执行了 av_seek_frame，然后挂起等这里清空队列)
                
                // 1. 清空所有的旧数据队列
                video_packet_queue.clear();
                audio_packet_queue.clear();
                frameQueue.clear();
                
                // 2. 清空 AAudio 积压
                if (g_audioRenderer) {
                    g_audioRenderer->cleanup(); // 销毁底层缓冲
                    g_audioRenderer->init(audio_codec_ctx->sample_rate, audio_codec_ctx->channels, AAUDIO_FORMAT_PCM_I16);
                    g_audioRenderer->start();
                }

                // 3. 清空解码器内部的历史缓存帧
                avcodec_flush_buffers(codec_ctx);
                if (audio_codec_ctx) {
                    avcodec_flush_buffers(audio_codec_ctx);
                }
                
                // 4. 重置时钟基准
                int64_t target_pts_us = g_seekPositionMs.load() * 1000;
                g_videoClock.store(target_pts_us);
                g_audioClock.store(target_pts_us);
                if (g_audioRenderer) {
                    g_audioRenderer->setFirstAudioPts(target_pts_us);
                }
                
                // 结束 Seek 状态，恢复正常播放
                g_isSeeking.store(false);
                continue; 
            }

            // 【重磅修复】：防止音频解码过快导致时钟跑飞
            // 控制音频队列的积压时长最高不超过 500ms
            if (g_audioRenderer && g_audioRenderer->getPendingAudioDurationUs() > 500000) {
                std::this_thread::sleep_for(std::chrono::milliseconds(10));
                continue;
            }

            AVPacket *pkt = audio_packet_queue.pop();
            if (pkt == nullptr) break;

            if (avcodec_send_packet(audio_codec_ctx, pkt) == 0) {
                while (avcodec_receive_frame(audio_codec_ctx, frame) == 0) {
                    
                    // 重采样
                    uint8_t* output;
                    int out_samples = swr_get_out_samples(swr_ctx, frame->nb_samples);
                    av_samples_alloc(&output, nullptr, audio_codec_ctx->channels,
                                     out_samples, AV_SAMPLE_FMT_S16, 0);
                    out_samples = swr_convert(swr_ctx, &output, out_samples,
                                              (const uint8_t**)frame->data, frame->nb_samples);

                    if (!g_paused && out_samples > 0) {
                        g_audioRenderer->writeData(output, out_samples);
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
        swr_free(&swr_ctx);
        g_audioDecodingFinished = true;
    });

    //4.视频渲染线程
    std::thread renderThread([&]() {

        // 1. 检查NativeWindow有效性
        if (!g_nativeWindow) {
            LOGE("RenderThread: NativeWindow is not valid");
            return;
        }

        // 2. 初始化EGL环境
        EGLDisplay display = initEGLDisplay();
        if (display == EGL_NO_DISPLAY) {
            LOGE("RenderThread: EGL display initialization failed");
            return;
        }

        EGLSurface surface = createEGLSurface(display, g_nativeWindow);
        if (surface == EGL_NO_SURFACE) {
            LOGE("RenderThread: EGL surface creation failed");
            eglTerminate(display);
            return;
        }

        EGLContext context = createEGLContext(display, surface);
        if (context == EGL_NO_CONTEXT) {
            LOGE("RenderThread: EGL context creation failed");
            eglDestroySurface(display, surface);
            eglTerminate(display);
            return;
        }

        // 3. 设置窗口缓冲几何
        int width = ANativeWindow_getWidth(g_nativeWindow);
        int height = ANativeWindow_getHeight(g_nativeWindow);
        ANativeWindow_setBuffersGeometry(g_nativeWindow, width, height, WINDOW_FORMAT_RGBA_8888);

        // 4. 初始化渲染器
        Renderer renderer;
        if (!renderer.init(width, height)) {
            LOGE("RenderThread: Renderer initialization failed");
            cleanupEGL(display, surface, context);
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

        int consecutive_drops = 0; // 连续丢帧计数器

        while (true) {
            // 检查暂停状态
            if (g_paused) {
                std::this_thread::sleep_for(std::chrono::milliseconds(100));
                continue;
            }
            AVFrame* frame = frameQueue.pop(); // 阻塞式获取
            if (!frame) {
                if (g_decodingFinished) break; // 终止信号
                continue;
            }
            // 更新视频时钟
            if (frame->pts != AV_NOPTS_VALUE) {
                g_videoClock.store(av_rescale_q(frame->pts,
                                                fmt_ctx->streams[video_stream_index]->time_base,
                                                AV_TIME_BASE_Q));
            }
            // 音画同步核心机制
            if (g_audioRenderer && g_audioClock.load() > 0) {
                // 当前听到的声音时间 = 最新放进队列的音频时间 - 队列里还没播的延迟
                int64_t current_audio_clock = g_audioClock.load();
                int64_t pending_delay = g_audioRenderer->getPendingAudioDurationUs();
                int64_t exact_audio_pts = current_audio_clock - pending_delay;
                
                int64_t video_pts = g_videoClock.load();
                
                if (exact_audio_pts > 0 && video_pts > 0) {
                    int64_t diff = video_pts - exact_audio_pts;

                    // 核心双向对齐逻辑
                    if (diff > 0) {  
                        // 视频比音频快了，睡眠等待
                        // 【增加倍速支持】：等待时间需要除以播放速度
                        int64_t sleep_time = diff / g_playbackSpeed.load();
                        if (sleep_time > 100000) sleep_time = 100000; // 单次最多只睡 100ms
                        std::this_thread::sleep_for(std::chrono::microseconds(sleep_time));
                    }
                    // 完全移除所有的 drop frame 逻辑！
                }
            } else {
                // 没有音频时的降级逻辑（基于帧率）
                auto now = std::chrono::high_resolution_clock::now();
                auto elapsed = std::chrono::duration_cast<std::chrono::microseconds>(now - lastFrameTime).count();
                if (elapsed < frameDuration) {
                    std::this_thread::sleep_for(std::chrono::microseconds(frameDuration - elapsed));
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

    //先让解析线程结束，再等待渲染线程结束
    const char* native_output_path = env->GetStringUTFChars(output_path, nullptr);
    LOGD("视频解析完成，输出文件: %s", native_output_path);
    // 使用完后释放内存
    env->ReleaseStringUTFChars(output_path, native_output_path);


    // 通知 Java 解码完成
    jclass clazz = env->GetObjectClass(thiz);
    if (clazz == nullptr) {
        LOGD("无法获取 Java 类");
        return;
    }

    // 获取 Java 方法 ID
    jmethodID methodID = env->GetMethodID(clazz, "onVideoDecoded", "(Ljava/lang/String;)V");
    if (methodID == nullptr) {
        LOGD("无法找到 Java 方法 onVideoDecoded");
        return;
    }


    const char* utf8_output_path = env->GetStringUTFChars(output_path, nullptr);
    if (utf8_output_path == nullptr) {
        LOGD("无法转换 jstring 到 const char*");
        return; // 错误处理
    }

    // 调用 Java 方法，通知解析完成
    jstring result = env->NewStringUTF(utf8_output_path);
    env->CallVoidMethod(thiz, methodID, result);

    // 记得释放字符串
    env->ReleaseStringUTFChars(output_path, utf8_output_path);

    if (g_audioRenderer) {
        g_audioRenderer->stop();
        delete g_audioRenderer;
        g_audioRenderer = nullptr;
    }
    renderThread.join();//之所以放在这里等待进程结束，是为了观察yuv文件何时生成

    // 7. 关闭文件
    fclose(yuv_file);
    avcodec_free_context(&codec_ctx);
    avformat_close_input(&fmt_ctx);
    env->ReleaseStringUTFChars(video_path, path);
    env->ReleaseStringUTFChars(output_path, outPath);

    // 8. 通知 Java 解码完成
    jclass cls = env->GetObjectClass(thiz);
    jmethodID method = env->GetMethodID(cls, "onVideoDecoded", "(Ljava/lang/String;)V");
    env->CallVoidMethod(thiz, method,
                        env->NewStringUTF("YUV文件已生成:在/Android/data/com.example.videodecoder/files/"));
}

JNIEXPORT jstring JNICALL
Java_com_example_videodecoder_MainActivity_stringFromJNI(JNIEnv *env, jobject thiz) {
    return env->NewStringUTF("欢迎使用视频解码器");
}
JNIEXPORT void JNICALL
Java_com_example_videodecoder_MainActivity_setPlaybackSpeed(JNIEnv *env, jobject thiz, jfloat speed) {
    g_playbackSpeed.store(speed);
}

JNIEXPORT void JNICALL
Java_com_example_videodecoder_MainActivity_seekToPosition(JNIEnv *env, jobject thiz, jint progressMs) {
    g_seekPositionMs.store(progressMs);
    g_isSeeking.store(true);
}

JNIEXPORT void JNICALL
Java_com_example_videodecoder_MainActivity_pauseDecoding(JNIEnv*, jobject) {
    if (!g_paused.exchange(true)) {  // 原子性地设置为true，并返回之前的值
        __android_log_print(ANDROID_LOG_DEBUG, "PauseResume", "Pausing playback");

        // 先暂停音频
        if (g_audioRenderer) {
            g_audioRenderer->pause();
        }

        // 记录暂停时间
        g_pauseTime = av_gettime_relative();
    }
}

JNIEXPORT void JNICALL
Java_com_example_videodecoder_MainActivity_resumeDecoding(JNIEnv*, jobject) {
    if (g_paused.exchange(false)) {  // 原子性地设置为false，并返回之前的值
        __android_log_print(ANDROID_LOG_DEBUG, "PauseResume", "Resuming playback");

        // 计算暂停持续时间
        int64_t pauseDuration = av_gettime_relative() - g_pauseTime;

        // 调整时钟基准
        g_audioClock.store(g_audioClock.load() + pauseDuration);
        g_videoClock.store(g_videoClock.load() + pauseDuration);

        // 恢复音频
        if (g_audioRenderer) {
            g_audioRenderer->resume();
        }
    }
}
JNIEXPORT void JNICALL
Java_com_example_videodecoder_MainActivity_nativeReleaseAudio(JNIEnv*, jobject) {
    if (g_audioRenderer) {
        delete g_audioRenderer;
        g_audioRenderer = nullptr;
    }
}


} // extern "C"