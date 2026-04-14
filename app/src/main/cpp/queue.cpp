//
// Created by QiChen on 2025/3/30.
//

#include "queue.h"
#include <chrono>

extern std::atomic<bool> g_stopRequested;

void PacketQueue::push(AVPacket* packet) {
    if (!packet) return;
    std::unique_lock<std::mutex> lock(mutex);
    // 限制队列大小，防止 Demuxer 光速读取导致 OOM 爆内存
    cond.wait(lock, [this]() {
        return queue.size() < 100 || demuxFinished.load() || g_stopRequested.load();
    });
    if (g_stopRequested.load()) {
        av_packet_free(&packet);
        return;
    }
    queue.push(packet);
    cond.notify_all();
}

AVPacket* PacketQueue::pop() {
    std::unique_lock<std::mutex> lock(mutex);
    cond.wait_for(lock, std::chrono::milliseconds(10), [this]() {
        return !queue.empty() || demuxFinished.load() || g_stopRequested.load();
    });
    if (queue.empty()) return nullptr;
    AVPacket* pkt = queue.front();
    queue.pop();
    cond.notify_all();
    return pkt;
}

void PacketQueue::notifyAll() {
    cond.notify_all();
}

bool PacketQueue::isDemuxFinished() {
    return demuxFinished.load();
}

void PacketQueue::setDemuxFinished(bool finished) {
    demuxFinished = finished;
    cond.notify_all();
}
