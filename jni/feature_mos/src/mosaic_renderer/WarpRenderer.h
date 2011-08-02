#pragma once

#include "FrameBuffer.h"

#include <GLES2/gl2.h>

#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>

class WarpRenderer {
  public:
    WarpRenderer();
    virtual ~WarpRenderer();

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

    void InitializeGLContext();

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
    virtual GLenum InputTextureType() const { return GL_TEXTURE_2D; }


    GLuint mGlProgram;
    GLuint mInputTextureName;
    int mInputTextureWidth;
    int mInputTextureHeight;

    GLuint mTexHandle;                  // Handle to s_texture.
    GLuint mTexCoordHandle;             // Handle to a_texCoord.
    GLuint mTriangleVerticesHandle;     // Handle to vPosition.

    // Attribute locations
    GLint  mPositionLoc;
    GLint  mAffinetransLoc;
    GLint  mViewporttransLoc;
    GLint  mScalingtransLoc;
    GLint  mTexCoordLoc;

    GLfloat mViewportMatrix[16];
    GLfloat mScalingMatrix[16];

    // Sampler location
    GLint mSamplerLoc;

    int mSurfaceWidth;      // Width of target surface.
    int mSurfaceHeight;     // Height of target surface.

    FrameBuffer *mFrameBuffer;
};

