/*
Copyright (c) 2011, Code Aurora Forum. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above
      copyright notice, this list of conditions and the following
      disclaimer in the documentation and/or other materials provided
      with the distribution.
    * Neither the name of Code Aurora Forum, Inc. nor the names of its
      contributors may be used to endorse or promote products derived
      from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

#include <sys/mman.h>
#include <fcntl.h>
#include <stdio.h>
#include <unistd.h>
#include <stdlib.h>
#include <ctype.h>
#include <errno.h>
#include <string.h>
#include "mm_camera_dbg.h"
#include <time.h>
#include "mm_camera_interface2.h"
#include <linux/ion.h>
#include "camera.h"

#define MM_CAMERA_PROFILE 1

struct file;
struct inode;
struct vm_area_struct;

/*===========================================================================
 * FUNCTION    - do_mmap -
 *
 * DESCRIPTION:  retured virtual addresss
 *==========================================================================*/
uint8_t *mm_camera_do_mmap(uint32_t size, int *pmemFd)
{
    void *ret; /* returned virtual address */
    int  pmem_fd = open("/dev/pmem_adsp", O_RDWR|O_SYNC);

    if (pmem_fd <= 0) {
        CDBG("do_mmap: Open device /dev/pmem_adsp failed!\n");
        return NULL;
    }
    /* to make it page size aligned */
    size = (size + 4095) & (~4095);
  ret = mmap(NULL,
    size,
    PROT_READ  | PROT_WRITE,
    MAP_SHARED,
    pmem_fd,
    0);
    if (ret == MAP_FAILED) {
        CDBG("do_mmap: pmem mmap() failed: %s (%d)\n", strerror(errno), errno);
        close(pmem_fd);
        return NULL;
    }
    CDBG("do_mmap: pmem mmap fd %d ptr %p len %u\n", pmem_fd, ret, size);
    *pmemFd = pmem_fd;
    return(uint8_t *)ret;
}

/*===========================================================================
 * FUNCTION    - do_munmap -
 *
 * DESCRIPTION:
 *==========================================================================*/
int mm_camera_do_munmap(int pmem_fd, void *addr, size_t size)
{
    int rc;

    if (pmem_fd <= 0) {
        CDBG("%s:invalid fd=%d\n", __func__, pmem_fd);
        return -1;
    }
    size = (size + 4095) & (~4095);
    CDBG("munmapped size = %d, virt_addr = 0x%p\n",
    size, addr);
    rc = (munmap(addr, size));
    close(pmem_fd);
    CDBG("do_mmap: pmem munmap fd %d ptr %p len %u rc %d\n", pmem_fd, addr,
    size, rc);
    return rc;
}

/*============================================================
   FUNCTION mm_camera_dump_image
   DESCRIPTION:
==============================================================*/
int mm_camera_dump_image(void *addr, uint32_t size, char *filename)
{
  int file_fd = open(filename, O_RDWR | O_CREAT, 0777);

  if (file_fd < 0) {
    CDBG_HIGH("%s: cannot open file\n", __func__);
		return -1;
	} else
    write(file_fd, addr, size);
  close(file_fd);
	CDBG("%s: %s, size=%d\n", __func__, filename, size);
	return 0;
}

uint32_t mm_camera_get_msm_frame_len(cam_format_t fmt_type,
                                     camera_mode_t mode,
                                     int width,
                                     int height,
                                     int image_type,
                                     uint8_t *num_planes,
                                     uint32_t plane[])
{
    uint32_t size;
    *num_planes = 0;
    int local_height;

    switch (fmt_type) {
    case CAMERA_YUV_420_NV12:
    case CAMERA_YUV_420_NV21:
        *num_planes = 2;
        if(CAMERA_MODE_3D == mode) {
            size = (uint32_t)(PAD_TO_2K(width*height)*3/2);
            plane[0] = PAD_TO_WORD(width*height);
        } else {
            if (image_type == OUTPUT_TYPE_V) {
                plane[0] = PAD_TO_2K(width * height);
                plane[1] = PAD_TO_2K(width * height/2);
            } else if (image_type == OUTPUT_TYPE_P) {
                plane[0] = PAD_TO_WORD(width * height);
                plane[1] = PAD_TO_WORD(width * height/2);
            } else {
                plane[0] = PAD_TO_WORD(width * CEILING16(height));
                plane[1] = PAD_TO_WORD(width * CEILING16(height)/2);
            }
            size = plane[0] + plane[1];
        }
        break;
    case CAMERA_BAYER_SBGGR10:
        *num_planes = 1;
        plane[0] = PAD_TO_WORD(width * height);
        size = plane[0];
        break;
    case CAMERA_YUV_422_NV16:
    case CAMERA_YUV_422_NV61:
      if( image_type == OUTPUT_TYPE_S || image_type == OUTPUT_TYPE_V) {
        local_height = CEILING16(height);
      } else {
        local_height = height;
      }
        *num_planes = 2;
        plane[0] = PAD_TO_WORD(width * height);
        plane[1] = PAD_TO_WORD(width * height);
        size = plane[0] + plane[1];
        break;
    default:
        CDBG("%s: format %d not supported.\n",
            __func__, fmt_type);
        size = 0;
    }
    CDBG("%s:fmt=%d,image_type=%d,width=%d,height=%d,frame_len=%d\n",
        __func__, fmt_type, image_type, width, height, size);
    return size;
}

void mm_camera_util_profile(const char *str)
{
#if (MM_CAMERA_PROFILE)
    struct timespec cur_time;

    clock_gettime(CLOCK_REALTIME, &cur_time);
    CDBG_HIGH("PROFILE %s: %ld.%09ld\n", str,
    cur_time.tv_sec, cur_time.tv_nsec);
#endif
}

