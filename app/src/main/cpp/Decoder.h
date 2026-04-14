//
// Created by QiChen on 2025/3/30.
//

#ifndef VIDEODECODER_DECODER_H
#define VIDEODECODER_DECODER_H


#include "queue.h"

extern "C" {
#include <libavcodec/avcodec.h>
#include <libavutil/imgutils.h>
#include <libswresample/swresample.h>
}

class PacketQueue;

class Decoder {
public:
    void decode(AVCodecContext* codec_ctx, FILE* yuv_file, PacketQueue& queue, FrameQueue& frameQueue);
private:
    FrameQueue frameQueue;  // 声明 FrameQueue 成员变量

};


#endif //VIDEODECODER_DECODER_H