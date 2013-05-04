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
#ifndef LOC_ENG_DMN_CONN_GLUE_PIPE_H
#define LOC_ENG_DMN_CONN_GLUE_PIPE_H

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

#include <linux/types.h>

int loc_eng_dmn_conn_glue_pipeget(const char * pipe_name, int mode);
int loc_eng_dmn_conn_glue_piperemove(const char * pipe_name, int fd);
int loc_eng_dmn_conn_glue_pipewrite(int fd, const void * buf, size_t sz);
int loc_eng_dmn_conn_glue_piperead(int fd, void * buf, size_t sz);

int loc_eng_dmn_conn_glue_pipeflush(int fd);
int loc_eng_dmn_conn_glue_pipeunblock(int fd);

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* LOC_ENG_DMN_CONN_GLUE_PIPE_H */
