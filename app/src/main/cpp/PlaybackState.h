//
// Created by QiChen on 2026/4/26.
//

#ifndef VIDEODECODER_PLAYBACKSTATE_H
#define VIDEODECODER_PLAYBACKSTATE_H

#include <atomic>
#include <condition_variable>
#include <cstdint>
#include <mutex>

struct PlaybackState {
    std::atomic<bool> isSeeking{false};
    std::atomic<bool> seekApplied{false};
    std::atomic<int64_t> seekPositionMs{0};
    std::atomic<bool> paused{false};
    std::atomic<bool> stopRequested{false};
    std::atomic<bool> decodingFinished{false};
    std::atomic<bool> audioDecodingFinished{false};
    std::atomic<int64_t> audioClock{0};
    std::atomic<int64_t> videoClock{0};
    std::atomic<int64_t> durationMs{0};
    std::atomic<int64_t> currentPositionMs{0};
    std::atomic<float> playbackSpeed{1.0f};

    std::mutex seekMutex;
    std::condition_variable seekCv;

    void resetForNewPlayback() {
        isSeeking.store(false);
        seekApplied.store(false);
        seekPositionMs.store(0);
        paused.store(false);
        stopRequested.store(false);
        decodingFinished.store(false);
        audioDecodingFinished.store(false);
        audioClock.store(0);
        videoClock.store(0);
        durationMs.store(0);
        currentPositionMs.store(0);
        playbackSpeed.store(1.0f);
    }

    void notifySeekApplied() {
        std::lock_guard<std::mutex> lock(seekMutex);
        seekCv.notify_all();
    }

    bool waitForSeekApplied(int timeoutMs = 1000) {
        std::unique_lock<std::mutex> lock(seekMutex);
        return seekCv.wait_for(lock, std::chrono::milliseconds(timeoutMs), [this] {
            return seekApplied.load() || stopRequested.load();
        });
    }
};

#endif //VIDEODECODER_PLAYBACKSTATE_H
