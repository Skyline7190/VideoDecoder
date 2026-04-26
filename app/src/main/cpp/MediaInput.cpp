//
// Created by QiChen on 2026/4/26.
//

#include "MediaInput.h"

#include <android/log.h>
#include <cerrno>
#include <cstdlib>
#include <cstring>
#include <unistd.h>

#define LOG_TAG "MediaInput"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

extern "C" {
#include <libavutil/error.h>
#include <libavutil/mem.h>
}

MediaInput::~MediaInput() {
    close();
}

int MediaInput::open(const char* path) {
    close();
    if (!path) {
        return AVERROR(EINVAL);
    }
    if (strncmp(path, "fd:", 3) == 0) {
        return openFdInput(path);
    }
    return avformat_open_input(&formatContext, path, nullptr, nullptr);
}

void MediaInput::close() {
    if (formatContext) {
        avformat_close_input(&formatContext);
    }
    if (avioContext) {
        av_freep(&avioContext->buffer);
        avio_context_free(&avioContext);
    } else if (avioBuffer) {
        av_freep(&avioBuffer);
    }
    if (duplicatedFd >= 0) {
        ::close(duplicatedFd);
        duplicatedFd = -1;
    }
    delete fdSource;
    fdSource = nullptr;
}

int MediaInput::openFdInput(const char* fdPath) {
    char* end = nullptr;
    long javaFd = strtol(fdPath + 3, &end, 10);
    if (!end || *end != '\0' || javaFd < 0) {
        return AVERROR(EINVAL);
    }

    duplicatedFd = dup(static_cast<int>(javaFd));
    if (duplicatedFd < 0) {
        return AVERROR(errno);
    }
    if (lseek(duplicatedFd, 0, SEEK_SET) < 0) {
        LOGD("Input fd is not seekable at start: %d", errno);
    }

    fdSource = new FdAvioSource();
    fdSource->fd = duplicatedFd;

    constexpr int avioBufferSize = 64 * 1024;
    avioBuffer = static_cast<uint8_t*>(av_malloc(avioBufferSize));
    if (!avioBuffer) {
        return AVERROR(ENOMEM);
    }

    avioContext = avio_alloc_context(avioBuffer,
                                     avioBufferSize,
                                     0,
                                     fdSource,
                                     readFdPacket,
                                     nullptr,
                                     seekFdPacket);
    if (!avioContext) {
        return AVERROR(ENOMEM);
    }
    avioBuffer = nullptr;

    formatContext = avformat_alloc_context();
    if (!formatContext) {
        return AVERROR(ENOMEM);
    }
    formatContext->pb = avioContext;
    formatContext->flags |= AVFMT_FLAG_CUSTOM_IO;
    return avformat_open_input(&formatContext, nullptr, nullptr, nullptr);
}

int MediaInput::readFdPacket(void* opaque, uint8_t* buffer, int bufferSize) {
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

int64_t MediaInput::seekFdPacket(void* opaque, int64_t offset, int whence) {
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
