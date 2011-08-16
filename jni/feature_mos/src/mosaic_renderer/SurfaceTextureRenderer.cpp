#include "SurfaceTextureRenderer.h"

#include <GLES2/gl2ext.h>

#include <android/log.h>
#define  LOG_TAG    "SurfaceTextureRenderer"
#define  LOGI(...)  __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)
#define  LOGE(...)  __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)

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

SurfaceTextureRenderer::SurfaceTextureRenderer() : Renderer() {
    memset(mSTMatrix, 0.0, 16*sizeof(float));
    mSTMatrix[0] = 1.0f;
    mSTMatrix[5] = 1.0f;
    mSTMatrix[10] = 1.0f;
    mSTMatrix[15] = 1.0f;
}

SurfaceTextureRenderer::~SurfaceTextureRenderer() {
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

void SurfaceTextureRenderer::SetSTMatrix(float *stmat)
{
    memcpy(mSTMatrix, stmat, 16*sizeof(float));
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

const char* SurfaceTextureRenderer::VertexShaderSource() const
{
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

    return gVertexShader;
}

const char* SurfaceTextureRenderer::FragmentShaderSource() const
{
    static const char gFragmentShader[] =
        "#extension GL_OES_EGL_image_external : require\n"
        "precision mediump float;\n"
        "varying vec2 vTextureCoord;\n"
        "varying vec2 vTextureNormCoord;\n"
        "uniform samplerExternalOES sTexture;\n"
        "void main() {\n"
        "  gl_FragColor = texture2D(sTexture, vTextureNormCoord);\n"
        "}\n";

    return gFragmentShader;
}
