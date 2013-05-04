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
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#include "log_util.h"
#include "loc_eng_msg.h"
#include "loc_eng_dmn_conn.h"
#include "loc_eng_dmn_conn_handler.h"

void* loc_api_handle = NULL;

int loc_eng_dmn_conn_loc_api_server_if_request_handler(struct ctrl_msgbuf *pmsg, int len)
{
    LOC_LOGD("%s:%d]\n", __func__, __LINE__);
#ifndef DEBUG_DMN_LOC_API
    if (NULL == loc_api_handle) {
        LOC_LOGE("%s:%d] NO agps data handle\n", __func__, __LINE__);
        return 1;
    }

    if (NULL != loc_api_handle) {
        loc_if_req_type_e_type type;
        switch (pmsg->cmsg.cmsg_if_request.type) {
          case IF_REQUEST_TYPE_SUPL:
          {
            LOC_LOGD("IF_REQUEST_TYPE_SUPL");
            type = LOC_ENG_IF_REQUEST_TYPE_SUPL;
            break;
          }
          case IF_REQUEST_TYPE_WIFI:
          {
            LOC_LOGD("IF_REQUEST_TYPE_WIFI");
            type = LOC_ENG_IF_REQUEST_TYPE_WIFI;
            break;
          }
          case IF_REQUEST_TYPE_ANY:
          {
            LOC_LOGD("IF_REQUEST_TYPE_ANY");
            type = LOC_ENG_IF_REQUEST_TYPE_ANY;
            break;
          }
          default:
          {
            LOC_LOGD("invalid IF_REQUEST_TYPE!");
            return -1;
          }
        }
        switch (pmsg->cmsg.cmsg_if_request.sender_id) {
          case IF_REQUEST_SENDER_ID_QUIPC:
          {
            LOC_LOGD("IF_REQUEST_SENDER_ID_QUIPC");
            loc_eng_msg_request_wifi *msg(
                new loc_eng_msg_request_wifi(loc_api_handle,
                                            type,
                                            LOC_ENG_IF_REQUEST_SENDER_ID_QUIPC,
                                            (char*)pmsg->cmsg.cmsg_if_request.ssid,
                                            (char*)pmsg->cmsg.cmsg_if_request.password));
            loc_eng_msg_sender(loc_api_handle, msg);
            break;
          }
          case IF_REQUEST_SENDER_ID_MSAPM:
          {
            LOC_LOGD("IF_REQUEST_SENDER_ID_MSAPM");
            loc_eng_msg_request_wifi *msg(
                new loc_eng_msg_request_wifi(loc_api_handle,
                                            type,
                                            LOC_ENG_IF_REQUEST_SENDER_ID_MSAPM,
                                            (char*)pmsg->cmsg.cmsg_if_request.ssid,
                                            (char*)pmsg->cmsg.cmsg_if_request.password));
            loc_eng_msg_sender(loc_api_handle, msg);
            break;
          }
          case IF_REQUEST_SENDER_ID_MSAPU:
          {
            LOC_LOGD("IF_REQUEST_SENDER_ID_MSAPU");
            loc_eng_msg_request_wifi *msg(
                new loc_eng_msg_request_wifi(loc_api_handle,
                                            type,
                                            LOC_ENG_IF_REQUEST_SENDER_ID_MSAPU,
                                            (char*)pmsg->cmsg.cmsg_if_request.ssid,
                                            (char*)pmsg->cmsg.cmsg_if_request.password));
            loc_eng_msg_sender(loc_api_handle, msg);
            break;
          }
          case IF_REQUEST_SENDER_ID_GPSONE_DAEMON:
          {
            LOC_LOGD("IF_REQUEST_SENDER_ID_GPSONE_DAEMON");
            loc_eng_msg_request_bit *msg(
                new loc_eng_msg_request_bit(loc_api_handle,
                                            type,
                                            pmsg->cmsg.cmsg_if_request.ipv4_addr,
                                            (char*)pmsg->cmsg.cmsg_if_request.ipv6_addr));
            loc_eng_msg_sender(loc_api_handle, msg);
            break;
          }
          default:
          {
            LOC_LOGD("invalid IF_REQUEST_SENDER_ID!");
            return -1;
          }
        }
    }

#else
   loc_eng_dmn_conn_loc_api_server_data_conn(LOC_ENG_IF_REQUEST_SENDER_ID_GPSONE_DAEMON, GPSONE_LOC_API_IF_REQUEST_SUCCESS);
#endif
    return 0;
}

