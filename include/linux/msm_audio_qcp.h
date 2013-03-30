/****************************************************************************
 ****************************************************************************
 ***
 ***   This header was automatically generated from a Linux kernel header
 ***   of the same name, to make information necessary for userspace to
 ***   call into the kernel available to libc.  It contains only constants,
 ***   structures, and macros generated from the original header, and thus,
 ***   contains no copyrightable information.
 ***
 ***   To edit the content of this header, modify the corresponding
 ***   source file (e.g. under external/kernel-headers/original/) then
 ***   run bionic/libc/kernel/tools/update_all.py
 ***
 ***   Any manual change here will be lost the next time this script will
 ***   be run. You've been warned!
 ***
 ****************************************************************************
 ****************************************************************************/
#ifndef __MSM_AUDIO_QCP_H
#define __MSM_AUDIO_QCP_H
#include <linux/msm_audio.h>
#define AUDIO_SET_QCELP_ENC_CONFIG _IOW(AUDIO_IOCTL_MAGIC,   0, struct msm_audio_qcelp_enc_config)
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define AUDIO_GET_QCELP_ENC_CONFIG _IOR(AUDIO_IOCTL_MAGIC,   1, struct msm_audio_qcelp_enc_config)
#define AUDIO_SET_EVRC_ENC_CONFIG _IOW(AUDIO_IOCTL_MAGIC,   2, struct msm_audio_evrc_enc_config)
#define AUDIO_GET_EVRC_ENC_CONFIG _IOR(AUDIO_IOCTL_MAGIC,   3, struct msm_audio_evrc_enc_config)
#define CDMA_RATE_BLANK 0x00
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define CDMA_RATE_EIGHTH 0x01
#define CDMA_RATE_QUARTER 0x02
#define CDMA_RATE_HALF 0x03
#define CDMA_RATE_FULL 0x04
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define CDMA_RATE_ERASURE 0x05
struct msm_audio_qcelp_enc_config {
 uint32_t cdma_rate;
 uint32_t min_bit_rate;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
 uint32_t max_bit_rate;
};
struct msm_audio_evrc_enc_config {
 uint32_t cdma_rate;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
 uint32_t min_bit_rate;
 uint32_t max_bit_rate;
};
#endif
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
