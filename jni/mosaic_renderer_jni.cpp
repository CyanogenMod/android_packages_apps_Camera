// OpenGL ES 2.0 code
#include <jni.h>
#include <android/log.h>

#include <db_utilities_camera.h>
#include "mosaic/ImageUtils.h"
#include "mosaic_renderer/FrameBuffer.h"
#include "mosaic_renderer/WarpRenderer.h"
#include "mosaic_renderer/SurfaceTextureRenderer.h"
#include <stdio.h>
#include <stdlib.h>
#include <math.h>

#include "mosaic_renderer_jni.h"

#define  LOG_TAG    "MosaicRenderer"
#define  LOGI(...)  __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)
#define  LOGV(...)  __android_log_print(ANDROID_LOG_VERBOSE,LOG_TAG,__VA_ARGS__)
#define  LOGE(...)  __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)

// Texture handle
GLuint gSurfaceTextureID[1];

bool gWarpImage = true;

// Low-Res input image frame in RGB format for preview rendering and processing
// and high-res RGB input image for processing.
unsigned char* gPreviewImageRGB[NR];
// Low-Res & high-res preview image width
int gPreviewImageRGBWidth[NR];
// Low-Res & high-res preview image height
int gPreviewImageRGBHeight[NR];

// Semaphore to protect simultaneous read/writes from gPreviewImageRGB
sem_t gPreviewImageRGB_semaphore;
sem_t gPreviewImageReady_semaphore;

// Off-screen preview FBO width (large enough to store the entire
// preview mosaic).
int gPreviewFBOWidth;
// Off-screen preview FBO height (large enough to store the entire
// preview mosaic).
int gPreviewFBOHeight;

// Shader to copy input SurfaceTexture into and RGBA FBO. The two shaders
// render to the textures with dimensions corresponding to the low-res and
// high-res image frames.
SurfaceTextureRenderer gSurfTexRenderer[NR];
// Off-screen FBOs to store the low-res and high-res RGBA copied out from
// the SurfaceTexture by the gSurfTexRenderers.
FrameBuffer gBufferInput[NR];
// Shader to add warped current frame to the preview FBO
WarpRenderer gWarper;
// Shader to warp and render the preview FBO to the screen
WarpRenderer gPreview;
// Off-screen FBO to store the result of gWarper
FrameBuffer gBuffer;

// Affine transformation in GL 4x4 format (column-major) to warp the
// current frame into the first frame coordinate system.
GLfloat g_dAffinetransGL[16];

// Affine transformation in GL 4x4 format (column-major) to warp the
// preview FBO into the current frame coordinate system.
GLfloat g_dAffinetransInvGL[16];

// GL 4x4 Identity transformation
GLfloat g_dAffinetransIdent[] = {
    1., 0., 0., 0.,
    0., 1., 0., 0.,
    0., 0., 1., 0.,
    0., 0., 0., 1.};

const int GL_TEXTURE_EXTERNAL_OES_ENUM = 0x8D65;

static void printGLString(const char *name, GLenum s) {
    const char *v = (const char *) glGetString(s);
    LOGI("GL %s = %s\n", name, v);
}

// @return false if there was an error
bool checkGlError(const char* op) {
    GLint error = glGetError();
    if (error != 0) {
        LOGE("after %s() glError (0x%x)\n", op, error);
        return false;
    }
    return true;
}

void bindSurfaceTexture(GLuint texId)
{
    glBindTexture(GL_TEXTURE_EXTERNAL_OES_ENUM, texId);

    // Can't do mipmapping with camera source
    glTexParameterf(GL_TEXTURE_EXTERNAL_OES_ENUM, GL_TEXTURE_MIN_FILTER,
            GL_LINEAR);
    glTexParameterf(GL_TEXTURE_EXTERNAL_OES_ENUM, GL_TEXTURE_MAG_FILTER,
            GL_LINEAR);
    // Clamp to edge is the only option
    glTexParameteri(GL_TEXTURE_EXTERNAL_OES_ENUM, GL_TEXTURE_WRAP_S,
            GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_EXTERNAL_OES_ENUM, GL_TEXTURE_WRAP_T,
            GL_CLAMP_TO_EDGE);
}

