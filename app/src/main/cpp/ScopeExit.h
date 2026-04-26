//
// Created by QiChen on 2026/4/26.
//

#ifndef VIDEODECODER_SCOPEEXIT_H
#define VIDEODECODER_SCOPEEXIT_H

#include <functional>
#include <utility>

class ScopeExit {
public:
    explicit ScopeExit(std::function<void()> cleanup) : cleanup_(std::move(cleanup)) {}
    ~ScopeExit() {
        cleanup_();
    }

    ScopeExit(const ScopeExit&) = delete;
    ScopeExit& operator=(const ScopeExit&) = delete;

private:
    std::function<void()> cleanup_;
};

#endif //VIDEODECODER_SCOPEEXIT_H
