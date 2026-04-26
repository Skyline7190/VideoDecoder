//
// Created by QiChen on 2025/3/30.
//

#include "Decoder.h"
#include "queue.h"
#include <queue>
#include <android/log.h>

extern "C" {
#include <libavutil/pixfmt.h>
#include <libswscale/swscale.h>
}

#define LOGE(format, ...) __android_log_print(ANDROID_LOG_ERROR, "Tag", format, ##__VA_ARGS__)




namespace {
void writePlane(FILE* file, const uint8_t* data, int linesize, int width, int height) {
    if (!file || !data || width <= 0 || height <= 0) return;
    for (int y = 0; y < height; ++y) {
        fwrite(data + y * linesize, 1, width, file);
    }
}
}

void Decoder::decode(AVCodecContext* codec_ctx, FILE* yuv_file, PacketQueue& queue,
                     FrameQueue& frameQueue, PlaybackState& state) {
    AVFrame *frame = av_frame_alloc();
    AVFrame *yuvFrame = av_frame_alloc();
    SwsContext* sws_ctx = nullptr;
    int cachedWidth = 0;
    int cachedHeight = 0;
    AVPixelFormat cachedFormat = AV_PIX_FMT_NONE;

    while (true) {
        if (state.stopRequested.load()) {
            break;
        }
        if (state.isSeeking.load()) {
            avcodec_flush_buffers(codec_ctx);
            frameQueue.clear();
            std::this_thread::sleep_for(std::chrono::milliseconds(2));
            continue;
        }
        // 处理暂停状态 - 使用更高效的检查方式
        while (state.paused.load()) {
            if (state.stopRequested.load()) {
                break;
            }
            std::this_thread::sleep_for(std::chrono::milliseconds(10));
            continue;
        }
        if (state.stopRequested.load()) {
            break;
        }
        AVPacket *pkt = queue.pop();

        if (pkt == nullptr) { // End of stream
            if (queue.isDemuxFinished() || state.stopRequested.load()) {
                break;
            }
            continue;
        }
        if (avcodec_send_packet(codec_ctx, pkt) == 0) {
            while (avcodec_receive_frame(codec_ctx, frame) == 0) {
                if (state.isSeeking.load() || state.stopRequested.load()) {
                    av_frame_unref(frame);
                    break;
                }
                AVPixelFormat srcFormat = static_cast<AVPixelFormat>(frame->format);
                if (!sws_ctx || cachedWidth != frame->width || cachedHeight != frame->height ||
                    cachedFormat != srcFormat) {
                    sws_freeContext(sws_ctx);
                    sws_ctx = sws_getContext(frame->width, frame->height, srcFormat,
                                             frame->width, frame->height, AV_PIX_FMT_YUV420P,
                                             SWS_BILINEAR, nullptr, nullptr, nullptr);

                    av_frame_unref(yuvFrame);
                    yuvFrame->format = AV_PIX_FMT_YUV420P;
                    yuvFrame->width = frame->width;
                    yuvFrame->height = frame->height;
                    if (!sws_ctx || av_frame_get_buffer(yuvFrame, 1) < 0) {
                        LOGE("Failed to allocate YUV420P conversion buffer");
                        sws_freeContext(sws_ctx);
                        sws_ctx = nullptr;
                        cachedWidth = 0;
                        cachedHeight = 0;
                        cachedFormat = AV_PIX_FMT_NONE;
                        av_frame_unref(frame);
                        continue;
                    }
                    cachedWidth = frame->width;
                    cachedHeight = frame->height;
                    cachedFormat = srcFormat;
                }

                if (av_frame_make_writable(yuvFrame) < 0) {
                    LOGE("YUV420P conversion buffer is not writable");
                    av_frame_unref(frame);
                    continue;
                }

                sws_scale(sws_ctx,
                          frame->data,
                          frame->linesize,
                          0,
                          frame->height,
                          yuvFrame->data,
                          yuvFrame->linesize);
                yuvFrame->pts = frame->pts;
                yuvFrame->pkt_dts = frame->pkt_dts;

                int chromaWidth = (yuvFrame->width + 1) / 2;
                int chromaHeight = (yuvFrame->height + 1) / 2;
                writePlane(yuv_file, yuvFrame->data[0], yuvFrame->linesize[0],
                           yuvFrame->width, yuvFrame->height);
                writePlane(yuv_file, yuvFrame->data[1], yuvFrame->linesize[1],
                           chromaWidth, chromaHeight);
                writePlane(yuv_file, yuvFrame->data[2], yuvFrame->linesize[2],
                           chromaWidth, chromaHeight);
                //------------------------------------------
                //推入frame到渲染队列
                if (!state.paused.load()) {  // 再次检查暂停状态
                    AVFrame* frameCopy = av_frame_clone(yuvFrame);
                    if (frameCopy) {
                        frameQueue.push(frameCopy);
                    }
                }
                //----------
                av_frame_unref(frame);
            }

        }
        av_packet_free(&pkt);
    }
    sws_freeContext(sws_ctx);
    av_frame_free(&yuvFrame);
    av_frame_free(&frame);
    LOGD("Decoder thread finished");
}

