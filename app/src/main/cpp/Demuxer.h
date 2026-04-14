//
// Created by QiChen on 2025/3/30.
//

#ifndef VIDEODECODER_DEMUXER_H
#define VIDEODECODER_DEMUXER_H

#include <atomic>
#include <thread>
#include <chrono>

extern "C" {
#include <libavformat/avformat.h>
}

extern std::atomic<bool> g_isSeeking;
extern std::atomic<bool> g_seekApplied;
extern std::atomic<int64_t> g_seekPositionMs;
extern std::atomic<bool> g_paused;
extern std::atomic<bool> g_stopRequested;
extern "C" {

}

class PacketQueue;

class Demuxer {
public:
    void demux(AVFormatContext* fmt_ctx, int video_stream_index, PacketQueue& queue);
    void demux(AVFormatContext* fmt_ctx,
               int video_stream_index, PacketQueue& video_queue,
               int audio_stream_index, PacketQueue& audio_queue);
};

#endif //VIDEODECODER_DEMUXER_H