void ClearPreviewImageRGB(int mID)
{
    unsigned char* ptr = gPreviewImageRGB[mID];
    for(int j = 0, i = 0;
            j < gPreviewImageRGBWidth[mID] * gPreviewImageRGBHeight[mID] * 4;
            j += 4)
    {
            ptr[i++] = 0;
            ptr[i++] = 0;
            ptr[i++] = 0;
            ptr[i++] = 255;
    }

}

void ConvertAffine3x3toGL4x4(double *matGL44, double *mat33)
{
    matGL44[0] = mat33[0];
    matGL44[1] = mat33[3];
    matGL44[2] = 0.0;
    matGL44[3] = mat33[6];

    matGL44[4] = mat33[1];
    matGL44[5] = mat33[4];
    matGL44[6] = 0.0;
    matGL44[7] = mat33[7];

    matGL44[8] = 0;
    matGL44[9] = 0;
    matGL44[10] = 1.0;
    matGL44[11] = 0.0;

    matGL44[12] = mat33[2];
    matGL44[13] = mat33[5];
    matGL44[14] = 0.0;
    matGL44[15] = mat33[8];
}

// This function computes fills the 4x4 matrices g_dAffinetrans and
// g_dAffinetransInv using the specified 3x3 affine transformation
// between the first captured frame and the current frame. The computed
// g_dAffinetrans is such that it warps the current frame into the
// coordinate system of the first frame. Thus, applying this transformation
// to each successive frame builds up the preview mosaic in the first frame
// coordinate system. Then the computed g_dAffinetransInv is such that it
// warps the computed preview mosaic into the coordinate system of the
// original (as captured) current frame. This has the effect of showing
// the current frame as is (without warping) but warping the rest of the
// mosaic data to still be in alignment with this frame.
void UpdateWarpTransformation(float *trs)
{
    double H[9], Hinv[9], Hp[9], Htemp[9];
    double K[9], Kinv[9];

    int w = gPreviewImageRGBWidth[LR];
    int h = gPreviewImageRGBHeight[LR];

    // K is the transformation to map the canonical [-1,1] vertex coordinate
    // system to the [0,w] image coordinate system before applying the given
    // affine transformation trs.
    K[0] = w / 2.0;
    K[1] = 0.0;
    K[2] = w / 2.0;
    K[3] = 0.0;
    K[4] = -h / 2.0;
    K[5] = h / 2.0;
    K[6] = 0.0;
    K[7] = 0.0;
    K[8] = 1.0;

    db_Identity3x3(Kinv);
    db_InvertCalibrationMatrix(Kinv, K);

    for(int i=0; i<9; i++)
    {
        H[i] = trs[i];
    }

    // Move the origin such that the frame is centered in the previewFBO
    H[2] += (gPreviewFBOWidth / 2 - gPreviewImageRGBWidth[LR] / 2);
    H[5] -= (gPreviewFBOHeight / 2 - gPreviewImageRGBHeight[LR] / 2);

    // Hp = inv(K) * H * K
    db_Identity3x3(Htemp);
    db_Multiply3x3_3x3(Htemp, H, K);
    db_Multiply3x3_3x3(Hp, Kinv, Htemp);

    ConvertAffine3x3toGL4x4(g_dAffinetrans, Hp);

    ////////////////////////////////////////////////
    ////// Compute g_dAffinetransInv now...   //////
    ////////////////////////////////////////////////

    w = gPreviewFBOWidth;
    h = gPreviewFBOHeight;

    K[0] = w / 2.0;
    K[1] = 0.0;
    K[2] = w / 2.0;
    K[3] = 0.0;
    K[4] = h / 2.0;
    K[5] = h / 2.0;
    K[6] = 0.0;
    K[7] = 0.0;
    K[8] = 1.0;

    db_Identity3x3(Kinv);
    db_InvertCalibrationMatrix(Kinv, K);

    db_Identity3x3(Hinv);
    db_InvertAffineTransform(Hinv, H);

    Hinv[2] += (gPreviewFBOWidth / 2 - gPreviewImageRGBWidth[LR] / 2);
    Hinv[5] -= (gPreviewFBOHeight / 2 - gPreviewImageRGBHeight[LR] / 2);

    // Hp = inv(K) * Hinv * K
    db_Identity3x3(Htemp);
    db_Multiply3x3_3x3(Htemp, Hinv, K);
    db_Multiply3x3_3x3(Hp, Kinv, Htemp);

    ConvertAffine3x3toGL4x4(g_dAffinetransInv, Hp);
}

