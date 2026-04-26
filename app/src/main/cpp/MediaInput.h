//
// Created by QiChen on 2026/4/26.
//

#ifndef VIDEODECODER_MEDIAINPUT_H
#define VIDEODECODER_MEDIAINPUT_H

#include <cstdint>

extern "C" {
#include <libavformat/avformat.h>
}

class MediaInput {
public:
    MediaInput() = default;
    ~MediaInput();

    MediaInput(const MediaInput&) = delete;
    MediaInput& operator=(const MediaInput&) = delete;

    int open(const char* path);
    void close();
    AVFormatContext* context() const { return formatContext; }

private:
    struct FdAvioSource {
        int fd = -1;
    };

    AVFormatContext* formatContext = nullptr;
    AVIOContext* avioContext = nullptr;
    uint8_t* avioBuffer = nullptr;
    FdAvioSource* fdSource = nullptr;
    int duplicatedFd = -1;

    static int readFdPacket(void* opaque, uint8_t* buffer, int bufferSize);
    static int64_t seekFdPacket(void* opaque, int64_t offset, int whence);
    int openFdInput(const char* fdPath);
};

#endif //VIDEODECODER_MEDIAINPUT_H
