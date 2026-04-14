//
// Created by QiChen on 2025/3/30.
//

#ifndef VIDEODECODER_QUEUE_H
#define VIDEODECODER_QUEUE_H

#include "android/log.h"
#define LOGD(...) ((void)__android_log_print(ANDROID_LOG_DEBUG, "VideoDecoder", __VA_ARGS__))

#include <queue>
#include <mutex>
#include <condition_variable>
#include <atomic>
#include <thread>

extern "C" {
#include <libavcodec/avcodec.h>
#include <libavutil/frame.h>
}

// 数据包队列 (PacketQueue)
class PacketQueue {
public:
    void push(AVPacket* packet);
    AVPacket* pop();
    bool isDemuxFinished();
    void setDemuxFinished(bool finished);
    void notifyAll();
    size_t size() {
        std::lock_guard<std::mutex> lock(mutex);
        return queue.size();
    }
    void clear() {
        std::lock_guard<std::mutex> lock(mutex);
        while (!queue.empty()) {
            AVPacket* pkt = queue.front();
            av_packet_free(&pkt);
            queue.pop();
        }
    }

private:
    std::queue<AVPacket*> queue;
    std::mutex mutex;
    std::condition_variable cond;
    std::atomic<bool> demuxFinished{false};
};

//-----更新分割线-----
/**
 * 用于在解码线程和渲染线程之间传递解码后的 AVFrame* 对象。
 */
class FrameQueue {
public:
    FrameQueue() = default;
    ~FrameQueue() { clear(); }

    // 阻塞式弹出帧（无限等待）
    AVFrame* pop() {
        std::unique_lock<std::mutex> lock(mutex_);
        cond_.wait(lock, [this]() {
            return !queue_.empty() || terminated_;
        });

        if (terminated_ && queue_.empty()) {
            return nullptr; // 终止且队列空
        }

        AVFrame* frame = queue_.front();
        queue_.pop();
        cond_.notify_all(); // 通知可能阻塞在 push 的解码线程
        return frame;
    }

    // 安全压入帧（带大小限制）
    bool push(AVFrame* frame, size_t max_size = 10) { // 减小队列大小限制
        if (!frame) return false;

        std::unique_lock<std::mutex> lock(mutex_);

        // 核心修复：如果队列满了，必须阻塞等待！否则解码器会光速解完并丢弃所有帧！
        cond_.wait(lock, [this, max_size]() {
            return queue_.size() < max_size || terminated_;
        });

        if (terminated_) {
            av_frame_free(&frame);
            return false;
        }

        queue_.push(frame);
        cond_.notify_all();
        return true;
    }

    // 清空队列并释放资源
    void clear() {
        std::unique_lock<std::mutex> lock(mutex_);
        while (!queue_.empty()) {
            av_frame_free(&queue_.front());
            queue_.pop();
        }
    }
    bool empty() const {
        std::unique_lock<std::mutex> lock(mutex_);
        return queue_.empty();
    }
    // 获取当前队列大小
    size_t size() const {
        std::unique_lock<std::mutex> lock(mutex_);
        return queue_.size();
    }

    // 标记队列终止（用于优雅退出）
    void terminate() {
        std::unique_lock<std::mutex> lock(mutex_);
        terminated_ = true;
        cond_.notify_all();
    }

private:
    mutable std::mutex mutex_;
    std::condition_variable cond_;
    std::queue<AVFrame*> queue_;
    std::atomic<bool> terminated_{false};
};
#endif //VIDEODECODER_QUEUE_H