void AllocateTextureMemory(int widthHR, int heightHR, int widthLR, int heightLR)
{
    gPreviewImageRGBWidth[HR] = widthHR;
    gPreviewImageRGBHeight[HR] = heightHR;

    gPreviewImageRGBWidth[LR] = widthLR;
    gPreviewImageRGBHeight[LR] = heightLR;

    sem_init(&gPreviewImageRGB_semaphore, 0, 1);
    sem_init(&gPreviewImageReady_semaphore, 0, 1);

    sem_wait(&gPreviewImageRGB_semaphore);
    gPreviewImageRGB[LR] = ImageUtils::allocateImage(gPreviewImageRGBWidth[LR],
            gPreviewImageRGBHeight[LR], 4);
    ClearPreviewImageRGB(LR);
    gPreviewImageRGB[HR] = ImageUtils::allocateImage(gPreviewImageRGBWidth[HR],
            gPreviewImageRGBHeight[HR], 4);
    ClearPreviewImageRGB(HR);
    sem_post(&gPreviewImageRGB_semaphore);

    gPreviewFBOWidth = PREVIEW_FBO_WIDTH_SCALE * gPreviewImageRGBWidth[LR];
    gPreviewFBOHeight = PREVIEW_FBO_HEIGHT_SCALE * gPreviewImageRGBHeight[LR];

    UpdateWarpTransformation(g_dAffinetransIdent);
}

void FreeTextureMemory()
{
    sem_wait(&gPreviewImageRGB_semaphore);
    ImageUtils::freeImage(gPreviewImageRGB[LR]);
    ImageUtils::freeImage(gPreviewImageRGB[HR]);
    sem_post(&gPreviewImageRGB_semaphore);

    sem_destroy(&gPreviewImageRGB_semaphore);
    sem_destroy(&gPreviewImageReady_semaphore);
}

extern "C"
{
    JNIEXPORT jint JNICALL Java_com_android_camera_panorama_MosaicRenderer_init(
            JNIEnv * env, jobject obj);
    JNIEXPORT void JNICALL Java_com_android_camera_panorama_MosaicRenderer_reset(
            JNIEnv * env, jobject obj,  jint width, jint height);
    JNIEXPORT void JNICALL Java_com_android_camera_panorama_MosaicRenderer_preprocess(
            JNIEnv * env, jobject obj, jfloatArray stMatrix);
    JNIEXPORT void JNICALL Java_com_android_camera_panorama_MosaicRenderer_transferGPUtoCPU(
            JNIEnv * env, jobject obj);
    JNIEXPORT void JNICALL Java_com_android_camera_panorama_MosaicRenderer_step(
            JNIEnv * env, jobject obj);
    JNIEXPORT void JNICALL Java_com_android_camera_panorama_MosaicRenderer_ready(
            JNIEnv * env, jobject obj);
    JNIEXPORT void JNICALL Java_com_android_camera_panorama_MosaicRenderer_setWarping(
            JNIEnv * env, jobject obj, jboolean flag);
};

