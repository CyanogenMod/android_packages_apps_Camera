// OpenGL ES 2.0 code
#include <jni.h>
#include <android/log.h>

#include <db_utilities_camera.h>
#include "mosaic/ImageUtils.h"
#include "mosaic_renderer/FrameBuffer.h"
#include "mosaic_renderer/WarpRenderer.h"
#include <stdio.h>
#include <stdlib.h>
#include <math.h>

#include "mosaic_renderer_jni.h"

#define  LOG_TAG    "MosaicRenderer"
#define  LOGI(...)  __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)
#define  LOGE(...)  __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)

// Texture handle
GLuint textureId[1];

bool warp_image = true;

// Low-Res input image frame in RGB format for preview rendering
unsigned char* gPreviewImageRGB;
// Low-Res preview image width
int gPreviewImageRGBWidth;
// Low-Res preview image height
int gPreviewImageRGBHeight;

// Semaphore to protect simultaneous read/writes from gPreviewImageRGB
sem_t gPreviewImageRGB_semaphore;

// Off-screen preview FBO width (large enough to store the entire
// preview mosaic).
int gPreviewFBOWidth;
// Off-screen preview FBO height (large enough to store the entire
// preview mosaic).
int gPreviewFBOHeight;

// Shader to add warped current frame to the preview FBO
WarpRenderer gWarper;
// Shader to warp and render the preview FBO to the screen
WarpRenderer gPreview;
// Off-screen FBO to store the result of gWarper
FrameBuffer *gBuffer;

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

void LoadTexture(unsigned char *buffer, int width, int height, GLuint texId)
{
    glBindTexture(GL_TEXTURE_2D, texId);

    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB, width, height, 0, GL_RGB,
            GL_UNSIGNED_BYTE, buffer);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
}

void ReloadTexture(unsigned char *buffer, int width, int height, GLuint texId)
{
    glBindTexture(GL_TEXTURE_2D, texId);

    glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, width, height, GL_RGB,
            GL_UNSIGNED_BYTE, buffer);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
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

    int w = gPreviewImageRGBWidth;
    int h = gPreviewImageRGBHeight;

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
    H[2] += (gPreviewFBOWidth / 2 - gPreviewImageRGBWidth / 2);
    H[5] -= (gPreviewFBOHeight / 2 - gPreviewImageRGBHeight / 2);

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

    Hinv[2] += (gPreviewFBOWidth / 2 - gPreviewImageRGBWidth / 2);
    Hinv[5] -= (gPreviewFBOHeight / 2 - gPreviewImageRGBHeight / 2);

    // Hp = inv(K) * Hinv * K
    db_Identity3x3(Htemp);
    db_Multiply3x3_3x3(Htemp, Hinv, K);
    db_Multiply3x3_3x3(Hp, Kinv, Htemp);

    ConvertAffine3x3toGL4x4(g_dAffinetransInv, Hp);
}

void AllocateTextureMemory(int width, int height)
{
    gPreviewImageRGBWidth = width;
    gPreviewImageRGBHeight = height;

    sem_init(&gPreviewImageRGB_semaphore, 0, 1);

    sem_wait(&gPreviewImageRGB_semaphore);
    gPreviewImageRGB = ImageUtils::allocateImage(gPreviewImageRGBWidth,
            gPreviewImageRGBHeight, ImageUtils::IMAGE_TYPE_NUM_CHANNELS);
    memset(gPreviewImageRGB, 0, gPreviewImageRGBWidth *
            gPreviewImageRGBHeight * 3 * sizeof(unsigned char));
    sem_post(&gPreviewImageRGB_semaphore);

    gPreviewFBOWidth = PREVIEW_FBO_WIDTH_SCALE * gPreviewImageRGBWidth;
    gPreviewFBOHeight = PREVIEW_FBO_HEIGHT_SCALE * gPreviewImageRGBHeight;

    UpdateWarpTransformation(g_dAffinetransIdent);
}

void FreeTextureMemory()
{
    sem_wait(&gPreviewImageRGB_semaphore);
    ImageUtils::freeImage(gPreviewImageRGB);
    sem_post(&gPreviewImageRGB_semaphore);

    sem_destroy(&gPreviewImageRGB_semaphore);
}

