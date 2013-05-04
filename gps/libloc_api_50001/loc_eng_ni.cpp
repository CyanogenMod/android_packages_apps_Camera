/* Copyright (c) 2009-2012, The Linux Foundation. All rights reserved.
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

#define LOG_NDDEBUG 0
#define LOG_TAG "LocSvc_eng"

#include <stdio.h>
#include <stdlib.h>
#include <sys/time.h>
#include <pthread.h>
#include <errno.h>
#include <string.h>
#include <ctype.h>
#include <unistd.h>
#include <time.h>

#include <loc_eng.h>

#include "log_util.h"

/*=============================================================================
 *
 *                             DATA DECLARATION
 *
 *============================================================================*/

/*=============================================================================
 *
 *                             FUNCTION DECLARATIONS
 *
 *============================================================================*/
static void* ni_thread_proc(void *args);

/*===========================================================================

FUNCTION loc_eng_ni_request_handler

DESCRIPTION
   Displays the NI request and awaits user input. If a previous request is
   in session, it is ignored.

RETURN VALUE
   none

===========================================================================*/
void loc_eng_ni_request_handler(loc_eng_data_s_type &loc_eng_data,
                            const GpsNiNotification *notif,
                            const void* passThrough)
{
    ENTRY_LOG();
    char lcs_addr[32]; // Decoded LCS address for UMTS CP NI
    loc_eng_ni_data_s_type* loc_eng_ni_data_p = &loc_eng_data.loc_eng_ni_data;

    if (NULL == loc_eng_data.ni_notify_cb) {
        EXIT_LOG(%s, "loc_eng_ni_init hasn't happened yet.");
        return;
    }

    /* If busy, use default or deny */
    if (NULL != loc_eng_ni_data_p->rawRequest)
    {
        /* XXX Consider sending a NO RESPONSE reply or queue the request */
        LOC_LOGW("loc_eng_ni_request_handler, notification in progress, new NI request ignored, type: %d",
                 notif->ni_type);
        if (NULL != passThrough) {
            free((void*)passThrough);
        }
    }
    else {
        /* Save request */
        loc_eng_ni_data_p->rawRequest = (void*)passThrough;

        /* Fill in notification */
        ((GpsNiNotification*)notif)->notification_id = loc_eng_ni_data_p->reqID;

        if (notif->notify_flags == GPS_NI_PRIVACY_OVERRIDE)
        {
            loc_eng_mute_one_session(loc_eng_data);
        }

        /* Log requestor ID and text for debugging */
        LOC_LOGI("Notification: notif_type: %d, timeout: %d, default_resp: %d", notif->ni_type, notif->timeout, notif->default_response);
        LOC_LOGI("              requestor_id: %s (encoding: %d)", notif->requestor_id, notif->requestor_id_encoding);
        LOC_LOGI("              text: %s text (encoding: %d)", notif->text, notif->text_encoding);
        if (notif->extras[0])
        {
            LOC_LOGI("              extras: %s", notif->extras);
        }

        /* For robustness, spawn a thread at this point to timeout to clear up the notification status, even though
         * the OEM layer in java does not do so.
         **/
        loc_eng_ni_data_p->respTimeLeft = 5 + (notif->timeout != 0 ? notif->timeout : LOC_NI_NO_RESPONSE_TIME);
        LOC_LOGI("Automatically sends 'no response' in %d seconds (to clear status)\n", loc_eng_ni_data_p->respTimeLeft);

        int rc = 0;
        rc = pthread_create(&loc_eng_ni_data_p->thread, NULL, ni_thread_proc, &loc_eng_data);
        if (rc)
        {
            LOC_LOGE("Loc NI thread is not created.\n");
        }
        rc = pthread_detach(loc_eng_ni_data_p->thread);
        if (rc)
        {
            LOC_LOGE("Loc NI thread is not detached.\n");
        }

        CALLBACK_LOG_CALLFLOW("ni_notify_cb - id", %d, notif->notification_id);
        loc_eng_data.ni_notify_cb((GpsNiNotification*)notif);
    }
    EXIT_LOG(%s, VOID_RET);
}

/*===========================================================================

FUNCTION ni_thread_proc

===========================================================================*/
static void* ni_thread_proc(void *args)
{
    ENTRY_LOG();

    loc_eng_data_s_type* loc_eng_data_p = (loc_eng_data_s_type*)args;
    loc_eng_ni_data_s_type* loc_eng_ni_data_p = &loc_eng_data_p->loc_eng_ni_data;
    int rc = 0;          /* return code from pthread calls */

    struct timeval present_time;
    struct timespec expire_time;

    LOC_LOGD("Starting Loc NI thread...\n");
    pthread_mutex_lock(&loc_eng_ni_data_p->tLock);
    /* Calculate absolute expire time */
    gettimeofday(&present_time, NULL);
    expire_time.tv_sec  = present_time.tv_sec + loc_eng_ni_data_p->respTimeLeft;
    expire_time.tv_nsec = present_time.tv_usec * 1000;
    LOC_LOGD("ni_thread_proc-Time out set for abs time %ld with delay %d sec\n",
             (long) expire_time.tv_sec, loc_eng_ni_data_p->respTimeLeft );

    while (!loc_eng_ni_data_p->respRecvd)
    {
        rc = pthread_cond_timedwait(&loc_eng_ni_data_p->tCond,
                                    &loc_eng_ni_data_p->tLock,
                                    &expire_time);
        if (rc == ETIMEDOUT)
        {
            loc_eng_ni_data_p->resp = GPS_NI_RESPONSE_NORESP;
            LOC_LOGD("ni_thread_proc-Thread time out after valting for specified time. Ret Val %d\n",rc );
            break;
        }
    }
    LOC_LOGD("ni_thread_proc-Java layer has sent us a user response and return value from "
             "pthread_cond_timedwait = %d\n",rc );
    loc_eng_ni_data_p->respRecvd = FALSE; /* Reset the user response flag for the next session*/

    // adding this check to support modem restart, in which case, we need the thread
    // to exit without calling sending data to loc_eng_msg_q. We made sure that
    // rawRequest is NULL in loc_eng_ni_reset_on_engine_restart()
    loc_eng_msg_inform_ni_response *msg = NULL;

    if (NULL != loc_eng_ni_data_p->rawRequest) {
        loc_eng_data_s_type *loc_eng_data_p = (loc_eng_data_s_type*)args;
        msg = new loc_eng_msg_inform_ni_response(loc_eng_data_p,
                                                 loc_eng_ni_data_p->resp,
                                                 loc_eng_ni_data_p->rawRequest);
        loc_eng_ni_data_p->rawRequest = NULL;
    }
    pthread_mutex_unlock(&loc_eng_ni_data_p->tLock);

    loc_eng_ni_data_p->respTimeLeft = 0;
    loc_eng_ni_data_p->reqID++;

    if (NULL != msg) {
        loc_eng_msg_sender(loc_eng_data_p, msg);
    }

    EXIT_LOG(%s, VOID_RET);
    return NULL;
}

