/* Copyright (c) 2011-2012, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation, nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */

#ifndef __LOC_H__
#define __LOC_H__

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

#include <ctype.h>
#include <cutils/properties.h>
#include <hardware/gps.h>

#define MIN_POSSIBLE_FIX_INTERVAL 1000 /* msec */

typedef enum loc_server_type {
    LOC_AGPS_CDMA_PDE_SERVER,
    LOC_AGPS_CUSTOM_PDE_SERVER,
    LOC_AGPS_MPC_SERVER,
    LOC_AGPS_SUPL_SERVER
} LocServerType;

typedef enum loc_position_mode_type {
    LOC_POSITION_MODE_STANDALONE,
    LOC_POSITION_MODE_MS_BASED,
    LOC_POSITION_MODE_MS_ASSISTED,
    LOC_POSITION_MODE_RESERVED_1,
    LOC_POSITION_MODE_RESERVED_2,
    LOC_POSITION_MODE_RESERVED_3,
    LOC_POSITION_MODE_RESERVED_4,
    LOC_POSITION_MODE_RESERVED_5
} LocPositionMode;

typedef void (*loc_location_cb_ext) (GpsLocation* location, void* locExt);
typedef void (*loc_sv_status_cb_ext) (GpsSvStatus* sv_status, void* svExt);
typedef void* (*loc_ext_parser)(void* data);

typedef struct {
    loc_location_cb_ext location_cb;
    gps_status_callback status_cb;
    loc_sv_status_cb_ext sv_status_cb;
    gps_nmea_callback nmea_cb;
    gps_set_capabilities set_capabilities_cb;
    gps_acquire_wakelock acquire_wakelock_cb;
    gps_release_wakelock release_wakelock_cb;
    gps_create_thread create_thread_cb;
    loc_ext_parser location_ext_parser;
    loc_ext_parser sv_ext_parser;
    gps_request_utc_time request_utc_time_cb;
} LocCallbacks;

enum loc_sess_status {
    LOC_SESS_SUCCESS,
    LOC_SESS_INTERMEDIATE,
    LOC_SESS_FAILURE
};

typedef uint32_t LocPosTechMask;
#define LOC_POS_TECH_MASK_DEFAULT ((LocPosTechMask)0x00000000)
#define LOC_POS_TECH_MASK_SATELLITE ((LocPosTechMask)0x00000001)
#define LOC_POS_TECH_MASK_CELLID ((LocPosTechMask)0x00000002)
#define LOC_POS_TECH_MASK_WIFI ((LocPosTechMask)0x00000004)
#define LOC_POS_TECH_MASK_SENSORS ((LocPosTechMask)0x00000008)
#define LOC_POS_TECH_MASK_REFERENCE_LOCATION ((LocPosTechMask)0x00000010)
#define LOC_POS_TECH_MASK_INJECTED_COARSE_POSITION ((LocPosTechMask)0x00000020)

#ifdef FEATURE_ULP
void loc_ulp_msg_sender(void* loc_eng_data_p, void* msg);
#endif

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif //__LOC_H__
