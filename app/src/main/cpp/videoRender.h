#ifndef VIDEORENDER_H
#define VIDEORENDER_H

#include <GLES2/gl2.h>
#include <mutex>
#include <atomic>

extern "C" {
#include <libavutil/frame.h>
}

class Renderer {
public:
    Renderer();
    ~Renderer();

    bool init(int width, int height);
    void renderFrame(AVFrame* frame);
    void cleanup();
    bool isInitialized() const;
    void reset();

private:
    bool createTextures();
    bool setupVertexData();
    void updateTextures(AVFrame* frame);
    bool loadShader(GLuint shader, const char* source);
    bool checkProgramLinkStatus(GLuint program);
    void checkGLError(const char* operation);

    GLuint program;
    GLuint textureY, textureU, textureV;
    GLuint vbo;
    GLint texYLoc, texULoc, texVLoc;
    GLint positionLoc, texCoordLoc;

    int width, height;
    std::mutex frameMutex;
    std::atomic<bool> contextLost{false};

    // 适配OpenGL ES 2.0的着色器
    static constexpr const char* vertexShaderSource =
            "attribute vec2 position;\n"
            "attribute vec2 texCoord;\n"
            "varying vec2 vTexCoord;\n"
            "void main() {\n"
            "    gl_Position = vec4(position, 0.0, 1.0);\n"
            "    vTexCoord = texCoord;\n"
            "}\n";

    static constexpr const char* fragmentShaderSource =
            "precision mediump float;\n"
            "varying vec2 vTexCoord;\n"
            "uniform sampler2D texY;\n"
            "uniform sampler2D texU;\n"
            "uniform sampler2D texV;\n"
            "void main() {\n"
            "    float y = texture2D(texY, vTexCoord).r;\n"
            "    float u = texture2D(texU, vTexCoord).r - 0.5;\n"
            "    float v = texture2D(texV, vTexCoord).r - 0.5;\n"
            "    float r = y + 1.402 * v;\n"
            "    float g = y - 0.344 * u - 0.714 * v;\n"
            "    float b = y + 1.772 * u;\n"
            "    gl_FragColor = vec4(r, g, b, 1.0);\n"
            "}\n";
};

#endif // VIDEORENDER_H