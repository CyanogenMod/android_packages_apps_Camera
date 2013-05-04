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
#include <linux/stat.h>
#include <fcntl.h>
#include <linux/types.h>
#include <unistd.h>
#include <errno.h>
#include <grp.h>
#include <sys/stat.h>

#include "log_util.h"

#include "loc_eng_dmn_conn_glue_msg.h"
#include "loc_eng_dmn_conn_handler.h"
#include "loc_eng_dmn_conn.h"
#include "loc_eng_msg.h"

static int loc_api_server_msgqid;
static int loc_api_resp_msgqid;
static int quipc_msgqid;
static int msapm_msgqid;
static int msapu_msgqid;

static const char * global_loc_api_q_path = GPSONE_LOC_API_Q_PATH;
static const char * global_loc_api_resp_q_path = GPSONE_LOC_API_RESP_Q_PATH;
static const char * global_quipc_ctrl_q_path = QUIPC_CTRL_Q_PATH;
static const char * global_msapm_ctrl_q_path = MSAPM_CTRL_Q_PATH;
static const char * global_msapu_ctrl_q_path = MSAPU_CTRL_Q_PATH;

static int loc_api_server_proc_init(void *context)
{
    loc_api_server_msgqid = loc_eng_dmn_conn_glue_msgget(global_loc_api_q_path, O_RDWR);
    //change mode/group for the global_loc_api_q_path pipe
    int result = chmod (global_loc_api_q_path, 0660);
    if (result != 0)
    {
        LOC_LOGE("failed to change mode for %s, error = %s\n", global_loc_api_q_path, strerror(errno));
    }

    struct group * gps_group = getgrnam("gps");
    if (gps_group != NULL)
    {
       result = chown (global_loc_api_q_path, -1, gps_group->gr_gid);
       if (result != 0)
       {
          LOC_LOGE("chown for pipe failed, pipe %s, gid = %d, result = %d, error = %s\n",
                   global_loc_api_q_path, gps_group->gr_gid, result, strerror(errno));
       }
    }
    else
    {
       LOC_LOGE("getgrnam for gps failed, error code = %d\n",  errno);
    }

    loc_api_resp_msgqid = loc_eng_dmn_conn_glue_msgget(global_loc_api_resp_q_path, O_RDWR);

    //change mode/group for the global_loc_api_resp_q_path pipe
    result = chmod (global_loc_api_resp_q_path, 0660);
    if (result != 0)
    {
        LOC_LOGE("failed to change mode for %s, error = %s\n", global_loc_api_resp_q_path, strerror(errno));
    }

    if (gps_group != NULL)
    {
       result = chown (global_loc_api_resp_q_path, -1, gps_group->gr_gid);
       if (result != 0)
       {
          LOC_LOGE("chown for pipe failed, pipe %s, gid = %d, result = %d, error = %s\n",
                   global_loc_api_resp_q_path,
                   gps_group->gr_gid, result, strerror(errno));
       }
    }

    quipc_msgqid = loc_eng_dmn_conn_glue_msgget(global_quipc_ctrl_q_path, O_RDWR);
    msapm_msgqid = loc_eng_dmn_conn_glue_msgget(global_msapm_ctrl_q_path , O_RDWR);
    msapu_msgqid = loc_eng_dmn_conn_glue_msgget(global_msapu_ctrl_q_path , O_RDWR);

    LOC_LOGD("%s:%d] loc_api_server_msgqid = %d\n", __func__, __LINE__, loc_api_server_msgqid);
    return 0;
}

static int loc_api_server_proc_pre(void *context)
{
    return 0;
}

static int loc_api_server_proc(void *context)
{
    int length, sz;
    int result = 0;
    static int cnt = 0;
    struct ctrl_msgbuf * p_cmsgbuf;
    struct ctrl_msgbuf cmsg_resp;

    sz = sizeof(struct ctrl_msgbuf) + 256;
    p_cmsgbuf = (struct ctrl_msgbuf *) malloc(sz);

    if (!p_cmsgbuf) {
        LOC_LOGE("%s:%d] Out of memory\n", __func__, __LINE__);
        return -1;
    }

    cnt ++;
    LOC_LOGD("%s:%d] %d listening on %s...\n", __func__, __LINE__, cnt, (char *) context);
    length = loc_eng_dmn_conn_glue_msgrcv(loc_api_server_msgqid, p_cmsgbuf, sz);
    if (length <= 0) {
        free(p_cmsgbuf);
        LOC_LOGE("%s:%d] fail receiving msg from gpsone_daemon, retry later\n", __func__, __LINE__);
        usleep(1000);
        return 0;
    }

    LOC_LOGD("%s:%d] received ctrl_type = %d\n", __func__, __LINE__, p_cmsgbuf->ctrl_type);
    switch(p_cmsgbuf->ctrl_type) {
        case GPSONE_LOC_API_IF_REQUEST:
            result = loc_eng_dmn_conn_loc_api_server_if_request_handler(p_cmsgbuf, length);
            break;

        case GPSONE_LOC_API_IF_RELEASE:
            result = loc_eng_dmn_conn_loc_api_server_if_release_handler(p_cmsgbuf, length);
            break;

        case GPSONE_UNBLOCK:
            LOC_LOGD("%s:%d] GPSONE_UNBLOCK\n", __func__, __LINE__);
            break;

        default:
            LOC_LOGE("%s:%d] unsupported ctrl_type = %d\n",
                __func__, __LINE__, p_cmsgbuf->ctrl_type);
            break;
    }

    free(p_cmsgbuf);
    return 0;
}