JNIEXPORT jint JNICALL Java_com_android_camera_panorama_MosaicRenderer_init(
        JNIEnv * env, jobject obj)
{
    gSurfTexRenderer[LR].InitializeGLProgram();
    gSurfTexRenderer[HR].InitializeGLProgram();
    gWarper.InitializeGLProgram();
    gPreview.InitializeGLProgram();
    gBuffer.InitializeGLContext();
    gBufferInput[LR].InitializeGLContext();
    gBufferInput[HR].InitializeGLContext();

    glBindFramebuffer(GL_FRAMEBUFFER, 0);

    glGenTextures(1, gSurfaceTextureID);
    // bind the surface texture
    bindSurfaceTexture(gSurfaceTextureID[0]);

    return (jint) gSurfaceTextureID[0];
}


JNIEXPORT void JNICALL Java_com_android_camera_panorama_MosaicRenderer_reset(
        JNIEnv * env, jobject obj,  jint width, jint height)
{
    gBuffer.Init(gPreviewFBOWidth, gPreviewFBOHeight, GL_RGBA);

    gBufferInput[LR].Init(gPreviewImageRGBWidth[LR],
            gPreviewImageRGBHeight[LR], GL_RGBA);

    gBufferInput[HR].Init(gPreviewImageRGBWidth[HR],
            gPreviewImageRGBHeight[HR], GL_RGBA);

    sem_wait(&gPreviewImageRGB_semaphore);
    ClearPreviewImageRGB(LR);
    ClearPreviewImageRGB(HR);
    sem_post(&gPreviewImageRGB_semaphore);

    // bind the surface texture
    bindSurfaceTexture(gSurfaceTextureID[0]);

    gSurfTexRenderer[LR].SetupGraphics(&gBufferInput[LR]);
    gSurfTexRenderer[LR].Clear(0.0, 0.0, 0.0, 1.0);
    gSurfTexRenderer[LR].SetViewportMatrix(1, 1, 1, 1);
    gSurfTexRenderer[LR].SetScalingMatrix(1.0f, -1.0f);
    gSurfTexRenderer[LR].SetInputTextureName(gSurfaceTextureID[0]);
    gSurfTexRenderer[LR].SetInputTextureType(GL_TEXTURE_EXTERNAL_OES_ENUM);

    gSurfTexRenderer[HR].SetupGraphics(&gBufferInput[HR]);
    gSurfTexRenderer[HR].Clear(0.0, 0.0, 0.0, 1.0);
    gSurfTexRenderer[HR].SetViewportMatrix(1, 1, 1, 1);
    gSurfTexRenderer[HR].SetScalingMatrix(1.0f, -1.0f);
    gSurfTexRenderer[HR].SetInputTextureName(gSurfaceTextureID[0]);
    gSurfTexRenderer[HR].SetInputTextureType(GL_TEXTURE_EXTERNAL_OES_ENUM);

    gWarper.SetupGraphics(&gBuffer);
    gWarper.Clear(0.0, 0.0, 0.0, 1.0);
    gWarper.SetViewportMatrix(gPreviewImageRGBWidth[LR],
            gPreviewImageRGBHeight[LR], gBuffer.GetWidth(),
            gBuffer.GetHeight());
    gWarper.SetScalingMatrix(1.0f, 1.0f);
    gWarper.SetInputTextureName(gBufferInput[LR].GetTextureName());
    gWarper.SetInputTextureType(GL_TEXTURE_2D);

    gPreview.SetupGraphics(width, height);
    gPreview.Clear(0.0, 0.0, 0.0, 1.0);
    gPreview.SetViewportMatrix(1, 1, 1, 1);
    gPreview.SetScalingMatrix(1.0f, -1.0f);
    gPreview.SetInputTextureName(gBuffer.GetTextureName());
    gPreview.SetInputTextureType(GL_TEXTURE_2D);
}

