//
// Created by QiChen on 2026/4/26.
//

#ifndef VIDEODECODER_NATIVEEGL_H
#define VIDEODECODER_NATIVEEGL_H

#include <EGL/egl.h>
#include <android/native_window.h>

EGLDisplay initEGLDisplay();
EGLSurface createEGLSurface(EGLDisplay display, ANativeWindow* window);
EGLContext createEGLContext(EGLDisplay display, EGLSurface surface);
void cleanupEGL(EGLDisplay display, EGLSurface surface, EGLContext context);

#endif //VIDEODECODER_NATIVEEGL_H
