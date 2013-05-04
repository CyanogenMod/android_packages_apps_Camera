/* Copyright (c) 2011, The Linux Foundation. All rights reserved.
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
 *     * Neither the name of The Linux Foundation nor the names of its
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
#include <fcntl.h>
#include "loc_eng_msg.h"
#include "loc_eng_dmn_conn_glue_msg.h"

#ifdef _ANDROID_

#define LOC_ENG_MSG_REQ_Q_PATH "/data/misc/gpsone_d/loc_eng_msg_req_q"

#else

#define LOC_ENG_MSG_REQ_Q_PATH "/tmp/loc_eng_msg_req_q"

#endif

int loc_eng_msgget(int * p_req_msgq)
{
    * p_req_msgq = loc_eng_dmn_conn_glue_msgget(LOC_ENG_MSG_REQ_Q_PATH, O_RDWR);
    return 0;
}

int loc_eng_msgremove(int req_msgq)
{
    loc_eng_dmn_conn_glue_piperemove(LOC_ENG_MSG_REQ_Q_PATH, req_msgq);
    return 0;
}

int loc_eng_msgsnd(int msgqid, void * msgp)
{
    int ret = loc_eng_dmn_conn_glue_pipewrite(msgqid, msgp, sizeof(void*));
    return ret;
}

int loc_eng_msgsnd_raw(int msgqid, void * msgp, unsigned int msgsz)
{
    int result;

    struct msgbuf * pmsg = (struct msgbuf *) msgp;

    if (msgsz < sizeof(struct msgbuf)) {
        LOC_LOGE("%s:%d] msgbuf is too small %d\n", __func__, __LINE__, msgsz);
        return -1;
    }

    pmsg->msgsz = msgsz;

    result = loc_eng_dmn_conn_glue_pipewrite(msgqid, msgp, msgsz);
    if (result != (int) msgsz) {
        LOC_LOGE("%s:%d] pipe broken %d, msgsz = %d\n", __func__, __LINE__, result, (int) msgsz);
        return -1;
    }
    return result;
}

int loc_eng_msgrcv(int msgqid, void ** msgp)
{
    int ret = loc_eng_dmn_conn_glue_piperead(msgqid, msgp, sizeof(void*));
    return ret;
}

int loc_eng_msgrcv_raw(int msgqid, void *msgp, unsigned int msgsz)
{
    int result;
    struct msgbuf * pmsg = (struct msgbuf *) msgp;

    if (msgsz < sizeof(struct msgbuf)) {
        LOC_LOGE("%s:%d] msgbuf is too small %d\n", __func__, __LINE__, msgsz);
        return -1;
    }

    result = loc_eng_dmn_conn_glue_piperead(msgqid, msgp, sizeof(struct msgbuf));
    if (result != sizeof(struct msgbuf)) {
        LOC_LOGE("%s:%d] pipe broken %d\n", __func__, __LINE__, result);
        return -1;
    }

    if (msgsz < pmsg->msgsz) {
        LOC_LOGE("%s:%d] msgbuf is too small %d < %d\n", __func__, __LINE__, (int) msgsz, (int) pmsg->msgsz);
        return -1;
    }

    if (pmsg->msgsz > sizeof(struct msgbuf)) {
        /* there is msg body */
        msgp += sizeof(struct msgbuf);

        result = loc_eng_dmn_conn_glue_piperead(msgqid, msgp, pmsg->msgsz - sizeof(struct msgbuf));

        if (result != (int) (pmsg->msgsz - sizeof(struct msgbuf))) {
            LOC_LOGE("%s:%d] pipe broken %d, msgid = %p, msgsz = %d\n", __func__, __LINE__, result,
                    (pmsg->msgid), (int) pmsg->msgsz);
            return -1;
        }
    }

    return pmsg->msgsz;
}

int loc_eng_msgflush(int msgqid)
{
    return loc_eng_dmn_conn_glue_msgflush(msgqid);
}

int loc_eng_msgunblock(int msgqid)
{
    return loc_eng_dmn_conn_glue_pipeunblock(msgqid);
}