extern "C"
{
    JNIEXPORT void JNICALL Java_com_android_camera_panorama_MosaicRenderer_init(
            JNIEnv * env, jobject obj);
    JNIEXPORT void JNICALL Java_com_android_camera_panorama_MosaicRenderer_reset(
            JNIEnv * env, jobject obj,  jint width, jint height);
    JNIEXPORT void JNICALL Java_com_android_camera_panorama_MosaicRenderer_step(
            JNIEnv * env, jobject obj);
    JNIEXPORT void JNICALL Java_com_android_camera_panorama_MosaicRenderer_ready(
            JNIEnv * env, jobject obj);
    JNIEXPORT void JNICALL Java_com_android_camera_panorama_MosaicRenderer_togglewarping(
            JNIEnv * env, jobject obj);
};

JNIEXPORT void JNICALL Java_com_android_camera_panorama_MosaicRenderer_init(
        JNIEnv * env, jobject obj)
{
    gWarper.InitializeGLProgram();
    gPreview.InitializeGLProgram();

    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    glGenTextures(1, &textureId[0]);
}

JNIEXPORT void JNICALL Java_com_android_camera_panorama_MosaicRenderer_reset(
        JNIEnv * env, jobject obj,  jint width, jint height)
{
    gBuffer = new FrameBuffer();
    gBuffer->Init(gPreviewFBOWidth, gPreviewFBOHeight, GL_RGBA);

    sem_wait(&gPreviewImageRGB_semaphore);
    memset(gPreviewImageRGB, 0, gPreviewImageRGBWidth *
            gPreviewImageRGBHeight * 3 * sizeof(unsigned char));
    sem_post(&gPreviewImageRGB_semaphore);

    // Load texture
    LoadTexture(gPreviewImageRGB, gPreviewImageRGBWidth,
            gPreviewImageRGBHeight, textureId[0]);

    gWarper.SetupGraphics(gBuffer);
    gWarper.Clear(0.0, 0.0, 0.0, 1.0);
    gWarper.SetViewportMatrix(gPreviewImageRGBWidth,
            gPreviewImageRGBHeight, gBuffer->GetWidth(), gBuffer->GetHeight());
    gWarper.SetScalingMatrix(1.0f, 1.0f);
    gWarper.SetInputTextureName(textureId[0]);

    gPreview.SetupGraphics(width, height);
    gPreview.Clear(0.0, 0.0, 0.0, 1.0);
    gPreview.SetViewportMatrix(1, 1, 1, 1);
    gPreview.SetScalingMatrix(1.0f, -1.0f);
    gPreview.SetInputTextureName(gBuffer->GetTextureName());
}

JNIEXPORT void JNICALL Java_com_android_camera_panorama_MosaicRenderer_step(
        JNIEnv * env, jobject obj)
{
    // Use the gWarper shader to apply the current frame transformation to the
    // current frame and then add it to the gBuffer FBO.
    gWarper.DrawTexture(g_dAffinetransGL);

    // Clear the screen to black.
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
    glClear(GL_COLOR_BUFFER_BIT);

    // Use the gPreview shader to apply the inverse of the current frame
    // transformation to the gBuffer FBO and render it to the screen.
    gPreview.DrawTexture(g_dAffinetransInvGL);
}

JNIEXPORT void JNICALL Java_com_android_camera_panorama_MosaicRenderer_togglewarping(
        JNIEnv * env, jobject obj)
{
    warp_image = !warp_image;
}


JNIEXPORT void JNICALL Java_com_android_camera_panorama_MosaicRenderer_ready(
        JNIEnv * env, jobject obj)
{
    sem_wait(&gPreviewImageRGB_semaphore);
    ReloadTexture(gPreviewImageRGB, gPreviewImageRGBWidth,
            gPreviewImageRGBHeight, textureId[0]);
    sem_post(&gPreviewImageRGB_semaphore);

    if(!warp_image)
    {
        for(int i=0; i<16; i++)
        {
            g_dAffinetrans[i] = g_dAffinetransIdent[i];
        }
    }

    for(int i=0; i<16; i++)
    {
        g_dAffinetransGL[i] = g_dAffinetrans[i];
        g_dAffinetransInvGL[i] = g_dAffinetransInv[i];
    }
}
