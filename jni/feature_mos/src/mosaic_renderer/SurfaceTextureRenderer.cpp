#include "SurfaceTextureRenderer.h"

#include <GLES2/gl2ext.h>

#include <android/log.h>
#define  LOG_TAG    "SurfaceTextureRenderer"
#define  LOGI(...)  __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)
#define  LOGE(...)  __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)

static const char gVertexShader[] =
"uniform mat4 uSTMatrix;\n"
"uniform mat4 u_scalingtrans;  \n"
"attribute vec4 aPosition;\n"
"attribute vec4 aTextureCoord;\n"
"varying vec2 vTextureCoord;\n"
"varying vec2 vTextureNormCoord;\n"
"void main() {\n"
"  gl_Position = u_scalingtrans * aPosition;\n"
"  vTextureCoord = (uSTMatrix * aTextureCoord).xy;\n"
"  vTextureNormCoord = aTextureCoord.xy;\n"
"}\n";

static const char gFragmentShader[] =
"#extension GL_OES_EGL_image_external : require\n"
"precision mediump float;\n"
"varying vec2 vTextureCoord;\n"
"varying vec2 vTextureNormCoord;\n"
"uniform samplerExternalOES sTexture;\n"
"void main() {\n"
"  gl_FragColor = texture2D(sTexture, vTextureNormCoord);\n"
"}\n";

const GLfloat g_vVertices[] = {
    -1.f, -1.f, 0.0f, 1.0f,  // Position 0
    0.0f,  1.0f,         // TexCoord 0
     1.f, -1.f, 0.0f, 1.0f, // Position 1
    1.0f,  1.0f,         // TexCoord 1
    -1.f,  1.f, 0.0f, 1.0f, // Position 2
    0.0f,  0.0f,         // TexCoord 2
    1.f,   1.f, 0.0f, 1.0f, // Position 3
    1.0f,  0.0f          // TexCoord 3
};
GLushort g_iIndices2[] = { 0, 1, 2, 3 };

const int GL_TEXTURE_EXTERNAL_OES_ENUM = 0x8D65;

const int VERTEX_STRIDE = 6 * sizeof(GLfloat);

SurfaceTextureRenderer::SurfaceTextureRenderer()
      : mGlProgram(0),
        mInputTextureName(-1),
        mInputTextureWidth(0),
        mInputTextureHeight(0),
        mSurfaceWidth(0),
        mSurfaceHeight(0)
{
    memset(mSTMatrix, 0.0, 16*sizeof(float));
    mSTMatrix[0] = 1.0f;
    mSTMatrix[5] = 1.0f;
    mSTMatrix[10] = 1.0f;
    mSTMatrix[15] = 1.0f;

    InitializeGLContext();
}

SurfaceTextureRenderer::~SurfaceTextureRenderer() {
}

void SurfaceTextureRenderer::SetSTMatrix(float *stmat)
{
    memcpy(mSTMatrix, stmat, 16*sizeof(float));
}

GLuint SurfaceTextureRenderer::loadShader(GLenum shaderType, const char* pSource) {
    GLuint shader = glCreateShader(shaderType);
    if (shader) {
        glShaderSource(shader, 1, &pSource, NULL);
        glCompileShader(shader);
        GLint compiled = 0;
        glGetShaderiv(shader, GL_COMPILE_STATUS, &compiled);
        if (!compiled) {
            GLint infoLen = 0;
            glGetShaderiv(shader, GL_INFO_LOG_LENGTH, &infoLen);
            if (infoLen) {
                char* buf = (char*) malloc(infoLen);
                if (buf) {
                    glGetShaderInfoLog(shader, infoLen, NULL, buf);
                    LOGE("Could not compile shader %d:\n%s\n",
                            shaderType, buf);
                    free(buf);
                }
                glDeleteShader(shader);
                shader = 0;
            }
        }
    }
    return shader;
}

