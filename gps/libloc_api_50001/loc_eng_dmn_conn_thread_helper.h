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
#ifndef __LOC_ENG_DMN_CONN_THREAD_HELPER_H__
#define __LOC_ENG_DMN_CONN_THREAD_HELPER_H__

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

#include <pthread.h>

struct loc_eng_dmn_conn_thelper {
    unsigned char   thread_exit;
    unsigned char   thread_ready;
    pthread_cond_t  thread_cond;
    pthread_mutex_t thread_mutex;
    pthread_t       thread_id;
    void *          thread_context;
    int             (*thread_proc_init) (void * context);
    int             (*thread_proc_pre)  (void * context);
    int             (*thread_proc)      (void * context);
    int             (*thread_proc_post) (void * context);
};

typedef pthread_t (* thelper_create_thread)(const char* name, void (*start)(void *), void* arg);
int loc_eng_dmn_conn_launch_thelper(struct loc_eng_dmn_conn_thelper * thelper,
    int (*thread_proc_init) (void * context),
    int (*thread_proc_pre)  (void * context),
    int (*thread_proc)      (void * context),
    int (*thread_proc_post) (void * context),
    thelper_create_thread   create_thread_cb,
    void * context);

int loc_eng_dmn_conn_unblock_thelper(struct loc_eng_dmn_conn_thelper * thelper);
int loc_eng_dmn_conn_join_thelper(struct loc_eng_dmn_conn_thelper * thelper);

/* if only need to use signal */
int thelper_signal_init(struct loc_eng_dmn_conn_thelper * thelper);
int thelper_signal_destroy(struct loc_eng_dmn_conn_thelper * thelper);
int thelper_signal_wait(struct loc_eng_dmn_conn_thelper * thelper);
int thelper_signal_ready(struct loc_eng_dmn_conn_thelper * thelper);
int thelper_signal_block(struct loc_eng_dmn_conn_thelper * thelper);

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* __LOC_ENG_DMN_CONN_THREAD_HELPER_H__ */
