#include "videoRender.h"
#include <android/log.h>
#include <chrono>

#define LOG_TAG "VideoRender"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

Renderer::Renderer() : program(0), textureY(0), textureU(0), textureV(0),
                       vbo(0), texYLoc(-1), texULoc(-1), texVLoc(-1),
                       positionLoc(-1), texCoordLoc(-1), width(0), height(0) {}

Renderer::~Renderer() {
    cleanup();
}

bool Renderer::init(int w, int h) {
    std::lock_guard<std::mutex> lock(frameMutex);
    glViewport(0, 0, w, h);
    width = w;
    height = h;
    contextLost = false;

    // 创建和编译着色器
    GLuint vertexShader = glCreateShader(GL_VERTEX_SHADER);
    if (!loadShader(vertexShader, vertexShaderSource)) {
        glDeleteShader(vertexShader);
        return false;
    }

    GLuint fragmentShader = glCreateShader(GL_FRAGMENT_SHADER);
    if (!loadShader(fragmentShader, fragmentShaderSource)) {
        glDeleteShader(vertexShader);
        glDeleteShader(fragmentShader);
        return false;
    }

    // 创建和链接程序
    program = glCreateProgram();
    glAttachShader(program, vertexShader);
    glAttachShader(program, fragmentShader);

    // 绑定属性位置
    glBindAttribLocation(program, 0, "position");
    glBindAttribLocation(program, 1, "texCoord");

    glLinkProgram(program);
    if (!checkProgramLinkStatus(program)) {
        glDeleteShader(vertexShader);
        glDeleteShader(fragmentShader);
        glDeleteProgram(program);
        program = 0;
        return false;
    }

    // 获取Uniform和Attribute位置
    texYLoc = glGetUniformLocation(program, "texY");
    texULoc = glGetUniformLocation(program, "texU");
    texVLoc = glGetUniformLocation(program, "texV");
    positionLoc = glGetAttribLocation(program, "position");
    texCoordLoc = glGetAttribLocation(program, "texCoord");

    if (texYLoc == -1 || texULoc == -1 || texVLoc == -1 ||
        positionLoc == -1 || texCoordLoc == -1) {
        LOGE("Failed to get uniform/attribute locations");
        return false;
    }

    // 清理着色器对象
    glDeleteShader(vertexShader);
    glDeleteShader(fragmentShader);

    // 创建纹理
    if (!createTextures()) {
        cleanup();
        return false;
    }

    // 设置顶点数据
    if (!setupVertexData()) {
        cleanup();
        return false;
    }

    LOGD("Renderer initialized successfully for size %dx%d", width, height);
    return true;
}

bool Renderer::createTextures() {
    glGenTextures(1, &textureY);
    glGenTextures(1, &textureU);
    glGenTextures(1, &textureV);

    if (!textureY || !textureU || !textureV) {
        LOGE("Failed to generate textures");
        return false;
    }

    // 设置纹理参数
// 设置纹理参数并分配内存（Y 分量）
    glBindTexture(GL_TEXTURE_2D, textureY);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_LUMINANCE,
                 width, height, 0,
                 GL_LUMINANCE, GL_UNSIGNED_BYTE, nullptr);

    // U 分量（假定 AVFrame 的 U 分量宽高是原始的一半）
    glBindTexture(GL_TEXTURE_2D, textureU);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_LUMINANCE,
                 width / 2, height / 2, 0,
                 GL_LUMINANCE, GL_UNSIGNED_BYTE, nullptr);

    // V 分量
    glBindTexture(GL_TEXTURE_2D, textureV);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_LUMINANCE,
                 width / 2, height / 2, 0,
                 GL_LUMINANCE, GL_UNSIGNED_BYTE, nullptr);

    // 解绑纹理
    glBindTexture(GL_TEXTURE_2D, 0);

    return true;
}

bool Renderer::setupVertexData() {
    float vertices[] = {
            -1.0f,  1.0f,  0.0f, 0.0f,  // 左上
            -1.0f, -1.0f,  0.0f, 1.0f,  // 左下
            1.0f, -1.0f,  1.0f, 1.0f,  // 右下
            1.0f,  1.0f,  1.0f, 0.0f   // 右上
    };

    glGenBuffers(1, &vbo);
    glBindBuffer(GL_ARRAY_BUFFER, vbo);
    glBufferData(GL_ARRAY_BUFFER, sizeof(vertices), vertices, GL_STATIC_DRAW);
    glBindBuffer(GL_ARRAY_BUFFER, 0);

    checkGLError("setupVertexData");
    return vbo != 0;
}

