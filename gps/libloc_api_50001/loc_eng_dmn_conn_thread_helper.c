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
#include <stdio.h>

#include "log_util.h"
#include "loc_eng_dmn_conn_thread_helper.h"

/*===========================================================================
FUNCTION    thelper_signal_init

DESCRIPTION
   This function will initialize the conditional variable resources.

   thelper - thelper instance

DEPENDENCIES
   None

RETURN VALUE
   0: success or negative value for failure

SIDE EFFECTS
   N/A

===========================================================================*/
int thelper_signal_init(struct loc_eng_dmn_conn_thelper * thelper)
{
    int result;
    thelper->thread_exit  = 0;
    thelper->thread_ready = 0;
    result = pthread_cond_init( &thelper->thread_cond, NULL);
    if (result) {
        return result;
    }

    result = pthread_mutex_init(&thelper->thread_mutex, NULL);
    if (result) {
        pthread_cond_destroy(&thelper->thread_cond);
    }
    return result;
}

/*===========================================================================
FUNCTION

DESCRIPTION
   This function will destroy the conditional variable resources

    thelper - pointer to thelper instance

DEPENDENCIES
   None

RETURN VALUE
   0: success or negative value for failure

SIDE EFFECTS
   N/A

===========================================================================*/
int thelper_signal_destroy(struct loc_eng_dmn_conn_thelper * thelper)
{
    int result, ret_result = 0;
    result = pthread_cond_destroy( &thelper->thread_cond);
    if (result) {
        ret_result = result;
    }

    result = pthread_mutex_destroy(&thelper->thread_mutex);
    if (result) {
        ret_result = result;
    }

    return ret_result;
}

/*===========================================================================
FUNCTION    thelper_signal_wait

DESCRIPTION
   This function will be blocked on the conditional variable until thelper_signal_ready
   is called

    thelper - pointer to thelper instance

DEPENDENCIES
   None

RETURN VALUE
   0: success or negative value for failure

SIDE EFFECTS
   N/A

===========================================================================*/
int thelper_signal_wait(struct loc_eng_dmn_conn_thelper * thelper)
{
    int result = 0;

    pthread_mutex_lock(&thelper->thread_mutex);
    if (!thelper->thread_ready && !thelper->thread_exit) {
        result = pthread_cond_wait(&thelper->thread_cond, &thelper->thread_mutex);
    }

    if (thelper->thread_exit) {
        result = -1;
    }
    pthread_mutex_unlock(&thelper->thread_mutex);

    return result;
}

/*===========================================================================
FUNCTION     thelper_signal_ready

DESCRIPTION
   This function will wake up the conditional variable

    thelper - pointer to thelper instance

DEPENDENCIES
   None

RETURN VALUE
   0: success or negative value for failure

SIDE EFFECTS
   N/A

===========================================================================*/
int thelper_signal_ready(struct loc_eng_dmn_conn_thelper * thelper)
{
    int result;

    LOC_LOGD("%s:%d] 0x%lx\n", __func__, __LINE__, (long) thelper);

    pthread_mutex_lock(&thelper->thread_mutex);
    thelper->thread_ready = 1;
    result = pthread_cond_signal(&thelper->thread_cond);
    pthread_mutex_unlock(&thelper->thread_mutex);

    return result;
}

/*===========================================================================
FUNCTION     thelper_signal_block

DESCRIPTION
   This function will set the thread ready to 0 to block the thelper_signal_wait

    thelper - pointer to thelper instance

DEPENDENCIES
   None

RETURN VALUE
   if thread_ready is set

SIDE EFFECTS
   N/A

===========================================================================*/
int thelper_signal_block(struct loc_eng_dmn_conn_thelper * thelper)
{
    int result = thelper->thread_ready;

    LOC_LOGD("%s:%d] 0x%lx\n", __func__, __LINE__, (long) thelper);

    pthread_mutex_lock(&thelper->thread_mutex);
    thelper->thread_ready = 0;
    pthread_mutex_unlock(&thelper->thread_mutex);

    return result;
}