GLuint SurfaceTextureRenderer::createProgram(const char* pVertexSource, const char* pFragmentSource)
{
    GLuint vertexShader = loadShader(GL_VERTEX_SHADER, pVertexSource);
    if (!vertexShader)
    {
        return 0;
    }
    LOGI("VertexShader Loaded!");

    GLuint pixelShader = loadShader(GL_FRAGMENT_SHADER, pFragmentSource);
    if (!pixelShader)
    {
        return 0;
    }
    LOGI("FragmentShader Loaded!");

    GLuint program = glCreateProgram();
    if (program)
    {
        glAttachShader(program, vertexShader);
        checkGlError("glAttachShader");
        glAttachShader(program, pixelShader);
        checkGlError("glAttachShader");

        LOGI("Shaders Attached!");

        glLinkProgram(program);
        GLint linkStatus = GL_FALSE;
        glGetProgramiv(program, GL_LINK_STATUS, &linkStatus);

        LOGI("Program Linked!");

        if (linkStatus != GL_TRUE)
        {
            GLint bufLength = 0;
            glGetProgramiv(program, GL_INFO_LOG_LENGTH, &bufLength);
            if (bufLength)
            {
                char* buf = (char*) malloc(bufLength);
                if (buf)
                {
                    glGetProgramInfoLog(program, bufLength, NULL, buf);
                    LOGE("Could not link program:\n%s\n", buf);
                    free(buf);
                }
            }
            glDeleteProgram(program);
            program = 0;
        }
    }
    return program;
}

bool SurfaceTextureRenderer::InitializeGLProgram()
{
    bool succeeded = false;
    do {
        GLuint glProgram;
        glProgram = createProgram(VertexShaderSource(),
                FragmentShaderSource());
        if (!glProgram) {
            break;
        }

        glUseProgram(glProgram);
        if (!checkGlError("glUseProgram")) break;

        maPositionHandle = glGetAttribLocation(glProgram, "aPosition");
        checkGlError("glGetAttribLocation aPosition");
        maTextureHandle = glGetAttribLocation(glProgram, "aTextureCoord");
        checkGlError("glGetAttribLocation aTextureCoord");
        muSTMatrixHandle = glGetUniformLocation(glProgram, "uSTMatrix");
        checkGlError("glGetUniformLocation uSTMatrix");
        mScalingtransLoc = glGetUniformLocation(glProgram, "u_scalingtrans");

        glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
        mGlProgram = glProgram;
        succeeded = true;
    } while (false);

    if (!succeeded && (mGlProgram != 0))
    {
        glDeleteProgram(mGlProgram);
        checkGlError("glDeleteProgram");
        mGlProgram = 0;
    }
    return succeeded;
}

void SurfaceTextureRenderer::SetViewportMatrix(int w, int h, int W, int H)
{
    for(int i=0; i<16; i++)
    {
        mViewportMatrix[i] = 0.0f;
    }

    mViewportMatrix[0] = float(w)/float(W);
    mViewportMatrix[5] = float(h)/float(H);
    mViewportMatrix[10] = 1.0f;
    mViewportMatrix[12] = -1.0f + float(w)/float(W);
    mViewportMatrix[13] = -1.0f + float(h)/float(H);
    mViewportMatrix[15] = 1.0f;
}

void SurfaceTextureRenderer::SetScalingMatrix(float xscale, float yscale)
{
    for(int i=0; i<16; i++)
    {
        mScalingMatrix[i] = 0.0f;
    }

    mScalingMatrix[0] = xscale;
    mScalingMatrix[5] = yscale;
    mScalingMatrix[10] = 1.0f;
    mScalingMatrix[15] = 1.0f;
}

// Set this renderer to use the default frame-buffer (screen) and
// set the viewport size to be the given width and height (pixels).
bool SurfaceTextureRenderer::SetupGraphics(int width, int height)
{
    bool succeeded = false;
    do {
        if (mGlProgram == 0)
        {
            if (!InitializeGLProgram())
            {
              break;
            }
        }
        glUseProgram(mGlProgram);
        if (!checkGlError("glUseProgram")) break;

        glBindFramebuffer(GL_FRAMEBUFFER, 0);

        mFrameBuffer = NULL;
        mSurfaceWidth = width;
        mSurfaceHeight = height;

        glViewport(0, 0, mSurfaceWidth, mSurfaceHeight);
        if (!checkGlError("glViewport")) break;
        succeeded = true;
    } while (false);

    return succeeded;
}


