//
// Created by QiChen on 2025/3/30.
//

#ifndef VIDEODECODER_DEMUXER_H
#define VIDEODECODER_DEMUXER_H

#include "PlaybackState.h"
#include <atomic>
#include <thread>
#include <chrono>

extern "C" {
#include <libavformat/avformat.h>
}

class PacketQueue;

class Demuxer {
public:
    void demux(AVFormatContext* fmt_ctx,
               int video_stream_index, PacketQueue& video_queue,
               int audio_stream_index, PacketQueue& audio_queue,
               PlaybackState& state);
};

#endif //VIDEODECODER_DEMUXER_H
