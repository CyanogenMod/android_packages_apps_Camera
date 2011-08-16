// OpenGL ES 2.0 code
#include <jni.h>
#include <android/log.h>

#include <db_utilities_camera.h>
#include "mosaic/ImageUtils.h"
#include "mosaic_renderer/FrameBuffer.h"
#include "mosaic_renderer/WarpRenderer.h"
#include "mosaic_renderer/SurfaceTextureRenderer.h"
#include "mosaic_renderer/YVURenderer.h"
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

// Low-Res input image frame in YUVA format for preview rendering and processing
// and high-res YUVA input image for processing.
unsigned char* gPreviewImage[NR];
// Low-Res & high-res preview image width
int gPreviewImageWidth[NR];
// Low-Res & high-res preview image height
int gPreviewImageHeight[NR];

// Semaphore to protect simultaneous read/writes from gPreviewImage
sem_t gPreviewImage_semaphore;

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

// Shader to convert RGBA textures into YVU textures for processing
YVURenderer gYVURenderer[NR];
// Off-screen FBOs to store the low-res and high-res YVU textures for processing
FrameBuffer gBufferInputYVU[NR];

// Shader to add warped current frame to the preview FBO
WarpRenderer gWarper1;
// Shader to translate the preview FBO
WarpRenderer gWarper2;
// Off-screen FBOs (flip-flop) to store the result of gWarper1 & gWarper2
FrameBuffer gBuffer[2];

// Shader to warp and render the preview FBO to the screen
WarpRenderer gPreview;

// Index of the gBuffer FBO gWarper1 is going to write into
int gCurrentFBOIndex = 0;

// Variables to represent the present top-left corner of the first frame
// in the previewFBO
double gOriginX = 0.0f;
double gOriginY = 0.0f;

// Variables tracking the translation value for the current frame and the
// last frame (both w.r.t the first frame). The difference between these
// values is used to control the panning speed of the viewfinder display
// on the UI screen.
double gThisTx = 0.0f;
double gLastTx = 0.0f;

// Affine transformation in GL 4x4 format (column-major) to warp the
// current frame into the first frame coordinate system.
GLfloat g_dAffinetransGL[16];

// Affine transformation in GL 4x4 format (column-major) to warp the
// preview FBO into the current frame coordinate system.
GLfloat g_dAffinetransInvGL[16];

// XY translation in GL 4x4 format (column-major) to slide the preview
// viewfinder across the preview FBO
GLfloat g_dTranslationGL[16];

// GL 4x4 Identity transformation
GLfloat g_dAffinetransIdent[] = {
    1., 0., 0., 0.,
    0., 1., 0., 0.,
    0., 0., 1., 0.,
    0., 0., 0., 1.};

float g_dIdent3x3[] = {
    1.0, 0.0, 0.0,
    0.0, 1.0, 0.0,
    0.0, 0.0, 1.0};

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