// Set this renderer to use the specified FBO and
// set the viewport size to be the width and height of this FBO.
bool SurfaceTextureRenderer::SetupGraphics(FrameBuffer* buffer)
{
    bool succeeded = false;
    do {
        if (mGlProgram == 0)
        {
            if (!InitializeGLProgram())
            {
              break;
            }
        }
        glUseProgram(mGlProgram);
        if (!checkGlError("glUseProgram")) break;

        glBindFramebuffer(GL_FRAMEBUFFER, buffer->GetFrameBufferName());

        mFrameBuffer = buffer;
        mSurfaceWidth = mFrameBuffer->GetWidth();
        mSurfaceHeight = mFrameBuffer->GetHeight();

        glViewport(0, 0, mSurfaceWidth, mSurfaceHeight);
        if (!checkGlError("glViewport")) break;
        succeeded = true;
    } while (false);

    return succeeded;
}

bool SurfaceTextureRenderer::Clear(float r, float g, float b, float a)
{
    bool succeeded = false;
    do {
        bool rt = (mFrameBuffer == NULL)?
                SetupGraphics(mSurfaceWidth, mSurfaceHeight) :
                SetupGraphics(mFrameBuffer);

        if(!rt)
            break;

        glClearColor(r, g, b, a);
        glClear(GL_COLOR_BUFFER_BIT);

        succeeded = true;
    } while (false);
    return succeeded;

}

bool SurfaceTextureRenderer::DrawTexture(GLfloat *affine)
{
    bool succeeded = false;
    do {
        bool rt = (mFrameBuffer == NULL)?
            SetupGraphics(mSurfaceWidth, mSurfaceHeight) :
            SetupGraphics(mFrameBuffer);

        if(!rt)
            break;

        glDisable(GL_BLEND);

        glActiveTexture(GL_TEXTURE0);
        if (!checkGlError("glActiveTexture")) break;

        const GLenum texture_type = InputTextureType();
        glBindTexture(texture_type, mInputTextureName);
        if (!checkGlError("glBindTexture")) break;

        glUniformMatrix4fv(mScalingtransLoc, 1, GL_FALSE, mScalingMatrix);

        // Load the vertex position
        glVertexAttribPointer(maPositionHandle, 4, GL_FLOAT,
                GL_FALSE, VERTEX_STRIDE, g_vVertices);
        glEnableVertexAttribArray(maPositionHandle);
        // Load the texture coordinate
        glVertexAttribPointer(maTextureHandle, 2, GL_FLOAT,
                GL_FALSE, VERTEX_STRIDE, &g_vVertices[4]);
        glEnableVertexAttribArray(maTextureHandle);

        // And, finally, execute the GL draw command.
        glDrawElements(GL_TRIANGLE_STRIP, 4, GL_UNSIGNED_SHORT, g_iIndices2);

        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glFinish();
        succeeded = true;
    } while (false);
    return succeeded;
}

void SurfaceTextureRenderer::InitializeGLContext()
{
    if(mFrameBuffer != NULL)
    {
        delete mFrameBuffer;
        mFrameBuffer = NULL;
    }

    mInputTextureName = -1;
    mInputTextureType = GL_TEXTURE_EXTERNAL_OES_ENUM;
    mGlProgram = 0;
}

int SurfaceTextureRenderer::GetTextureName()
{
    return mInputTextureName;
}

void SurfaceTextureRenderer::SetInputTextureName(GLuint textureName)
{
    mInputTextureName = textureName;
}

void SurfaceTextureRenderer::SetInputTextureType(GLenum textureType)
{
    mInputTextureType = textureType;
}

void SurfaceTextureRenderer::SetInputTextureDimensions(int width, int height)
{
    mInputTextureWidth = width;
    mInputTextureHeight = height;
}


const char* SurfaceTextureRenderer::VertexShaderSource() const
{
    return gVertexShader;
}

const char* SurfaceTextureRenderer::FragmentShaderSource() const
{
    return gFragmentShader;
}
