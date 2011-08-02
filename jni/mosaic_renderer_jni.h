#pragma once
#include <GLES2/gl2.h>
#include <GLES2/gl2ext.h>
#include <semaphore.h>

// The Preview FBO dimensions are determined from the low-res
// frame dimensions (gPreviewImageRGBWidth, gPreviewImageRGBHeight)
// using the scale factors below.
const int PREVIEW_FBO_WIDTH_SCALE = 4;
const int PREVIEW_FBO_HEIGHT_SCALE = 2;

extern "C" void AllocateTextureMemory(int width, int height);
extern "C" void FreeTextureMemory();
extern "C" void UpdateWarpTransformation(float *trs);

extern unsigned char* gPreviewImageRGB;
extern int gPreviewImageRGBWidth;
extern int gPreviewImageRGBHeight;

extern sem_t gPreviewImageRGB_semaphore;

extern double g_dAffinetrans[16];
extern double g_dAffinetransInv[16];
