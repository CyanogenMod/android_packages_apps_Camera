#include "WarpRenderer.h"

#include <GLES2/gl2ext.h>

#include <android/log.h>
#define  LOG_TAG    "WarpRenderer"
#define  LOGI(...)  __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)
#define  LOGE(...)  __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)


static const char gVertexShader[] =
"uniform mat4 u_affinetrans;  \n"
"uniform mat4 u_viewporttrans;  \n"
"uniform mat4 u_scalingtrans;  \n"
"attribute vec4 a_position;   \n"
"attribute vec2 a_texCoord;   \n"
"varying vec2 v_texCoord;     \n"
"void main()                  \n"
"{                            \n"
"   gl_Position = u_scalingtrans * u_viewporttrans * u_affinetrans * a_position; \n"
"   v_texCoord = a_texCoord;  \n"
"}                            \n";

static const char gFragmentShader[] =
"precision mediump float;                            \n"
"varying vec2 v_texCoord;                            \n"
"uniform sampler2D s_texture;                        \n"
"void main()                                         \n"
"{                                                   \n"
"  vec4 color;                                       \n"
"  color = texture2D(s_texture, v_texCoord);       \n"
"  gl_FragColor = color;                             \n"
"}                                                   \n";


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

const int VERTEX_STRIDE = 6 * sizeof(GLfloat);

GLushort g_iIndices[] = { 0, 1, 2, 3 };

WarpRenderer::WarpRenderer()
      : mGlProgram(0),
        mInputTextureName(-1),
        mInputTextureWidth(0),
        mInputTextureHeight(0),
        mSurfaceWidth(0),
        mSurfaceHeight(0)
                    {
    InitializeGLContext();
}

WarpRenderer::~WarpRenderer() {
}

GLuint WarpRenderer::loadShader(GLenum shaderType, const char* pSource) {
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

GLuint WarpRenderer::createProgram(const char* pVertexSource, const char* pFragmentSource)
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

bool WarpRenderer::InitializeGLProgram()
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

        // Get attribute locations
        mPositionLoc     = glGetAttribLocation(glProgram, "a_position");
        mAffinetransLoc  = glGetUniformLocation(glProgram, "u_affinetrans");
        mViewporttransLoc = glGetUniformLocation(glProgram, "u_viewporttrans");
        mScalingtransLoc = glGetUniformLocation(glProgram, "u_scalingtrans");
        mTexCoordLoc     = glGetAttribLocation(glProgram, "a_texCoord");

        // Get sampler location
        mSamplerLoc      = glGetUniformLocation(glProgram, "s_texture");

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

void WarpRenderer::SetViewportMatrix(int w, int h, int W, int H)
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

void WarpRenderer::SetScalingMatrix(float xscale, float yscale)
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
bool WarpRenderer::SetupGraphics(int width, int height)
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
bool WarpRenderer::SetupGraphics(FrameBuffer* buffer)
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

bool WarpRenderer::Clear(float r, float g, float b, float a)
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

bool WarpRenderer::DrawTexture(GLfloat *affine)
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

        // Set the sampler texture unit to 0
        glUniform1i(mSamplerLoc, 0);

        // Load the vertex position
        glVertexAttribPointer(mPositionLoc, 4, GL_FLOAT,
                GL_FALSE, VERTEX_STRIDE, g_vVertices);

        // Load the texture coordinate
        glVertexAttribPointer(mTexCoordLoc, 2, GL_FLOAT,
                GL_FALSE, VERTEX_STRIDE, &g_vVertices[4]);

        glEnableVertexAttribArray(mPositionLoc);
        glEnableVertexAttribArray(mTexCoordLoc);

        // pass matrix information to the vertex shader
        glUniformMatrix4fv(mAffinetransLoc, 1, GL_FALSE, affine);
        glUniformMatrix4fv(mViewporttransLoc, 1, GL_FALSE, mViewportMatrix);
        glUniformMatrix4fv(mScalingtransLoc, 1, GL_FALSE, mScalingMatrix);

        // And, finally, execute the GL draw command.
        glDrawElements(GL_TRIANGLE_STRIP, 4, GL_UNSIGNED_SHORT, g_iIndices);

        checkGlError("glDrawElements");

        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        succeeded = true;
    } while (false);
    return succeeded;
}

void WarpRenderer::InitializeGLContext()
{
    if(mFrameBuffer != NULL)
    {
        delete mFrameBuffer;
        mFrameBuffer = NULL;
    }

    mInputTextureName = -1;
    mGlProgram = 0;
    mTexHandle = 0;
}

int WarpRenderer::GetTextureName()
{
    return mInputTextureName;
}

void WarpRenderer::SetInputTextureName(GLuint textureName)
{
    mInputTextureName = textureName;
}

void WarpRenderer::SetInputTextureDimensions(int width, int height)
{
    mInputTextureWidth = width;
    mInputTextureHeight = height;
}


const char* WarpRenderer::VertexShaderSource() const
{
    return gVertexShader;
}

const char* WarpRenderer::FragmentShaderSource() const
{
    return gFragmentShader;
}