void Renderer::updateTextures(AVFrame* frame) {
    if (!frame || !frame->data[0] || !frame->data[1] || !frame->data[2]) {
        LOGW("Invalid frame data");
        return;
    }

    auto updatePlane = [](GLuint tex, int width, int height, void* data) {
        glBindTexture(GL_TEXTURE_2D, tex);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_LUMINANCE,
                     width, height, 0,
                     GL_LUMINANCE, GL_UNSIGNED_BYTE, data);
    };

    updatePlane(textureY, frame->width, frame->height, frame->data[0]);
    updatePlane(textureU, frame->width/2, frame->height/2, frame->data[1]);
    updatePlane(textureV, frame->width/2, frame->height/2, frame->data[2]);

    checkGLError("updateTextures");
}

void Renderer::renderFrame(AVFrame* frame) {
    if (contextLost) {
        LOGW("Context lost, skipping render");
        return;
    }

    std::lock_guard<std::mutex> lock(frameMutex);

    if (!frame || !frame->data[0] || !isInitialized()) {
        LOGW("Invalid frame or renderer not initialized");
        return;
    }
    //LOGD("Rendering frame: %dx%d", frame->width, frame->height);
    // 清除屏幕
    glClearColor(1.0, 0.0, 0.0, 1.0);
    glClear(GL_COLOR_BUFFER_BIT);

    // 使用程序
    glUseProgram(program);

    // 更新纹理
    updateTextures(frame);

    // 绑定纹理
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, textureY);
    glUniform1i(texYLoc, 0);

    glActiveTexture(GL_TEXTURE1);
    glBindTexture(GL_TEXTURE_2D, textureU);
    glUniform1i(texULoc, 1);

    glActiveTexture(GL_TEXTURE2);
    glBindTexture(GL_TEXTURE_2D, textureV);
    glUniform1i(texVLoc, 2);

    // 设置顶点数据
    glBindBuffer(GL_ARRAY_BUFFER, vbo);

    glEnableVertexAttribArray(positionLoc);
    glVertexAttribPointer(positionLoc, 2, GL_FLOAT, GL_FALSE,
                          4 * sizeof(float), (void*)0);

    glEnableVertexAttribArray(texCoordLoc);
    glVertexAttribPointer(texCoordLoc, 2, GL_FLOAT, GL_FALSE,
                          4 * sizeof(float), (void*)(2 * sizeof(float)));

    // 绘制
    glDrawArrays(GL_TRIANGLE_FAN, 0, 4);

    // 重置状态
    glDisableVertexAttribArray(positionLoc);
    glDisableVertexAttribArray(texCoordLoc);
    glBindBuffer(GL_ARRAY_BUFFER, 0);
    glBindTexture(GL_TEXTURE_2D, 0);
    glUseProgram(0);

    // 强制刷新
    glFlush();

    checkGLError("renderFrame");
    if (!frame || !frame->data[0] || !frame->data[1] || !frame->data[2]) {
        LOGE("Invalid frame data");
        return;
    }
}

void Renderer::cleanup() {
    std::lock_guard<std::mutex> lock(frameMutex);

    if (program) glDeleteProgram(program);
    if (textureY) glDeleteTextures(1, &textureY);
    if (textureU) glDeleteTextures(1, &textureU);
    if (textureV) glDeleteTextures(1, &textureV);
    if (vbo) glDeleteBuffers(1, &vbo);

    program = textureY = textureU = textureV = vbo = 0;
    texYLoc = texULoc = texVLoc = positionLoc = texCoordLoc = -1;
    width = height = 0;

    LOGD("Renderer resources cleaned up");
}

void Renderer::reset() {
    cleanup();
    contextLost = true;
}

bool Renderer::isInitialized() const {
    return program != 0 && textureY != 0 && textureU != 0 &&
           textureV != 0 && vbo != 0 && !contextLost;
}

bool Renderer::loadShader(GLuint shader, const char* source) {
    glShaderSource(shader, 1, &source, NULL);
    glCompileShader(shader);

    GLint success;
    glGetShaderiv(shader, GL_COMPILE_STATUS, &success);
    if (!success) {
        GLchar infoLog[512];
        glGetShaderInfoLog(shader, 512, NULL, infoLog);
        LOGE("Shader compilation failed:\n%s", infoLog);
        return false;
    }
    return true;
}

bool Renderer::checkProgramLinkStatus(GLuint program) {
    GLint success;
    glGetProgramiv(program, GL_LINK_STATUS, &success);
    if (!success) {
        GLchar infoLog[512];
        glGetProgramInfoLog(program, 512, NULL, infoLog);
        LOGE("Program linking failed:\n%s", infoLog);
        return false;
    }
    return true;
}

void Renderer::checkGLError(const char* operation) {
    for (GLint error = glGetError(); error; error = glGetError()) {
        LOGE("After %s() glError (0x%x)", operation, error);
    }
}