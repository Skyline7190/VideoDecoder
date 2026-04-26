//
// Created by QiChen on 2026/4/26.
//

#include "NativeWindowHolder.h"

#include <android/log.h>
#include <android/native_window_jni.h>

#define LOG_TAG "NativeWindowHolder"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

void NativeWindowHolder::NativeWindowDeleter::operator()(ANativeWindow* window) const {
    if (window) {
        ANativeWindow_release(window);
    }
}

void NativeWindowHolder::setSurface(JNIEnv* env, jobject surface) {
    std::lock_guard<std::mutex> lock(mutex);
    window.reset();
    if (surface == nullptr) {
        return;
    }

    ANativeWindow* nativeWindow = ANativeWindow_fromSurface(env, surface);
    if (!nativeWindow) {
        LOGE("Failed to create NativeWindow from Surface");
        return;
    }
    window = WindowPtr(nativeWindow, NativeWindowDeleter());
}

NativeWindowHolder::WindowPtr NativeWindowHolder::snapshot() const {
    std::lock_guard<std::mutex> lock(mutex);
    return window;
}
