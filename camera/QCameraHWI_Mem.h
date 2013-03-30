/*
** Copyright (c) 2011-2012 Code Aurora Forum. All rights reserved.
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
**     http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/


#ifndef __QCAMERAHWI_MEM_H
#define __QCAMERAHWI_MEM_H

#include <binder/MemoryBase.h>
#include <binder/MemoryHeapBase.h>
#include <utils/threads.h>
#include <stdint.h>

extern "C" {
#include <linux/android_pmem.h>
#include <linux/ion.h>
#include <camera.h>
//#include <camera_defs_i.h>

}

#define VIDEO_BUFFER_COUNT 5
#define VIDEO_BUFFER_COUNT_LOW_POWER_CAMCORDER 9

#define PREVIEW_BUFFER_COUNT 5
#define VIDEO_BUFFER_COUNT_LOW_POWER_CAMCORDER 9

namespace android {

// This class represents a heap which maintains several contiguous
// buffers.  The heap may be backed by pmem (when pmem_pool contains
// the name of a /dev/pmem* file), or by ashmem (when pmem_pool == NULL).

struct MemPool : public RefBase {
    MemPool(int buffer_size, int num_buffers,
            int frame_size,
            const char *name);

    virtual ~MemPool() = 0;

    void completeInitialization();
    bool initialized() const {
        return mHeap != NULL && mHeap->base() != MAP_FAILED;
    }

    virtual status_t dump(int fd, const Vector<String16>& args) const;

    int mBufferSize;
    int mAlignedBufferSize;
    int mNumBuffers;
    int mFrameSize;
    sp<MemoryHeapBase> mHeap;
    sp<MemoryBase> *mBuffers;

    const char *mName;
};

class AshmemPool : public MemPool {
public:
    AshmemPool(int buffer_size, int num_buffers,
               int frame_size,
               const char *name);
};

class PmemPool : public MemPool {
public:
    PmemPool(const char *pmem_pool,
             int flags, int pmem_type,
             int buffer_size, int num_buffers,
             int frame_size, int cbcr_offset,
             int yoffset, const char *name);
    virtual ~PmemPool();
    int mFd;
    int mPmemType;
    int mCbCrOffset;
    int myOffset;
    int mCameraControlFd;
    uint32_t mAlignedSize;
    struct pmem_region mSize;
};

class IonPool : public MemPool {
public:
    IonPool( int flags,
             int buffer_size, int num_buffers,
             int frame_size, int cbcr_offset,
             int yoffset, const char *name);
    virtual ~IonPool();
    int mFd;
    int mCbCrOffset;
    int myOffset;
    int mCameraControlFd;
    uint32_t mAlignedSize;
private:
    static const char mIonDevName[];
};

};
#endif
