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
        if (g_isSeeking.load()) {
            int64_t target_pts_ms = g_seekPositionMs.load();
            int64_t target_pts_us = target_pts_ms * 1000;
            int seekRet = avformat_seek_file(fmt_ctx, -1, INT64_MIN, target_pts_us, INT64_MAX, AVSEEK_FLAG_BACKWARD);
            if (seekRet < 0) {
                int64_t seek_target = av_rescale_q(target_pts_us, AV_TIME_BASE_Q, fmt_ctx->streams[video_stream_index]->time_base);
                seekRet = av_seek_frame(fmt_ctx, video_stream_index, seek_target, AVSEEK_FLAG_BACKWARD);
            }
            if (seekRet < 0) {
                LOGD("Seek failed at %lld ms", static_cast<long long>(target_pts_ms));
            }
            g_seekApplied.store(true);
            av_packet_unref(pkt);
            continue;
        }

        if (g_paused) {
            std::this_thread::sleep_for(std::chrono::milliseconds(10));
            continue;
        }

        if (av_read_frame(fmt_ctx, pkt) < 0) {
            // 不立即退出 demux 线程，允许后续用户触发 seek 后继续读取数据
            std::this_thread::sleep_for(std::chrono::milliseconds(10));
            continue;
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
    video_queue.setDemuxFinished(true);
    audio_queue.setDemuxFinished(true);
    video_queue.push(nullptr);
    audio_queue.push(nullptr);
    LOGD("Demuxing finished (audio+video)");
}