JNIEXPORT void JNICALL Java_com_android_camera_panorama_MosaicRenderer_preprocess(
        JNIEnv * env, jobject obj, jfloatArray stMatrix)
{
    jfloat *stmat = env->GetFloatArrayElements(stMatrix, 0);

    gSurfTexRenderer[LR].SetSTMatrix((float*) stmat);
    gSurfTexRenderer[HR].SetSTMatrix((float*) stmat);

    env->ReleaseFloatArrayElements(stMatrix, stmat, 0);

    gSurfTexRenderer[LR].DrawTexture(g_dAffinetransIdent);
    gSurfTexRenderer[HR].DrawTexture(g_dAffinetransIdent);
}

JNIEXPORT void JNICALL Java_com_android_camera_panorama_MosaicRenderer_transferGPUtoCPU(
        JNIEnv * env, jobject obj)
{
    sem_wait(&gPreviewImageRGB_semaphore);
    // Bind to the input LR FBO and read the Low-Res data from there...
    glBindFramebuffer(GL_FRAMEBUFFER, gBufferInput[LR].GetFrameBufferName());
    glReadPixels(0,
                 0,
                 gBufferInput[LR].GetWidth(),
                 gBufferInput[LR].GetHeight(),
                 GL_RGBA,
                 GL_UNSIGNED_BYTE,
                 gPreviewImageRGB[LR]);

    checkGlError("glReadPixels LR");

    // Bind to the input HR FBO and read the high-res data from there...
    glBindFramebuffer(GL_FRAMEBUFFER, gBufferInput[HR].GetFrameBufferName());
    glReadPixels(0,
                 0,
                 gBufferInput[HR].GetWidth(),
                 gBufferInput[HR].GetHeight(),
                 GL_RGBA,
                 GL_UNSIGNED_BYTE,
                 gPreviewImageRGB[HR]);

    checkGlError("glReadPixels HR");

    sem_post(&gPreviewImageRGB_semaphore);
}

JNIEXPORT void JNICALL Java_com_android_camera_panorama_MosaicRenderer_step(
        JNIEnv * env, jobject obj)
{
    // Use the gWarper shader to apply the current frame transformation to the
    // current frame and then add it to the gBuffer FBO.
    gWarper.DrawTexture(g_dAffinetransGL);

    // Use the gPreview shader to apply the inverse of the current frame
    // transformation to the gBuffer FBO and render it to the screen.
    gPreview.DrawTexture(g_dAffinetransInvGL);
}

JNIEXPORT void JNICALL Java_com_android_camera_panorama_MosaicRenderer_setWarping(
        JNIEnv * env, jobject obj, jboolean flag)
{
    // TODO: Review this logic
    if(gWarpImage != (bool) flag) //switching from viewfinder to capture or vice-versa
    {
        gWarper.Clear(0.0, 0.0, 0.0, 1.0);
        gPreview.Clear(0.0, 0.0, 0.0, 1.0);
        // Clear the screen to black.
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT);
    }
    gWarpImage = (bool)flag;
}


JNIEXPORT void JNICALL Java_com_android_camera_panorama_MosaicRenderer_ready(
        JNIEnv * env, jobject obj)
{
    if(!gWarpImage)
    {
        // TODO: Review this logic...
        UpdateWarpTransformation(g_dAffinetransIdent);

        for(int i=0; i<16; i++)
        {
            g_dAffinetrans[i] = g_dAffinetransIdent[i];
            g_dAffinetransInv[i] = g_dAffinetransIdent[i];
        }
        g_dAffinetrans[12] = 1.0f;
        g_dAffinetrans[13] = 1.0f;
    }

    for(int i=0; i<16; i++)
    {
        g_dAffinetransGL[i] = g_dAffinetrans[i];
        g_dAffinetransInvGL[i] = g_dAffinetransInv[i];
    }
}
