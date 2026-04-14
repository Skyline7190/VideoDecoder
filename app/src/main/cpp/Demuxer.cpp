//
// Created by QiChen on 2025/3/30.
//

#include "Demuxer.h"
#include "queue.h"
//单队列
void Demuxer::demux(AVFormatContext* fmt_ctx, int video_stream_index, PacketQueue& queue) {
    AVPacket *pkt = av_packet_alloc();
    while (av_read_frame(fmt_ctx, pkt) >= 0) {
        if (pkt->stream_index == video_stream_index) {
            AVPacket *pktCopy = av_packet_alloc();
            av_packet_ref(pktCopy, pkt);
            queue.push(pktCopy);
        }
        av_packet_unref(pkt);
    }
    av_packet_free(&pkt);
    queue.push(nullptr); // Signal end of stream
    queue.setDemuxFinished(true);
}
//双队列
void Demuxer::demux(AVFormatContext* fmt_ctx,
                    int video_stream_index, PacketQueue& video_queue,
                    int audio_stream_index, PacketQueue& audio_queue) {
    AVPacket *pkt = av_packet_alloc();
    while (true) {
        if (g_paused) {
            std::this_thread::sleep_for(std::chrono::milliseconds(10));
            continue;
        }

        if (g_isSeeking.load()) {
            // 执行 FFmpeg 的真实文件跳转
            int64_t target_pts_ms = g_seekPositionMs.load();
            int64_t target_pts_us = target_pts_ms * 1000;
            
            // 跳转到目标位置，使用 AVSEEK_FLAG_BACKWARD 确保跳转到关键帧防止花屏
            int64_t seek_target = av_rescale_q(target_pts_us, AV_TIME_BASE_Q, fmt_ctx->streams[video_stream_index]->time_base);
            av_seek_frame(fmt_ctx, video_stream_index, seek_target, AVSEEK_FLAG_BACKWARD);
            
            // 清理状态交给外层的 native-lib 处理 (队列清理、时钟重置等)
            // 等待外层处理完毕解除 Seek 状态
            while(g_isSeeking.load()) {
                std::this_thread::sleep_for(std::chrono::milliseconds(5));
            }
        }

        if (av_read_frame(fmt_ctx, pkt) < 0) {
            break;
        }

        if (pkt->stream_index == video_stream_index) {
            AVPacket *pktCopy = av_packet_alloc();
            av_packet_ref(pktCopy, pkt);
            video_queue.push(pktCopy);
            //LOGD("Pushed video packet to queue");
        } else if (pkt->stream_index == audio_stream_index) {
            AVPacket *pktCopy = av_packet_alloc();
            av_packet_ref(pktCopy, pkt);
            audio_queue.push(pktCopy);
            //LOGD("Pushed audio packet to queue");
        }
        av_packet_unref(pkt);
    }
    av_packet_free(&pkt);

    // Send end signals
    video_queue.push(nullptr);
    audio_queue.push(nullptr);
    LOGD("Demuxing finished (audio+video)");
}