//
// Created by QiChen on 2025/3/30.
//

#include "Decoder.h"
#include "queue.h"
#include <queue>
#include <android/log.h>

// 外部声明全局暂停标志
extern std::atomic<bool> g_paused;
extern std::atomic<bool> g_isSeeking;
extern std::atomic<bool> g_stopRequested;


#define LOGE(format, ...) __android_log_print(ANDROID_LOG_ERROR, "Tag", format, ##__VA_ARGS__)




void Decoder::decode(AVCodecContext* codec_ctx, FILE* yuv_file, PacketQueue& queue, FrameQueue& frameQueue) {
    AVFrame *frame = av_frame_alloc();
    int64_t last_pts = AV_NOPTS_VALUE;

    while (true) {
        if (g_stopRequested.load()) {
            break;
        }
        if (g_isSeeking.load()) {
            avcodec_flush_buffers(codec_ctx);
            frameQueue.clear();
            std::this_thread::sleep_for(std::chrono::milliseconds(2));
            continue;
        }
        // 处理暂停状态 - 使用更高效的检查方式
        while (g_paused.load()) {
            if (g_stopRequested.load()) {
                break;
            }
            std::this_thread::sleep_for(std::chrono::milliseconds(10));
            continue;
        }
        if (g_stopRequested.load()) {
            break;
        }
        AVPacket *pkt = queue.pop();

        if (pkt == nullptr) { // End of stream
            if (queue.isDemuxFinished() || g_stopRequested.load()) {
                break;
            }
            continue;
        }
        // 记录最后一个有效包的pts
        if (pkt->pts != AV_NOPTS_VALUE) {
            last_pts = pkt->pts;
        }
        if (avcodec_send_packet(codec_ctx, pkt) == 0) {
            while (avcodec_receive_frame(codec_ctx, frame) == 0) {
                if (g_isSeeking.load() || g_stopRequested.load()) {
                    av_frame_unref(frame);
                    break;
                }
                fwrite(frame->data[0], 1, frame->width * frame->height, yuv_file);
                fwrite(frame->data[1], 1, frame->width * frame->height / 4, yuv_file);
                fwrite(frame->data[2], 1, frame->width * frame->height / 4, yuv_file);
                //------------------------------------------
                //推入frame到渲染队列
                if (!g_paused.load()) {  // 再次检查暂停状态
                    AVFrame* frameCopy = av_frame_clone(frame);
                    if (frameCopy) {
                        frameQueue.push(frameCopy);
                    }
                } else {
                    av_frame_unref(frame);
                }
                //----------
            }

        }
        av_packet_free(&pkt);
    }
    av_frame_free(&frame);
    LOGD("Decoder thread finished");
}

