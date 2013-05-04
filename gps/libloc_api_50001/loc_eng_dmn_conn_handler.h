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
#ifndef LOC_ENG_DATA_SERVER_HANDLER
#define LOC_ENG_DATA_SERVER_HANDLER

#include <linux/types.h>
#include <arpa/inet.h>

//for SSID_BUF_SIZE
#include <hardware/gps.h>

#ifndef SSID_BUF_SIZE
    #define SSID_BUF_SIZE (32+1)
#endif

enum {
    /* 0x0 - 0xEF is reserved for daemon internal */
    GPSONE_LOC_API_IF_REQUEST   = 0xF0,
    GPSONE_LOC_API_IF_RELEASE,
    GPSONE_LOC_API_RESPONSE,
    GPSONE_UNBLOCK,
};

enum {
    GPSONE_LOC_API_IF_REQUEST_SUCCESS = 0xF0,
    GPSONE_LOC_API_IF_RELEASE_SUCCESS,
    GPSONE_LOC_API_IF_FAILURE,
};


struct ctrl_msg_response {
    int result;
};

struct ctrl_msg_unblock {
    int reserved;
};

typedef enum {
  IF_REQUEST_TYPE_SUPL = 0,
  IF_REQUEST_TYPE_WIFI,
  IF_REQUEST_TYPE_ANY
} ctrl_if_req_type_e_type;

typedef enum {
  IF_REQUEST_SENDER_ID_QUIPC = 0,
  IF_REQUEST_SENDER_ID_MSAPM,
  IF_REQUEST_SENDER_ID_MSAPU,
  IF_REQUEST_SENDER_ID_GPSONE_DAEMON,
  IF_REQUEST_SENDER_ID_MODEM
} ctrl_if_req_sender_id_e_type;

struct ctrl_msg_if_request {
    ctrl_if_req_type_e_type type;
    ctrl_if_req_sender_id_e_type sender_id;
    unsigned long ipv4_addr;
    unsigned char ipv6_addr[16];
    char ssid[SSID_BUF_SIZE];
    char password[SSID_BUF_SIZE];
};

/* do not change this structure */
struct ctrl_msgbuf {
    size_t msgsz;
    uint16_t reserved1;
    uint32_t reserved2;
    uint8_t ctrl_type;
    union {
        struct ctrl_msg_response   cmsg_response;
        struct ctrl_msg_unblock    cmsg_unblock;
        struct ctrl_msg_if_request cmsg_if_request;
    } cmsg;
};

extern void* loc_api_handle;

int loc_eng_dmn_conn_loc_api_server_if_request_handler(struct ctrl_msgbuf *pmsg, int len);
int loc_eng_dmn_conn_loc_api_server_if_release_handler(struct ctrl_msgbuf *pmsg, int len);

#endif /* LOC_ENG_DATA_SERVER_HANDLER */
