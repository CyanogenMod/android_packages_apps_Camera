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

#include <loc_eng.h>
#include <loc_eng_msg.h>
#include "log_util.h"


/*===========================================================================
FUNCTION    loc_eng_xtra_init

DESCRIPTION
   Initialize XTRA module.

DEPENDENCIES
   N/A

RETURN VALUE
   0: success

SIDE EFFECTS
   N/A

===========================================================================*/
int loc_eng_xtra_init (loc_eng_data_s_type &loc_eng_data,
                       GpsXtraCallbacks* callbacks)
{
    int ret_val = -1;
    loc_eng_xtra_data_s_type *xtra_module_data_ptr;

    if(callbacks == NULL)
        LOC_LOGE("loc_eng_xtra_init: failed, cb is NULL");
    else {
        xtra_module_data_ptr = &loc_eng_data.xtra_module_data;
        xtra_module_data_ptr->download_request_cb = callbacks->download_request_cb;
        ret_val = 0;
    }
    return ret_val;
}

/*===========================================================================
FUNCTION    loc_eng_xtra_inject_data

DESCRIPTION
   Injects XTRA file into the engine but buffers the data if engine is busy.

DEPENDENCIES
   N/A

RETURN VALUE
   0: success
   >0: failure

SIDE EFFECTS
   N/A

===========================================================================*/
int loc_eng_xtra_inject_data(loc_eng_data_s_type &loc_eng_data,
                             char* data, int length)
{
    loc_eng_msg_inject_xtra_data *msg(new loc_eng_msg_inject_xtra_data(&loc_eng_data,
                                                                       data, length));
    loc_eng_msg_sender(&loc_eng_data, msg);

    return 0;
}