void loc_eng_ni_reset_on_engine_restart(loc_eng_data_s_type &loc_eng_data)
{
    ENTRY_LOG();
    loc_eng_ni_data_s_type* loc_eng_ni_data_p = &loc_eng_data.loc_eng_ni_data;

    if (NULL == loc_eng_data.ni_notify_cb) {
        EXIT_LOG(%s, "loc_eng_ni_init hasn't happened yet.");
        return;
    }

    // only if modem has requested but then died.
    if (NULL != loc_eng_ni_data_p->rawRequest) {
        free(loc_eng_ni_data_p->rawRequest);
        loc_eng_ni_data_p->rawRequest = NULL;

        pthread_mutex_lock(&loc_eng_ni_data_p->tLock);
        // the goal is to wake up ni_thread_proc
        // and let it exit.
        loc_eng_ni_data_p->respRecvd = TRUE;
        pthread_cond_signal(&loc_eng_ni_data_p->tCond);
        pthread_mutex_unlock(&loc_eng_ni_data_p->tLock);
    }

    EXIT_LOG(%s, VOID_RET);
}

/*===========================================================================
FUNCTION    loc_eng_ni_init

DESCRIPTION
   This function initializes the NI interface

DEPENDENCIES
   NONE

RETURN VALUE
   None

SIDE EFFECTS
   N/A

===========================================================================*/
void loc_eng_ni_init(loc_eng_data_s_type &loc_eng_data, GpsNiCallbacks *callbacks)
{
    ENTRY_LOG_CALLFLOW();

    if(callbacks == NULL)
        EXIT_LOG(%s, "loc_eng_ni_init: failed, cb is NULL");
    else if (NULL == callbacks->notify_cb) {
        EXIT_LOG(%s, "loc_eng_ni_init: failed, no cb.");
    } else if (NULL != loc_eng_data.ni_notify_cb) {
        EXIT_LOG(%s, "loc_eng_ni_init: already inited.");
    } else {
        loc_eng_ni_data_s_type* loc_eng_ni_data_p = &loc_eng_data.loc_eng_ni_data;
        loc_eng_ni_data_p->respTimeLeft = 0;
        loc_eng_ni_data_p->respRecvd = FALSE;
        loc_eng_ni_data_p->rawRequest = NULL;
        loc_eng_ni_data_p->reqID = 0;
        pthread_cond_init(&loc_eng_ni_data_p->tCond, NULL);
        pthread_mutex_init(&loc_eng_ni_data_p->tLock, NULL);

        loc_eng_data.ni_notify_cb = callbacks->notify_cb;
        EXIT_LOG(%s, VOID_RET);
    }
}

/*===========================================================================
FUNCTION    loc_eng_ni_respond

DESCRIPTION
   This function receives user response from upper layer framework

DEPENDENCIES
   NONE

RETURN VALUE
   None

SIDE EFFECTS
   N/A

===========================================================================*/
void loc_eng_ni_respond(loc_eng_data_s_type &loc_eng_data,
                        int notif_id, GpsUserResponseType user_response)
{
    ENTRY_LOG_CALLFLOW();
    loc_eng_ni_data_s_type* loc_eng_ni_data_p = &loc_eng_data.loc_eng_ni_data;

    if (NULL == loc_eng_data.ni_notify_cb) {
        EXIT_LOG(%s, "loc_eng_ni_init hasn't happened yet.");
        return;
    }

    if (notif_id == loc_eng_ni_data_p->reqID &&
        NULL != loc_eng_ni_data_p->rawRequest)
    {
        LOC_LOGI("loc_eng_ni_respond: send user response %d for notif %d", user_response, notif_id);
        pthread_mutex_lock(&loc_eng_ni_data_p->tLock);
        loc_eng_ni_data_p->resp = user_response;
        loc_eng_ni_data_p->respRecvd = TRUE;
        pthread_cond_signal(&loc_eng_ni_data_p->tCond);
        pthread_mutex_unlock(&loc_eng_ni_data_p->tLock);
    }
    else {
        LOC_LOGE("loc_eng_ni_respond: reqID %d and notif_id %d mismatch or rawRequest %p, response: %d",
                 loc_eng_ni_data_p->reqID, notif_id, loc_eng_ni_data_p->rawRequest, user_response);
    }

    EXIT_LOG(%s, VOID_RET);
}
