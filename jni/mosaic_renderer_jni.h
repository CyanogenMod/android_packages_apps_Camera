#pragma once
#include <GLES2/gl2.h>
#include <GLES2/gl2ext.h>
#include <semaphore.h>

// The Preview FBO dimensions are determined from the low-res
// frame dimensions (gPreviewImageRGBWidth, gPreviewImageRGBHeight)
// using the scale factors below.
const int PREVIEW_FBO_WIDTH_SCALE = 4;
const int PREVIEW_FBO_HEIGHT_SCALE = 2;

const int LR = 0; // Low-resolution mode
const int HR = 1; // High-resolution mode
const int NR = 2; // Number of resolution modes

extern "C" void AllocateTextureMemory(int widthHR, int heightHR,
        int widthLR, int heightLR);
extern "C" void FreeTextureMemory();
extern "C" void UpdateWarpTransformation(float *trs);

extern unsigned char* gPreviewImageRGB[NR];
extern int gPreviewImageRGBWidth[NR];
extern int gPreviewImageRGBHeight[NR];

extern sem_t gPreviewImageRGB_semaphore;
extern sem_t gPreviewImageReady_semaphore;

extern double g_dAffinetrans[16];
extern double g_dAffinetransInv[16];
