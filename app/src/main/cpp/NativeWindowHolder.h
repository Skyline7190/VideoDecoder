//
// Created by QiChen on 2026/4/26.
//

#ifndef VIDEODECODER_NATIVEWINDOWHOLDER_H
#define VIDEODECODER_NATIVEWINDOWHOLDER_H

#include <jni.h>
#include <android/native_window.h>
#include <memory>
#include <mutex>

class NativeWindowHolder {
public:
    using WindowPtr = std::shared_ptr<ANativeWindow>;

    void setSurface(JNIEnv* env, jobject surface);
    WindowPtr snapshot() const;

private:
    struct NativeWindowDeleter {
        void operator()(ANativeWindow* window) const;
    };

    mutable std::mutex mutex;
    WindowPtr window;
};

#endif //VIDEODECODER_NATIVEWINDOWHOLDER_H
