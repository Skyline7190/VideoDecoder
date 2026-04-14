//
// Created by QiChen on 2025/3/31.
//

#include "AudioRender.h"
#include <android/log.h>
#include <thread>

#define LOG_TAG "AudioRenderer"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
extern std::atomic<bool> g_paused;
extern std::atomic<int64_t> g_pauseTime;

AudioRenderer::AudioRenderer() = default;

AudioRenderer::~AudioRenderer() {
    cleanup();
}

bool AudioRenderer::init(int sampleRate, int channels, int format) {
    this->sampleRate = sampleRate;
    this->channelCount = channels;
    this->format = format;
    AAudioStreamBuilder* builder = nullptr;
    aaudio_result_t result = AAudio_createStreamBuilder(&builder);
    if (result != AAUDIO_OK) {
        LOGE("Failed to create stream builder: %s", AAudio_convertResultToText(result));
        return false;
    }

    AAudioStreamBuilder_setFormat(builder, static_cast<aaudio_format_t>(format));
    AAudioStreamBuilder_setChannelCount(builder, channels);
    AAudioStreamBuilder_setSampleRate(builder, sampleRate);
    AAudioStreamBuilder_setPerformanceMode(builder, AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
    AAudioStreamBuilder_setDataCallback(builder, dataCallback, this);
    AAudioStreamBuilder_setDirection(builder, AAUDIO_DIRECTION_OUTPUT);

    result = AAudioStreamBuilder_openStream(builder, &stream);
    AAudioStreamBuilder_delete(builder);

    if (result != AAUDIO_OK) {
        LOGE("Failed to open stream: %s", AAudio_convertResultToText(result));
        stream = nullptr;
        return false;
    }

    initialized = true;
    LOGD("Audio renderer initialized: SR=%d, channels=%d, format=%d",
         sampleRate, channels, format);
    return true;
}

void AudioRenderer::start() {
    if (!initialized) return;

    aaudio_result_t result = AAudioStream_requestStart(stream);
    if (result != AAUDIO_OK) {
        LOGE("Failed to start stream: %s", AAudio_convertResultToText(result));
    }
}

void AudioRenderer::stop() {
    if (!initialized) return;

    AAudioStream_requestStop(stream);
}

void AudioRenderer::cleanup() {
    if (stream) {
        AAudioStream_close(stream);
        stream = nullptr;
    }
    initialized = false;

    std::lock_guard<std::mutex> lock(queueMutex);
    while (!audioQueue.empty()) {
        audioQueue.pop();
    }
    queuedAudioBytes = 0;
}

void AudioRenderer::writeData(const uint8_t* data, int32_t numFrames) {
    if (!initialized) return;

    int32_t frameSize = AAudioStream_getChannelCount(stream) * AAudioStream_getSamplesPerFrame(stream);
    int32_t dataSize = numFrames * frameSize;

    std::vector<uint8_t> buffer(data, data + dataSize);

    std::lock_guard<std::mutex> lock(queueMutex);
    audioQueue.push(std::move(buffer));
    queuedAudioBytes += dataSize;
}

void AudioRenderer::setVolume(float volume) {
    this->volume = volume;
}
void AudioRenderer::setSyncThreshold(int64_t thresholdMs) {
    syncThresholdMs.store(thresholdMs);
}

void AudioRenderer::setAudioStartTime(int64_t startTimeMs) {
    audioStartTimeMs.store(startTimeMs);
    isAudioStarted.store(false);
}
extern "C" {
#include <libavutil/time.h>
}
aaudio_data_callback_result_t AudioRenderer::dataCallback(
        AAudioStream* stream,
        void* userData,
        void* audioData,
        int32_t numFrames) {
    auto* renderer = static_cast<AudioRenderer*>(userData);
    if (!renderer || !renderer->initialized) {
        memset(audioData, 0, numFrames * AAudioStream_getChannelCount(stream) *
                             (AAudioStream_getFormat(stream) == AAUDIO_FORMAT_PCM_I16 ? sizeof(int16_t) : sizeof(float)));
        return AAUDIO_CALLBACK_RESULT_STOP;
    }
    // === 新增同步逻辑开始 ===
    if (!renderer->isAudioStarted.load()) {
        int64_t currentTime = av_gettime_relative() / 1000; // 当前时间（毫秒）
        int64_t startTime = renderer->audioStartTimeMs.load();
        int64_t threshold = renderer->syncThresholdMs.load();

        if (currentTime < startTime - threshold) {
            // 未到开始时间，填充静音
            memset(audioData, 0, numFrames * AAudioStream_getChannelCount(stream) *
                                 (AAudioStream_getFormat(stream) == AAUDIO_FORMAT_PCM_I16 ? sizeof(int16_t) : sizeof(float)));
            return AAUDIO_CALLBACK_RESULT_CONTINUE;
        } else {
            renderer->isAudioStarted.store(true);
        }
    }
    // === 新增同步逻辑结束 ===
    int32_t framesWritten = 0;
    auto* output = static_cast<uint8_t*>(audioData);
    int32_t channelCount = AAudioStream_getChannelCount(stream);
    aaudio_format_t format = AAudioStream_getFormat(stream);
    int32_t frameSize = channelCount * (format == AAUDIO_FORMAT_PCM_I16 ? sizeof(int16_t) : sizeof(float));
    int32_t totalBytesNeeded = numFrames * frameSize;
    int32_t bytesWritten = 0;

    // Clear buffer first
    memset(audioData, 0, totalBytesNeeded);

    if (g_paused.load()) {
        return AAUDIO_CALLBACK_RESULT_CONTINUE;
    }

    std::lock_guard<std::mutex> lock(renderer->queueMutex);
    while (bytesWritten < totalBytesNeeded && !renderer->audioQueue.empty()) {
        auto& buffer = renderer->audioQueue.front();
        int32_t bytesToCopy = std::min(static_cast<int32_t>(buffer.size()),
                                       totalBytesNeeded - bytesWritten);

        memcpy(output + bytesWritten, buffer.data(), bytesToCopy);
        bytesWritten += bytesToCopy;
        if (bytesToCopy == buffer.size()) {
            renderer->audioQueue.pop();
        } else {
            buffer.erase(buffer.begin(), buffer.begin() + bytesToCopy);
        }
        renderer->queuedAudioBytes -= bytesToCopy;
    }

    // Apply volume
    if (renderer->volume != 1.0f) {
        if (format == AAUDIO_FORMAT_PCM_I16) {
            int16_t* samples = reinterpret_cast<int16_t*>(output);
            int32_t numSamples = bytesWritten / sizeof(int16_t);
            for (int i = 0; i < numSamples; i++) {
                samples[i] = static_cast<int16_t>(samples[i] * renderer->volume);
            }
        } else if (format == AAUDIO_FORMAT_PCM_FLOAT) {
            float* samples = reinterpret_cast<float*>(output);
            int32_t numSamples = bytesWritten / sizeof(float);
            for (int i = 0; i < numSamples; i++) {
                samples[i] = samples[i] * renderer->volume;
            }
        }
    }

    return AAUDIO_CALLBACK_RESULT_CONTINUE;
}

void AudioRenderer::pause() {
    if (!initialized || !playing.load()) return;

    aaudio_result_t result = AAudioStream_requestPause(stream);

    if (result != AAUDIO_OK) {
        LOGE("Failed to pause stream: %s", AAudio_convertResultToText(result));
        needsRecovery = true;
    } else {
        playing.store(false);
        LOGE("Audio successfully paused");
    }
}

void AudioRenderer::resume() {
    if (!initialized) {
        LOGE("Cannot resume - audio renderer not initialized");
        return;
    }

    aaudio_stream_state_t currentState = AAudioStream_getState(stream);
    LOGD("Current audio stream state before resume: %d", currentState);

    if (currentState == AAUDIO_STREAM_STATE_STARTED) {
        LOGD("Audio is already playing");
        playing.store(true);
        return;
    }

    if (currentState == AAUDIO_STREAM_STATE_DISCONNECTED) {
        LOGD("Audio stream disconnected, recreating...");
        cleanup();
        if (!init(sampleRate, channelCount, format)) {
            LOGE("Failed to recreate audio stream");
            return;
        }
    }

    aaudio_result_t result = AAudioStream_requestStart(stream);
    if (result != AAUDIO_OK) {
        LOGE("Failed to resume stream: %s", AAudio_convertResultToText(result));
        LOGE("Attempting stream recovery...");
        cleanup();
        if (init(sampleRate, channelCount, format)) {
            result = AAudioStream_requestStart(stream);
            if (result != AAUDIO_OK) {
                LOGE("Recovery failed: %s", AAudio_convertResultToText(result));
            } else {
                playing.store(true);
                LOGD("Audio recovery successful");
            }
        }
    } else {
        playing.store(true);
        LOGD("Audio successfully resumed");
    }
}

void AudioRenderer::setFirstAudioPts(int64_t pts) {
    firstAudioPts.store(pts);
}

int64_t AudioRenderer::getExactAudioClockUs() {
    if (!initialized || !stream || sampleRate <= 0) return 0;
    
    int64_t framesRead = AAudioStream_getFramesRead(stream);
    int64_t playedDurationUs = (framesRead * 1000000LL) / sampleRate;
    
    int64_t basePts = firstAudioPts.load();
    if (basePts == -1) basePts = 0;
    
    return basePts + playedDurationUs;
}

int64_t AudioRenderer::getPendingAudioDurationUs() {
    if (!initialized || !stream || sampleRate <= 0) return 0;
    
    // 计算 AAudio 底层硬件缓冲中的帧数
    int64_t framesWritten = AAudioStream_getFramesWritten(stream);
    int64_t framesRead = AAudioStream_getFramesRead(stream);
    int64_t hardwarePendingFrames = framesWritten - framesRead;
    if (hardwarePendingFrames < 0) hardwarePendingFrames = 0;
    
    // 计算硬件缓冲延迟微秒数
    int64_t hardwareDelayUs = (hardwarePendingFrames * 1000000LL) / sampleRate;

    // 计算我们手动队列的延迟微秒数
    int64_t queueDelayUs = 0;
    int32_t frameSize = channelCount * sizeof(int16_t); // FFmpeg 重采样强制输出 S16
    if (frameSize > 0) {
        int64_t queuedBytes = queuedAudioBytes.load();
        int64_t queueFrames = queuedBytes / frameSize;
        queueDelayUs = (queueFrames * 1000000LL) / sampleRate;
    }
    
    return hardwareDelayUs + queueDelayUs;
}
