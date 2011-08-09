#pragma once

#include "FrameBuffer.h"

#include <GLES2/gl2.h>

#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>

//TODO: Add a base class Renderer for WarpRenderer and SurfaceTextureRenderer.
class SurfaceTextureRenderer {
  public:
    SurfaceTextureRenderer();
    virtual ~SurfaceTextureRenderer();

    // Initialize OpenGL resources
    // @return true if successful
    bool InitializeGLProgram();

    bool SetupGraphics(FrameBuffer* buffer);
    bool SetupGraphics(int width, int height);

    bool Clear(float r, float g, float b, float a);

    void SetViewportMatrix(int w, int h, int W, int H);
    void SetScalingMatrix(float xscale, float yscale);
    bool DrawTexture(GLfloat *affine);

    int GetTextureName();
    void SetInputTextureName(GLuint textureName);
    void SetInputTextureDimensions(int width, int height);
    void SetInputTextureType(GLenum textureType);

    void InitializeGLContext();

    void SetSTMatrix(float *stmat);

  protected:

    GLuint loadShader(GLenum shaderType, const char* pSource);
    GLuint createProgram(const char*, const char* );

    int SurfaceWidth() const { return mSurfaceWidth; }
    int SurfaceHeight() const { return mSurfaceHeight; }

 private:
    // Source code for shaders.
    virtual const char* VertexShaderSource() const;
    virtual const char* FragmentShaderSource() const;

    // Redefine this to use special texture types such as
    // GL_TEXTURE_EXTERNAL_OES.
    virtual GLenum InputTextureType() const { return mInputTextureType; }

    GLuint mGlProgram;
    GLuint mInputTextureName;
    GLenum mInputTextureType;
    int mInputTextureWidth;
    int mInputTextureHeight;

    // Attribute locations
    GLint  mScalingtransLoc;
    GLint muSTMatrixHandle;
    GLint maPositionHandle;
    GLint maTextureHandle;

    GLfloat mViewportMatrix[16];
    GLfloat mScalingMatrix[16];
    GLfloat mSTMatrix[16];

    int mSurfaceWidth;      // Width of target surface.
    int mSurfaceHeight;     // Height of target surface.

    FrameBuffer *mFrameBuffer;
};