/*===========================================================================
FUNCTION    thelper_main

DESCRIPTION
   This function is the main thread. It will be launched as a child thread

    data - pointer to the instance

DEPENDENCIES
   None

RETURN VALUE
   NULL

SIDE EFFECTS
   N/A

===========================================================================*/
static void * thelper_main(void *data)
{
    int result = 0;
    struct loc_eng_dmn_conn_thelper * thelper = (struct loc_eng_dmn_conn_thelper *) data;

    if (thelper->thread_proc_init) {
        result = thelper->thread_proc_init(thelper->thread_context);
        if (result < 0) {
            thelper->thread_exit = 1;
            thelper_signal_ready(thelper);
            LOC_LOGE("%s:%d] error: 0x%lx\n", __func__, __LINE__, (long) thelper);
            return NULL;
        }
    }

    thelper_signal_ready(thelper);

    if (thelper->thread_proc_pre) {
        result = thelper->thread_proc_pre(thelper->thread_context);
        if (result < 0) {
            thelper->thread_exit = 1;
            LOC_LOGE("%s:%d] error: 0x%lx\n", __func__, __LINE__, (long) thelper);
            return NULL;
        }
    }

    do {
        if (thelper->thread_proc) {
            result = thelper->thread_proc(thelper->thread_context);
            if (result < 0) {
                thelper->thread_exit = 1;
                LOC_LOGE("%s:%d] error: 0x%lx\n", __func__, __LINE__, (long) thelper);
            }
        }
    } while (thelper->thread_exit == 0);

    if (thelper->thread_proc_post) {
        result = thelper->thread_proc_post(thelper->thread_context);
    }

    if (result != 0) {
        LOC_LOGE("%s:%d] error: 0x%lx\n", __func__, __LINE__, (long) thelper);
    }
    return NULL;
}

static void thelper_main_2(void *data)
{
    thelper_main(data);
    return;
}


/*===========================================================================
FUNCTION    loc_eng_dmn_conn_launch_thelper

DESCRIPTION
   This function will initialize the thread context and launch the thelper_main

    thelper - pointer to thelper instance
    thread_proc_init - The initialization function pointer
    thread_proc_pre  - The function to call before task loop and after initialization
    thread_proc      - The task loop
    thread_proc_post - The function to call after the task loop
    context          - the context for the above four functions

DEPENDENCIES
   None

RETURN VALUE
   0: success or negative value for failure

SIDE EFFECTS
   N/A

===========================================================================*/
int loc_eng_dmn_conn_launch_thelper(struct loc_eng_dmn_conn_thelper * thelper,
    int (*thread_proc_init) (void * context),
    int (*thread_proc_pre) (void * context),
    int (*thread_proc) (void * context),
    int (*thread_proc_post) (void * context),
    thelper_create_thread   create_thread_cb,
    void * context)
{
    int result;

    thelper_signal_init(thelper);

    if (context) {
        thelper->thread_context    = context;
    }

    thelper->thread_proc_init  = thread_proc_init;
    thelper->thread_proc_pre   = thread_proc_pre;
    thelper->thread_proc       = thread_proc;
    thelper->thread_proc_post  = thread_proc_post;

    LOC_LOGD("%s:%d] 0x%lx call pthread_create\n", __func__, __LINE__, (long) thelper);
    if (create_thread_cb) {
        result = 0;
        thelper->thread_id = create_thread_cb("loc_eng_dmn_conn",
            thelper_main_2, (void *)thelper);
    } else {
        result = pthread_create(&thelper->thread_id, NULL,
            thelper_main, (void *)thelper);
    }

    if (result != 0) {
        LOC_LOGE("%s:%d] 0x%lx\n", __func__, __LINE__, (long) thelper);
        return -1;
    }

    LOC_LOGD("%s:%d] 0x%lx pthread_create done\n", __func__, __LINE__, (long) thelper);

    thelper_signal_wait(thelper);

    LOC_LOGD("%s:%d] 0x%lx pthread ready\n", __func__, __LINE__, (long) thelper);
    return thelper->thread_exit;
}

/*===========================================================================
FUNCTION    loc_eng_dmn_conn_unblock_thelper

DESCRIPTION
   This function unblocks thelper_main to release the thread

    thelper - pointer to thelper instance

DEPENDENCIES
   None

RETURN VALUE
   0: success

SIDE EFFECTS
   N/A

===========================================================================*/
int loc_eng_dmn_conn_unblock_thelper(struct loc_eng_dmn_conn_thelper * thelper)
{
    LOC_LOGD("%s:%d] 0x%lx\n", __func__, __LINE__, (long) thelper);
    thelper->thread_exit = 1;
    return 0;
}

/*===========================================================================
FUNCTION    loc_eng_dmn_conn_join_thelper

    thelper - pointer to thelper instance

DESCRIPTION
   This function will wait for the thread of thelper_main to finish

DEPENDENCIES
   None

RETURN VALUE
   0: success or negative value for failure

SIDE EFFECTS
   N/A

===========================================================================*/
int loc_eng_dmn_conn_join_thelper(struct loc_eng_dmn_conn_thelper * thelper)
{
    int result;

    LOC_LOGD("%s:%d] 0x%lx\n", __func__, __LINE__, (long) thelper);
    result = pthread_join(thelper->thread_id, NULL);
    if (result != 0) {
        LOC_LOGE("%s:%d] 0x%lx\n", __func__, __LINE__, (long) thelper);
    }
    LOC_LOGD("%s:%d] 0x%lx\n", __func__, __LINE__, (long) thelper);

    thelper_signal_destroy(thelper);

    return result;
}

