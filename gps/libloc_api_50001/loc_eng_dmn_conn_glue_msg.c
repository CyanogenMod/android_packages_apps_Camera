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
#include <linux/stat.h>
#include <fcntl.h>

#include <linux/types.h>

#include "log_util.h"

#include "loc_eng_dmn_conn_glue_msg.h"
#include "loc_eng_dmn_conn_handler.h"

/*===========================================================================
FUNCTION    loc_eng_dmn_conn_glue_msgget

DESCRIPTION
   This function get a message queue

   q_path - name path of the message queue
   mode -

DEPENDENCIES
   None

RETURN VALUE
   message queue id

SIDE EFFECTS
   N/A

===========================================================================*/
int loc_eng_dmn_conn_glue_msgget(const char * q_path, int mode)
{
    int msgqid;
    msgqid = loc_eng_dmn_conn_glue_pipeget(q_path, mode);
    return msgqid;
}

/*===========================================================================
FUNCTION    loc_eng_dmn_conn_glue_msgremove

DESCRIPTION
   remove a message queue

   q_path - name path of the message queue
   msgqid - message queue id

DEPENDENCIES
   None

RETURN VALUE
   0: success or negative value for failure

SIDE EFFECTS
   N/A

===========================================================================*/
int loc_eng_dmn_conn_glue_msgremove(const char * q_path, int msgqid)
{
    int result;
    result = loc_eng_dmn_conn_glue_piperemove(q_path, msgqid);
    return result;
}

/*===========================================================================
FUNCTION    loc_eng_dmn_conn_glue_msgsnd

DESCRIPTION
   Send a message

   msgqid - message queue id
   msgp - pointer to the message to be sent
   msgsz - size of the message

DEPENDENCIES
   None

RETURN VALUE
   number of bytes sent out or negative value for failure

SIDE EFFECTS
   N/A

===========================================================================*/
int loc_eng_dmn_conn_glue_msgsnd(int msgqid, const void * msgp, size_t msgsz)
{
    int result;
    struct ctrl_msgbuf *pmsg = (struct ctrl_msgbuf *) msgp;
    pmsg->msgsz = msgsz;

    result = loc_eng_dmn_conn_glue_pipewrite(msgqid, msgp, msgsz);
    if (result != (int) msgsz) {
        LOC_LOGE("%s:%d] pipe broken %d, msgsz = %d\n", __func__, __LINE__, result, (int) msgsz);
        return -1;
    }

    return result;
}

/*===========================================================================
FUNCTION    loc_eng_dmn_conn_glue_msgrcv

DESCRIPTION
   receive a message

   msgqid - message queue id
   msgp - pointer to the buffer to hold the message
   msgsz - size of the buffer

DEPENDENCIES
   None

RETURN VALUE
   number of bytes received or negative value for failure

SIDE EFFECTS
   N/A

===========================================================================*/
int loc_eng_dmn_conn_glue_msgrcv(int msgqid, void *msgp, size_t msgbufsz)
{
    int result;
    struct ctrl_msgbuf *pmsg = (struct ctrl_msgbuf *) msgp;

    result = loc_eng_dmn_conn_glue_piperead(msgqid, &(pmsg->msgsz), sizeof(pmsg->msgsz));
    if (result != sizeof(pmsg->msgsz)) {
        LOC_LOGE("%s:%d] pipe broken %d\n", __func__, __LINE__, result);
        return -1;
    }

    if (msgbufsz < pmsg->msgsz) {
        LOC_LOGE("%s:%d] msgbuf is too small %d < %d\n", __func__, __LINE__, (int) msgbufsz, (int) pmsg->msgsz);
        return -1;
    }

    result = loc_eng_dmn_conn_glue_piperead(msgqid, (uint8_t *) msgp + sizeof(pmsg->msgsz), pmsg->msgsz - sizeof(pmsg->msgsz));
    if (result != (int) (pmsg->msgsz - sizeof(pmsg->msgsz))) {
        LOC_LOGE("%s:%d] pipe broken %d, msgsz = %d\n", __func__, __LINE__, result, (int) pmsg->msgsz);
        return -1;
    }

    return pmsg->msgsz;
}

/*===========================================================================
FUNCTION    loc_eng_dmn_conn_glue_msgunblock

DESCRIPTION
   unblock a message queue

   msgqid - message queue id

DEPENDENCIES
   None

RETURN VALUE
   0: success

SIDE EFFECTS
   N/A

===========================================================================*/
int loc_eng_dmn_conn_glue_msgunblock(int msgqid)
{
    return loc_eng_dmn_conn_glue_pipeunblock(msgqid);
}

/*===========================================================================
FUNCTION    loc_eng_dmn_conn_glue_msgflush

DESCRIPTION
   flush out the message in a queue

   msgqid - message queue id

DEPENDENCIES
   None

RETURN VALUE
   number of bytes that are flushed out.

SIDE EFFECTS
   N/A

===========================================================================*/
int loc_eng_dmn_conn_glue_msgflush(int msgqid)
{
    int length;
    char buf[128];

    do {
        length = loc_eng_dmn_conn_glue_piperead(msgqid, buf, 128);
        LOC_LOGD("%s:%d] %s\n", __func__, __LINE__, buf);
    } while(length);
    return length;
}

