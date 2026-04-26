//
// Created by QiChen on 2026/4/26.
//

#ifndef VIDEODECODER_JNISTRINGCHARS_H
#define VIDEODECODER_JNISTRINGCHARS_H

#include <jni.h>

class JniStringChars {
public:
    JniStringChars(JNIEnv* env, jstring value) : env_(env), value_(value) {
        if (value_) {
            chars_ = env_->GetStringUTFChars(value_, nullptr);
        }
    }

    ~JniStringChars() {
        if (chars_) {
            env_->ReleaseStringUTFChars(value_, chars_);
        }
    }

    JniStringChars(const JniStringChars&) = delete;
    JniStringChars& operator=(const JniStringChars&) = delete;

    bool valid() const {
        return value_ == nullptr || chars_ != nullptr;
    }

    bool hasValue() const {
        return chars_ != nullptr;
    }

    const char* c_str() const {
        return chars_;
    }

private:
    JNIEnv* env_ = nullptr;
    jstring value_ = nullptr;
    const char* chars_ = nullptr;
};

#endif //VIDEODECODER_JNISTRINGCHARS_H