void ClearPreviewImage(int mID)
{
    unsigned char* ptr = gPreviewImage[mID];
    for(int j = 0, i = 0;
            j < gPreviewImageWidth[mID] * gPreviewImageHeight[mID] * 4;
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

// This function computes fills the 4x4 matrices g_dAffinetrans,
// g_dAffinetransInv and g_dTranslation using the specified 3x3 affine
// transformation between the first captured frame and the current frame.
// The computed g_dAffinetrans is such that it warps the current frame into the
// coordinate system of the first frame. Thus, applying this transformation
// to each successive frame builds up the preview mosaic in the first frame
// coordinate system. Then the computed g_dAffinetransInv is such that it
// warps the computed preview mosaic into the coordinate system of the
// original (as captured) current frame. This has the effect of showing
// the current frame as is (without warping) but warping the rest of the
// mosaic data to still be in alignment with this frame.
void UpdateWarpTransformation(float *trs)
{
    double H[9], Hinv[9], Hp[9], Htemp[9], T[9], Tp[9], Ttemp[9];
    double K[9], Kinv[9];

    int w = gPreviewImageWidth[LR];
    int h = gPreviewImageHeight[LR];

    // K is the transformation to map the canonical [-1,1] vertex coordinate
    // system to the [0,w] image coordinate system before applying the given
    // affine transformation trs.
    K[0] = w / 2.0 - 0.5;
    K[1] = 0.0;
    K[2] = w / 2.0 - 0.5;
    K[3] = 0.0;
    K[4] = h / 2.0 - 0.5;
    K[5] = h / 2.0 - 0.5;
    K[6] = 0.0;
    K[7] = 0.0;
    K[8] = 1.0;

    db_Identity3x3(Kinv);
    db_InvertCalibrationMatrix(Kinv, K);

    for(int i=0; i<9; i++)
    {
        H[i] = trs[i];
    }

    gThisTx = trs[2];

    // Move the origin such that the frame is centered in the previewFBO
    H[2] += gOriginX;
    H[5] += gOriginY;

    // Hp = inv(K) * H * K
    // K moves the coordinate system from openGL to image pixels so
    // that the alignment transform H can be applied to them.
    // inv(K) moves the coordinate system back to openGL normalized
    // coordinates so that the shader can correctly render it.
    db_Identity3x3(Htemp);
    db_Multiply3x3_3x3(Htemp, H, K);
    db_Multiply3x3_3x3(Hp, Kinv, Htemp);

    ConvertAffine3x3toGL4x4(g_dAffinetrans, Hp);

    ////////////////////////////////////////////////////////////////
    ////// Compute g_Translation & g_dAffinetransInv now...   //////
    ////////////////////////////////////////////////////////////////

    w = gPreviewFBOWidth;
    h = gPreviewFBOHeight;

    K[0] = w / 2.0 - 0.5;
    K[1] = 0.0;
    K[2] = w / 2.0 - 0.5;
    K[3] = 0.0;
    K[4] = h / 2.0 - 0.5;
    K[5] = h / 2.0 - 0.5;
    K[6] = 0.0;
    K[7] = 0.0;
    K[8] = 1.0;

    db_Identity3x3(Kinv);
    db_InvertCalibrationMatrix(Kinv, K);

    // T only has a fraction of the x-translation of this frame relative
    // to the last frame.
    db_Identity3x3(T);
    T[2] = (gThisTx - gLastTx) * VIEWFINDER_PAN_FACTOR_HORZ;
    T[5] = 0;

    gLastTx = gThisTx;

    db_Identity3x3(Hinv);
    db_InvertAffineTransform(Hinv, H);

    Hinv[2] += gOriginX;
    Hinv[5] += gOriginY;

    // We update the origin of where the first frame is laid out in the
    // previewFBO to reflect that we have panned the entire preview mosaic
    // inside the previewFBO by translation T. This is needed to ensure
    // that the next frame can be correctly rendered aligned with the existing
    // mosaic.
    gOriginX += T[2];
    gOriginY += T[5];


    // Hp = inv(K) * Hinv * K
    db_Identity3x3(Htemp);
    db_Multiply3x3_3x3(Htemp, Hinv, K);
    db_Multiply3x3_3x3(Hp, Kinv, Htemp);

    ConvertAffine3x3toGL4x4(g_dAffinetransInv, Hp);

    // Tp = inv(K) * T * K
    db_Identity3x3(Ttemp);
    db_Multiply3x3_3x3(Ttemp, T, K);
    db_Multiply3x3_3x3(Tp, Kinv, Ttemp);

    ConvertAffine3x3toGL4x4(g_dTranslation, Tp);

}

void AllocateTextureMemory(int widthHR, int heightHR, int widthLR, int heightLR)
{
    gPreviewImageWidth[HR] = widthHR;
    gPreviewImageHeight[HR] = heightHR;

    gPreviewImageWidth[LR] = widthLR;
    gPreviewImageHeight[LR] = heightLR;

    sem_init(&gPreviewImage_semaphore, 0, 1);

    sem_wait(&gPreviewImage_semaphore);
    gPreviewImage[LR] = ImageUtils::allocateImage(gPreviewImageWidth[LR],
            gPreviewImageHeight[LR], 4);
    ClearPreviewImage(LR);
    gPreviewImage[HR] = ImageUtils::allocateImage(gPreviewImageWidth[HR],
            gPreviewImageHeight[HR], 4);
    ClearPreviewImage(HR);
    sem_post(&gPreviewImage_semaphore);

    gPreviewFBOWidth = PREVIEW_FBO_WIDTH_SCALE * gPreviewImageWidth[LR];
    gPreviewFBOHeight = PREVIEW_FBO_HEIGHT_SCALE * gPreviewImageHeight[LR];

    gOriginX = (gPreviewFBOWidth / 2 - gPreviewImageWidth[LR] / 2);
    gOriginY = (gPreviewFBOHeight / 2 - gPreviewImageHeight[LR] / 2);

    UpdateWarpTransformation(g_dIdent3x3);
}

void FreeTextureMemory()
{
    sem_wait(&gPreviewImage_semaphore);
    ImageUtils::freeImage(gPreviewImage[LR]);
    ImageUtils::freeImage(gPreviewImage[HR]);
    sem_post(&gPreviewImage_semaphore);

    sem_destroy(&gPreviewImage_semaphore);
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
    gYVURenderer[LR].InitializeGLProgram();
    gYVURenderer[HR].InitializeGLProgram();
    gWarper1.InitializeGLProgram();
    gWarper2.InitializeGLProgram();
    gPreview.InitializeGLProgram();
    gBuffer[0].InitializeGLContext();
    gBuffer[1].InitializeGLContext();
    gBufferInput[LR].InitializeGLContext();
    gBufferInput[HR].InitializeGLContext();
    gBufferInputYVU[LR].InitializeGLContext();
    gBufferInputYVU[HR].InitializeGLContext();

    glBindFramebuffer(GL_FRAMEBUFFER, 0);

    glGenTextures(1, gSurfaceTextureID);
    // bind the surface texture
    bindSurfaceTexture(gSurfaceTextureID[0]);

    return (jint) gSurfaceTextureID[0];
}


JNIEXPORT void JNICALL Java_com_android_camera_panorama_MosaicRenderer_reset(
        JNIEnv * env, jobject obj,  jint width, jint height)
{
    gBuffer[0].Init(gPreviewFBOWidth, gPreviewFBOHeight, GL_RGBA);
    gBuffer[1].Init(gPreviewFBOWidth, gPreviewFBOHeight, GL_RGBA);

    gBufferInput[LR].Init(gPreviewImageWidth[LR],
            gPreviewImageHeight[LR], GL_RGBA);

    gBufferInput[HR].Init(gPreviewImageWidth[HR],
            gPreviewImageHeight[HR], GL_RGBA);

    gBufferInputYVU[LR].Init(gPreviewImageWidth[LR],
            gPreviewImageHeight[LR], GL_RGBA);

    gBufferInputYVU[HR].Init(gPreviewImageWidth[HR],
            gPreviewImageHeight[HR], GL_RGBA);

    sem_wait(&gPreviewImage_semaphore);
    ClearPreviewImage(LR);
    ClearPreviewImage(HR);
    sem_post(&gPreviewImage_semaphore);

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

    gYVURenderer[LR].SetupGraphics(&gBufferInputYVU[LR]);
    gYVURenderer[LR].Clear(0.0, 0.0, 0.0, 1.0);
    gYVURenderer[LR].SetInputTextureName(gBufferInput[LR].GetTextureName());
    gYVURenderer[LR].SetInputTextureType(GL_TEXTURE_2D);

    gYVURenderer[HR].SetupGraphics(&gBufferInputYVU[HR]);
    gYVURenderer[HR].Clear(0.0, 0.0, 0.0, 1.0);
    gYVURenderer[HR].SetInputTextureName(gBufferInput[HR].GetTextureName());
    gYVURenderer[HR].SetInputTextureType(GL_TEXTURE_2D);

    // gBufferInput[LR] --> gWarper1 --> gBuffer[gCurrentFBOIndex]
    gWarper1.SetupGraphics(&gBuffer[gCurrentFBOIndex]);
    gWarper1.Clear(0.0, 0.0, 0.0, 1.0);
    gWarper1.SetViewportMatrix(gPreviewImageWidth[LR],
            gPreviewImageHeight[LR], gBuffer[gCurrentFBOIndex].GetWidth(),
            gBuffer[gCurrentFBOIndex].GetHeight());
    gWarper1.SetScalingMatrix(1.0f, 1.0f);
    gWarper1.SetInputTextureName(gBufferInput[LR].GetTextureName());
    gWarper1.SetInputTextureType(GL_TEXTURE_2D);

    // gBuffer[gCurrentFBOIndex] --> gWarper2 --> gBuffer[1-gCurrentFBOIndex]
    gWarper2.SetupGraphics(&gBuffer[1-gCurrentFBOIndex]);
    gWarper2.Clear(0.0, 0.0, 0.0, 1.0);
    gWarper2.SetViewportMatrix(1, 1, 1, 1);
    gWarper2.SetScalingMatrix(1.0f, 1.0f);
    gWarper2.SetInputTextureName(gBuffer[gCurrentFBOIndex].GetTextureName());
    gWarper2.SetInputTextureType(GL_TEXTURE_2D);

    gPreview.SetupGraphics(width, height);
    gPreview.Clear(0.0, 0.0, 0.0, 1.0);
    gPreview.SetViewportMatrix(1, 1, 1, 1);
    // Scale the previewFBO so that the viewfinder window fills the layout height
    // while maintaining the image aspect ratio
    gPreview.SetScalingMatrix((PREVIEW_FBO_WIDTH_SCALE / PREVIEW_FBO_HEIGHT_SCALE) *
        (gPreviewImageWidth[LR] / gPreviewImageHeight[LR]) / (width / height) *
        PREVIEW_FBO_HEIGHT_SCALE, -1.0f*PREVIEW_FBO_HEIGHT_SCALE);
    gPreview.SetInputTextureName(gBuffer[1-gCurrentFBOIndex].GetTextureName());
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

#ifndef now_ms
#include <time.h>
static double
now_ms(void)
{
    //struct timespec res;
    struct timeval res;
    //clock_gettime(CLOCK_REALTIME, &res);
    gettimeofday(&res, NULL);
    return 1000.0*res.tv_sec + (double)res.tv_usec/1e3;
}
#endif



JNIEXPORT void JNICALL Java_com_android_camera_panorama_MosaicRenderer_transferGPUtoCPU(
        JNIEnv * env, jobject obj)
{
    double t0, t1, time_c;

    t0 = now_ms();

    gYVURenderer[LR].DrawTexture();
    gYVURenderer[HR].DrawTexture();

    t1 = now_ms();
    time_c = t1 - t0;
    LOGV("YVU Rendering: %g ms", time_c);

    sem_wait(&gPreviewImage_semaphore);
    // Bind to the input LR FBO and read the Low-Res data from there...
    glBindFramebuffer(GL_FRAMEBUFFER, gBufferInputYVU[LR].GetFrameBufferName());
    t0 = now_ms();
    glReadPixels(0,
                 0,
                 gBufferInput[LR].GetWidth(),
                 gBufferInput[LR].GetHeight(),
                 GL_RGBA,
                 GL_UNSIGNED_BYTE,
                 gPreviewImage[LR]);

    checkGlError("glReadPixels LR");
    t1 = now_ms();
    time_c = t1 - t0;
    LOGV("glReadPixels LR: %g ms", time_c);

    // Bind to the input HR FBO and read the high-res data from there...
    glBindFramebuffer(GL_FRAMEBUFFER, gBufferInputYVU[HR].GetFrameBufferName());
    t0 = now_ms();
    glReadPixels(0,
                 0,
                 gBufferInput[HR].GetWidth(),
                 gBufferInput[HR].GetHeight(),
                 GL_RGBA,
                 GL_UNSIGNED_BYTE,
                 gPreviewImage[HR]);

    checkGlError("glReadPixels HR");
    t1 = now_ms();
    time_c = t1 - t0;
    LOGV("glReadPixels HR: %g ms", time_c);

    sem_post(&gPreviewImage_semaphore);
}

JNIEXPORT void JNICALL Java_com_android_camera_panorama_MosaicRenderer_step(
        JNIEnv * env, jobject obj)
{
    if(!gWarpImage)
    {
        gWarper1.SetupGraphics(&gBuffer[gCurrentFBOIndex]);
        gPreview.SetInputTextureName(gBuffer[gCurrentFBOIndex].GetTextureName());

        // Use gWarper1 shader to apply the current frame transformation to the
        // current frame and then add it to the gBuffer FBO.
        gWarper1.DrawTexture(g_dAffinetransGL);

        // Use the gPreview shader to apply the inverse of the current frame
        // transformation to the gBuffer FBO and render it to the screen.
        gPreview.DrawTexture(g_dAffinetransInvGL);
    }
    else
    {
        gWarper1.SetupGraphics(&gBuffer[gCurrentFBOIndex]);
        gWarper2.SetupGraphics(&gBuffer[1-gCurrentFBOIndex]);
        gWarper2.SetInputTextureName(gBuffer[gCurrentFBOIndex].GetTextureName());
        gPreview.SetInputTextureName(gBuffer[1-gCurrentFBOIndex].GetTextureName());

        // Use gWarper1 shader to apply the current frame transformation to the
        // current frame and then add it to the gBuffer FBO.
        gWarper1.DrawTexture(g_dAffinetransGL);

        // Use gWarper2 to translate the contents of the gBuffer FBO and copy
        // it into the second gBuffer FBO
        gWarper2.DrawTexture(g_dTranslationGL);

        // Use the gPreview shader to apply the inverse of the current frame
        // transformation to the gBuffer FBO and render it to the screen.
        gPreview.DrawTexture(g_dAffinetransInvGL);

        gCurrentFBOIndex = 1 - gCurrentFBOIndex;
    }
}

JNIEXPORT void JNICALL Java_com_android_camera_panorama_MosaicRenderer_setWarping(
        JNIEnv * env, jobject obj, jboolean flag)
{
    // TODO: Review this logic
    if(gWarpImage != (bool) flag) //switching from viewfinder to capture or vice-versa
    {
        gWarper1.Clear(0.0, 0.0, 0.0, 1.0);
        gWarper2.Clear(0.0, 0.0, 0.0, 1.0);
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
        UpdateWarpTransformation(g_dIdent3x3);

        for(int i=0; i<16; i++)
        {
            g_dAffinetransInv[i] = g_dAffinetransIdent[i];
        }
    }

    for(int i=0; i<16; i++)
    {
        g_dAffinetransGL[i] = g_dAffinetrans[i];
        g_dAffinetransInvGL[i] = g_dAffinetransInv[i];
        g_dTranslationGL[i] = g_dTranslation[i];
    }
}