int loc_eng_dmn_conn_loc_api_server_if_release_handler(struct ctrl_msgbuf *pmsg, int len)
{
    LOC_LOGD("%s:%d]\n", __func__, __LINE__);
#ifndef DEBUG_DMN_LOC_API
    loc_if_req_type_e_type type;
    switch (pmsg->cmsg.cmsg_if_request.type) {
      case IF_REQUEST_TYPE_SUPL:
      {
        LOC_LOGD("IF_REQUEST_TYPE_SUPL");
        type = LOC_ENG_IF_REQUEST_TYPE_SUPL;
        break;
      }
      case IF_REQUEST_TYPE_WIFI:
      {
        LOC_LOGD("IF_REQUEST_TYPE_WIFI");
        type = LOC_ENG_IF_REQUEST_TYPE_WIFI;
        break;
      }
      case IF_REQUEST_TYPE_ANY:
      {
        LOC_LOGD("IF_REQUEST_TYPE_ANY");
        type = LOC_ENG_IF_REQUEST_TYPE_ANY;
        break;
      }
      default:
      {
        LOC_LOGD("invalid IF_REQUEST_TYPE!");
        return -1;
      }
    }
    switch (pmsg->cmsg.cmsg_if_request.sender_id) {
      case IF_REQUEST_SENDER_ID_QUIPC:
      {
        LOC_LOGD("IF_REQUEST_SENDER_ID_QUIPC");
        loc_eng_msg_release_wifi *msg(
            new loc_eng_msg_release_wifi(loc_api_handle,
                                        type,
                                        LOC_ENG_IF_REQUEST_SENDER_ID_QUIPC,
                                        (char*)pmsg->cmsg.cmsg_if_request.ssid,
                                        (char*)pmsg->cmsg.cmsg_if_request.password));
        loc_eng_msg_sender(loc_api_handle, msg);
        break;
      }
      case IF_REQUEST_SENDER_ID_MSAPM:
      {
        LOC_LOGD("IF_REQUEST_SENDER_ID_MSAPM");
        loc_eng_msg_release_wifi *msg(
            new loc_eng_msg_release_wifi(loc_api_handle,
                                        type,
                                        LOC_ENG_IF_REQUEST_SENDER_ID_MSAPM,
                                        (char*)pmsg->cmsg.cmsg_if_request.ssid,
                                        (char*)pmsg->cmsg.cmsg_if_request.password));
        loc_eng_msg_sender(loc_api_handle, msg);
        break;
      }
      case IF_REQUEST_SENDER_ID_MSAPU:
      {
        LOC_LOGD("IF_REQUEST_SENDER_ID_MSAPU");
        loc_eng_msg_release_wifi *msg(
            new loc_eng_msg_release_wifi(loc_api_handle,
                                        type,
                                        LOC_ENG_IF_REQUEST_SENDER_ID_MSAPU,
                                        (char*)pmsg->cmsg.cmsg_if_request.ssid,
                                        (char*)pmsg->cmsg.cmsg_if_request.password));
        loc_eng_msg_sender(loc_api_handle, msg);
        break;
      }
      case IF_REQUEST_SENDER_ID_GPSONE_DAEMON:
      {
        LOC_LOGD("IF_REQUEST_SENDER_ID_GPSONE_DAEMON");
        loc_eng_msg_release_bit *msg(
            new loc_eng_msg_release_bit(loc_api_handle,
                                        type,
                                        pmsg->cmsg.cmsg_if_request.ipv4_addr,
                                        (char*)pmsg->cmsg.cmsg_if_request.ipv6_addr));
        loc_eng_msg_sender(loc_api_handle, msg);
        break;
      }
      default:
      {
        LOC_LOGD("invalid IF_REQUEST_SENDER_ID!");
        return -1;
      }
    }
#else
   loc_eng_dmn_conn_loc_api_server_data_conn(LOC_ENG_IF_REQUEST_SENDER_ID_GPSONE_DAEMON, GPSONE_LOC_API_IF_RELEASE_SUCCESS);
#endif
    return 0;
}