static int loc_api_server_proc_post(void *context)
{
    LOC_LOGD("%s:%d]\n", __func__, __LINE__);
    loc_eng_dmn_conn_glue_msgremove( global_loc_api_q_path, loc_api_server_msgqid);
    loc_eng_dmn_conn_glue_msgremove( global_loc_api_resp_q_path, loc_api_resp_msgqid);
    loc_eng_dmn_conn_glue_msgremove( global_quipc_ctrl_q_path, quipc_msgqid);
    loc_eng_dmn_conn_glue_msgremove( global_msapm_ctrl_q_path, msapm_msgqid);
    loc_eng_dmn_conn_glue_msgremove( global_msapu_ctrl_q_path, msapu_msgqid);
    return 0;
}

static int loc_eng_dmn_conn_unblock_proc(void)
{
    struct ctrl_msgbuf cmsgbuf;
    cmsgbuf.ctrl_type = GPSONE_UNBLOCK;
    LOC_LOGD("%s:%d]\n", __func__, __LINE__);
    loc_eng_dmn_conn_glue_msgsnd(loc_api_server_msgqid, & cmsgbuf, sizeof(cmsgbuf));
    return 0;
}

static struct loc_eng_dmn_conn_thelper thelper;

int loc_eng_dmn_conn_loc_api_server_launch(thelper_create_thread   create_thread_cb,
    const char * loc_api_q_path, const char * resp_q_path, void *agps_handle)
{
    int result;

    loc_api_handle = agps_handle;

    if (loc_api_q_path) global_loc_api_q_path = loc_api_q_path;
    if (resp_q_path)    global_loc_api_resp_q_path = resp_q_path;

    result = loc_eng_dmn_conn_launch_thelper( &thelper,
        loc_api_server_proc_init,
        loc_api_server_proc_pre,
        loc_api_server_proc,
        loc_api_server_proc_post,
        create_thread_cb,
        (char *) global_loc_api_q_path);
    if (result != 0) {
        LOC_LOGE("%s:%d]\n", __func__, __LINE__);
        return -1;
    }
    return 0;
}

int loc_eng_dmn_conn_loc_api_server_unblock(void)
{
    loc_eng_dmn_conn_unblock_thelper(&thelper);
    loc_eng_dmn_conn_unblock_proc();
    return 0;
}

int loc_eng_dmn_conn_loc_api_server_join(void)
{
    loc_eng_dmn_conn_join_thelper(&thelper);
    return 0;
}

int loc_eng_dmn_conn_loc_api_server_data_conn(int sender_id, int status) {
  struct ctrl_msgbuf cmsgbuf;
  LOC_LOGD("%s:%d] quipc_msgqid = %d\n", __func__, __LINE__, quipc_msgqid);
  cmsgbuf.ctrl_type = GPSONE_LOC_API_RESPONSE;
  cmsgbuf.cmsg.cmsg_response.result = status;
  switch (sender_id) {
    case LOC_ENG_IF_REQUEST_SENDER_ID_QUIPC: {
      LOC_LOGD("%s:%d] sender_id = LOC_ENG_IF_REQUEST_SENDER_ID_QUIPC", __func__, __LINE__);
      if (loc_eng_dmn_conn_glue_msgsnd(quipc_msgqid, & cmsgbuf, sizeof(struct ctrl_msgbuf)) < 0) {
        LOC_LOGD("%s:%d] error! conn_glue_msgsnd failed\n", __func__, __LINE__);
        return -1;
      }
      break;
    }
    case LOC_ENG_IF_REQUEST_SENDER_ID_MSAPM: {
      LOC_LOGD("%s:%d] sender_id = LOC_ENG_IF_REQUEST_SENDER_ID_MSAPM", __func__, __LINE__);
      if (loc_eng_dmn_conn_glue_msgsnd(msapm_msgqid, & cmsgbuf, sizeof(struct ctrl_msgbuf)) < 0) {
        LOC_LOGD("%s:%d] error! conn_glue_msgsnd failed\n", __func__, __LINE__);
        return -1;
      }
      break;
    }
    case LOC_ENG_IF_REQUEST_SENDER_ID_MSAPU: {
      LOC_LOGD("%s:%d] sender_id = LOC_ENG_IF_REQUEST_SENDER_ID_MSAPU", __func__, __LINE__);
      if (loc_eng_dmn_conn_glue_msgsnd(msapu_msgqid, & cmsgbuf, sizeof(struct ctrl_msgbuf)) < 0) {
        LOC_LOGD("%s:%d] error! conn_glue_msgsnd failed\n", __func__, __LINE__);
        return -1;
      }
      break;
    }
    case LOC_ENG_IF_REQUEST_SENDER_ID_GPSONE_DAEMON: {
      LOC_LOGD("%s:%d] sender_id = LOC_ENG_IF_REQUEST_SENDER_ID_GPSONE_DAEMON", __func__, __LINE__);
      if (loc_eng_dmn_conn_glue_msgsnd(loc_api_resp_msgqid, & cmsgbuf, sizeof(struct ctrl_msgbuf)) < 0) {
        LOC_LOGD("%s:%d] error! conn_glue_msgsnd failed\n", __func__, __LINE__);
        return -1;
      }
      break;
    }
    default: {
      LOC_LOGD("%s:%d] invalid sender ID!", __func__, __LINE__);
    }
  }
  return 0;
}

