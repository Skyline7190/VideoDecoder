//
// Created by QiChen on 2026/4/26.
//

#include "NativeEgl.h"

#include <android/log.h>

#define LOG_TAG "NativeEgl"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

EGLDisplay initEGLDisplay() {
    EGLDisplay display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (display == EGL_NO_DISPLAY) {
        LOGE("eglGetDisplay failed");
        return EGL_NO_DISPLAY;
    }

    EGLint major, minor;
    if (eglInitialize(display, &major, &minor) != EGL_TRUE) {
        LOGE("eglInitialize failed");
        return EGL_NO_DISPLAY;
    }

    return display;
}

EGLSurface createEGLSurface(EGLDisplay display, ANativeWindow* window) {
    EGLint configAttribs[] = {
            EGL_SURFACE_TYPE, EGL_WINDOW_BIT,
            EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
            EGL_BLUE_SIZE, 8,
            EGL_GREEN_SIZE, 8,
            EGL_RED_SIZE, 8,
            EGL_ALPHA_SIZE, 8,
            EGL_DEPTH_SIZE, 16,
            EGL_NONE
    };

    EGLConfig config;
    EGLint numConfigs;
    if (eglChooseConfig(display, configAttribs, &config, 1, &numConfigs) != EGL_TRUE || numConfigs == 0) {
        LOGE("eglChooseConfig failed");
        return EGL_NO_SURFACE;
    }

    EGLSurface surface = eglCreateWindowSurface(display, config, window, nullptr);
    if (surface == EGL_NO_SURFACE) {
        LOGE("eglCreateWindowSurface failed");
        return EGL_NO_SURFACE;
    }

    return surface;
}

EGLContext createEGLContext(EGLDisplay display, EGLSurface surface) {
    EGLConfig config;
    EGLint numConfigs;
    EGLint configSpec[] = {
            EGL_SURFACE_TYPE, EGL_WINDOW_BIT,
            EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
            EGL_RED_SIZE, 8,
            EGL_GREEN_SIZE, 8,
            EGL_BLUE_SIZE, 8,
            EGL_ALPHA_SIZE, 8,
            EGL_DEPTH_SIZE, 24,
            EGL_STENCIL_SIZE, 8,
            EGL_NONE
    };

    if (eglChooseConfig(display, configSpec, nullptr, 0, &numConfigs) == EGL_FALSE) {
        LOGE("eglChooseConfig failed to get number of configs");
        return EGL_NO_CONTEXT;
    }

    EGLConfig* configs = new EGLConfig[numConfigs];
    if (eglChooseConfig(display, configSpec, configs, numConfigs, &numConfigs) == EGL_FALSE) {
        LOGE("eglChooseConfig failed to get configs");
        delete[] configs;
        return EGL_NO_CONTEXT;
    }

    config = configs[0];
    delete[] configs;

    EGLint contextAttribs[] = {
            EGL_CONTEXT_CLIENT_VERSION, 2,
            EGL_NONE
    };

    EGLContext context = eglCreateContext(display, config, EGL_NO_CONTEXT, contextAttribs);
    if (context == EGL_NO_CONTEXT) {
        LOGE("eglCreateContext failed");
        return EGL_NO_CONTEXT;
    }

    if (eglMakeCurrent(display, surface, surface, context) != EGL_TRUE) {
        LOGE("eglMakeCurrent failed");
        return EGL_NO_CONTEXT;
    }

    return context;
}

void cleanupEGL(EGLDisplay display, EGLSurface surface, EGLContext context) {
    eglMakeCurrent(display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    eglDestroyContext(display, context);
    eglDestroySurface(display, surface);
    eglTerminate(display);
}
