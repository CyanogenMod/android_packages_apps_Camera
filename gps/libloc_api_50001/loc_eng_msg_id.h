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
#ifndef LOC_ENG_MSG_ID_H
#define LOC_ENG_MSG_ID_H

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

struct msgbuf {
    unsigned int msgsz;
    void* msgid;
};

enum loc_eng_msg_ids_t {
    /* 0x 0 - 0xEF is reserved for daemon internal */
    /* 0xF0 - 0x1FF is reserved for daemon & framework communication */
    LOC_ENG_MSG_QUIT = 0x200,

    LOC_ENG_MSG_ENGINE_DOWN,
    LOC_ENG_MSG_ENGINE_UP,

    LOC_ENG_MSG_START_FIX,
    LOC_ENG_MSG_STOP_FIX,
    LOC_ENG_MSG_SET_POSITION_MODE,
    LOC_ENG_MSG_SET_TIME,
    LOC_ENG_MSG_INJECT_XTRA_DATA,
    LOC_ENG_MSG_INJECT_LOCATION,
    LOC_ENG_MSG_DELETE_AIDING_DATA,
    LOC_ENG_MSG_SET_APN,
    LOC_ENG_MSG_SET_SERVER_URL,
    LOC_ENG_MSG_SET_SERVER_IPV4,
    LOC_ENG_MSG_ENABLE_DATA,

    LOC_ENG_MSG_SUPL_VERSION,
    LOC_ENG_MSG_SET_SENSOR_CONTROL_CONFIG,
    LOC_ENG_MSG_SET_SENSOR_PROPERTIES,
    LOC_ENG_MSG_SET_SENSOR_PERF_CONTROL_CONFIG,
    LOC_ENG_MSG_MUTE_SESSION,

    LOC_ENG_MSG_ATL_OPEN_SUCCESS,
    LOC_ENG_MSG_ATL_CLOSED,
    LOC_ENG_MSG_ATL_OPEN_FAILED,

    LOC_ENG_MSG_REPORT_POSITION,
    LOC_ENG_MSG_REPORT_SV,
    LOC_ENG_MSG_REPORT_STATUS,
    LOC_ENG_MSG_REPORT_NMEA,
    LOC_ENG_MSG_REQUEST_BIT,
    LOC_ENG_MSG_RELEASE_BIT,
    LOC_ENG_MSG_REQUEST_ATL,
    LOC_ENG_MSG_RELEASE_ATL,
    LOC_ENG_MSG_REQUEST_WIFI,
    LOC_ENG_MSG_RELEASE_WIFI,
    LOC_ENG_MSG_REQUEST_NI,
    LOC_ENG_MSG_INFORM_NI_RESPONSE,
    LOC_ENG_MSG_REQUEST_XTRA_DATA,
    LOC_ENG_MSG_REQUEST_TIME,
    LOC_ENG_MSG_REQUEST_POSITION,
    LOC_ENG_MSG_EXT_POWER_CONFIG,

#ifdef FEATURE_ULP
    // The following messages are added for ulp
    LOC_ENG_MSG_REQUEST_PHONE_CONTEXT,
    LOC_ENG_MSG_REQUEST_NETWORK_POSIITON,

    /* Following messages are for ulp, start at index 0x600 */

    // Message is sent by GPS HAL layer to add/remove unique request criteria
    ULP_MSG_UPDATE_CRITERIA = 0x600,

    // Message is sent by GPS HAL layer to request ULP to start producing position fixes

    ULP_MSG_START_FIX,

    // Message is sent by Android framework(GpsLocationProvider)
    // to request ULP to stop producing position fixes
    ULP_MSG_STOP_FIX,

    // Message is sent by Android framework(GpsLocationProvider)
    // to inject phone context setting  include initial phone context setting and subsequent changes
    ULP_MSG_INJECT_PHONE_CONTEXT_SETTINGS,

    // Message is sent by network provider to INJECT the position in UlpNetworkPositionReport format
    ULP_MSG_INJECT_NETWORK_POSITION,

    // Message is sent by QUIPC provider in order to report the position in GpsPosition format with QUIPC status
    ULP_MSG_REPORT_QUIPC_POSITION,

    // Message is sent by QUIPC module in order to request some info from ULP
    ULP_MSG_REQUEST_COARSE_POSITION,

    // Message is sent to ULP module to re-evaluate its subsystems
    ULP_MSG_MONITOR,

    // Last ULP MSG
    ULP_MSG_LAST = 0x700,
#endif
    /* Message is sent by HAL to LOC API to configure LTE Positioning
       Profile in modem */
    LOC_ENG_MSG_LPP_CONFIG,

#ifdef FEATURE_ULP
    // Message is sent by Android framework (GpsLocationProvider)
    // to inject the raw command
    ULP_MSG_INJECT_RAW_COMMAND,
#endif

    /* Message is sent by HAL to LOC API to select A-GLONASS protocol */
    LOC_ENG_MSG_A_GLONASS_PROTOCOL,
};

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* LOC_ENG_MSG_ID_H */
