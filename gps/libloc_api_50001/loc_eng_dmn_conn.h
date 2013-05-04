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
#ifndef LOC_ENG_DATA_SERVER_H
#define LOC_ENG_DATA_SERVER_H

#include "loc_eng_dmn_conn_thread_helper.h"

#ifdef _ANDROID_

#define GPSONE_LOC_API_Q_PATH "/data/misc/gpsone_d/gpsone_loc_api_q"
#define GPSONE_LOC_API_RESP_Q_PATH "/data/misc/gpsone_d/gpsone_loc_api_resp_q"
#define QUIPC_CTRL_Q_PATH "/data/misc/gpsone_d/quipc_ctrl_q"
#define MSAPM_CTRL_Q_PATH "/data/misc/gpsone_d/msapm_ctrl_q"
#define MSAPU_CTRL_Q_PATH "/data/misc/gpsone_d/msapu_ctrl_q"

#else

#define GPSONE_LOC_API_Q_PATH "/tmp/gpsone_loc_api_q"
#define GPSONE_LOC_API_RESP_Q_PATH "/tmp/gpsone_loc_api_resp_q"
#define QUIPC_CTRL_Q_PATH "/tmp/quipc_ctrl_q"
#define MSAPM_CTRL_Q_PATH "/tmp/msapm_ctrl_q"
#define MSAPU_CTRL_Q_PATH "/tmp/msapu_ctrl_q"

#endif

int loc_eng_dmn_conn_loc_api_server_launch(thelper_create_thread   create_thread_cb,
    const char * loc_api_q_path, const char * ctrl_q_path, void *agps_handle);
int loc_eng_dmn_conn_loc_api_server_unblock(void);
int loc_eng_dmn_conn_loc_api_server_join(void);
int loc_eng_dmn_conn_loc_api_server_data_conn(int, int);

#endif /* LOC_ENG_DATA_SERVER_H */

