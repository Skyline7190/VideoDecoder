//
// Created by QiChen on 2025/3/31.
//

#ifndef VIDEODECODER_AUDIORENDER_H
#define VIDEODECODER_AUDIORENDER_H


#include "PlaybackState.h"
#include <aaudio/AAudio.h>
#include <mutex>
#include <queue>
#include <vector>
#include <atomic>
#include <memory>

class AudioRenderer {
public:
    AudioRenderer();
    ~AudioRenderer();

    bool init(int sampleRate, int channels, int format);
    void start();
    void stop();
    void cleanup();
    void writeData(const uint8_t* data, int32_t numFrames);
    void setVolume(float volume);
    bool isInitialized() const { return initialized; }
    void pause();
    void resume();
    bool isPlaying() const { return playing; }
    void setSyncThreshold(int64_t thresholdMs); // 设置同步阈值（毫秒）
    void setAudioStartTime(int64_t startTimeMs); // 设置音频开始时间
    int64_t getPendingAudioDurationUs(); // 获取当前音频积压的缓冲时长（微秒）
    void setPlaybackState(const std::shared_ptr<PlaybackState>& state);
    
    void setFirstAudioPts(int64_t pts); // 记录第一帧音频的PTS
    int64_t getExactAudioClockUs(); // 获取绝对精准的硬件播放时钟

private:
    struct AudioChunk {
        std::vector<uint8_t> data;
        size_t offset = 0;
    };

    std::atomic<int64_t> firstAudioPts{-1}; // 第一帧音频的时间戳
    std::atomic<int64_t> queuedAudioBytes{0}; // 记录尚未播放的音频字节数
    std::atomic<int64_t> syncThresholdMs{0}; // 同步阈值（毫秒）
    std::atomic<int64_t> audioStartTimeMs{0}; // 音频开始时间
    std::atomic<bool> isAudioStarted{false}; // 音频是否已开始播放
    AAudioStream* stream = nullptr;
    std::mutex queueMutex;
    std::queue<AudioChunk> audioQueue;
    std::atomic<bool> initialized{false};
    std::atomic<float> volume{1.0f};
    std::atomic<bool> playing{false};
    int sampleRate = 0;
    int channelCount = 0;
    int format = AAUDIO_FORMAT_UNSPECIFIED;
    std::atomic<bool> needsRecovery{false};
    std::weak_ptr<PlaybackState> playbackState;

    static aaudio_data_callback_result_t dataCallback(
            AAudioStream* stream,
            void* userData,
            void* audioData,
            int32_t numFrames);
};


#endif //VIDEODECODER_AUDIORENDER_H
