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

#ifndef LOC_ENG_LOG_H
#define LOC_ENG_LOG_H

#ifdef __cplusplus
extern "C"
{
#endif

#include <ctype.h>
#include <hardware/gps.h>
#include <loc.h>

const char* loc_get_gps_status_name(GpsStatusValue gps_status);
const char* loc_get_msg_name(int id);
const char* loc_get_position_mode_name(GpsPositionMode mode);
const char* loc_get_position_recurrence_name(GpsPositionRecurrence recur);
const char* loc_get_aiding_data_mask_names(GpsAidingData data);
const char* loc_get_agps_type_name(AGpsType type);
const char* loc_get_ni_type_name(GpsNiType type);
const char* loc_get_ni_response_name(GpsUserResponseType response);
const char* loc_get_ni_encoding_name(GpsNiEncodingType encoding);
#ifdef FEATURE_IPV6
const char* loc_get_agps_bear_name(AGpsBearerType bear);
#endif
const char* loc_get_server_type_name(LocServerType type);
const char* loc_get_position_sess_status_name(enum loc_sess_status status);
const char* loc_get_agps_status_name(AGpsStatusValue status);

#ifdef __cplusplus
}
#endif

#endif /* LOC_ENG_LOG_H */
