/* Copyright (c) 2011 The Linux Foundation. All rights reserved.
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

#ifndef __LOG_UTIL_H__
#define __LOG_UTIL_H__

#ifdef __cplusplus
extern "C"
{
#endif
/*=============================================================================
 *
 *                         LOC LOGGER TYPE DECLARATION
 *
 *============================================================================*/
/* LOC LOGGER */
typedef struct loc_logger_s
{
  unsigned long  DEBUG_LEVEL;
  unsigned long  TIMESTAMP;
} loc_logger_s_type;

/*=============================================================================
 *
 *                               EXTERNAL DATA
 *
 *============================================================================*/
extern loc_logger_s_type loc_logger;

// Logging Improvements
extern const char *loc_logger_boolStr[];

extern const char *boolStr[];
extern const char VOID_RET[];
extern const char FROM_AFW[];
extern const char TO_MODEM[];
extern const char FROM_MODEM[];
extern const char TO_AFW[];
extern const char EXIT_TAG[];
extern const char ENTRY_TAG[];
/*=============================================================================
 *
 *                        MODULE EXPORTED FUNCTIONS
 *
 *============================================================================*/
extern void loc_logger_init(unsigned long debug, unsigned long timestamp);
extern char* get_timestamp(char* str, unsigned long buf_size);


#include <utils/Log.h>

#ifndef DEBUG_DMN_LOC_API

/* LOGGING MACROS */
#define LOC_LOGE(...) ALOGE("E/"__VA_ARGS__)

#define LOC_LOGW(...) \
if (loc_logger.DEBUG_LEVEL >= 2) { ALOGE("W/"__VA_ARGS__); } \
else if (loc_logger.DEBUG_LEVEL <= 0) { ALOGW("W/"__VA_ARGS__); }

#define LOC_LOGI(...) \
if (loc_logger.DEBUG_LEVEL >= 3) { ALOGE("I/"__VA_ARGS__); } \
else if (loc_logger.DEBUG_LEVEL <= 0) { ALOGI("W/"__VA_ARGS__); }

#define LOC_LOGD(...) \
if (loc_logger.DEBUG_LEVEL >= 4) { ALOGE("D/"__VA_ARGS__); } \
else if (loc_logger.DEBUG_LEVEL <= 0) { ALOGD("W/"__VA_ARGS__); }

#define LOC_LOGV(...) \
if (loc_logger.DEBUG_LEVEL >= 5) { ALOGE("V/"__VA_ARGS__); } \
else if (loc_logger.DEBUG_LEVEL <= 0) { ALOGV("W/"__VA_ARGS__); }

#else /* DEBUG_DMN_LOC_API */

#define LOC_LOGE(...) ALOGE("E/"__VA_ARGS__)

#define LOC_LOGW(...) ALOGW("W/"__VA_ARGS__)

#define LOC_LOGI(...) ALOGI("I/"__VA_ARGS__)

#define LOC_LOGD(...) ALOGD("D/"__VA_ARGS__)

#define LOC_LOGV(...) ALOGV("V/"__VA_ARGS__)

#endif /* DEBUG_DMN_LOC_API */

/*=============================================================================
 *
 *                          LOGGING IMPROVEMENT MACROS
 *
 *============================================================================*/
#define LOG_(LOC_LOG, ID, WHAT, SPEC, VAL)                                    \
    do {                                                                      \
        if (loc_logger.TIMESTAMP) {                                           \
            char ts[32];                                                      \
            LOC_LOG("[%s] %s %s line %d " #SPEC,                              \
                     get_timestamp(ts, sizeof(ts)), ID, WHAT, __LINE__, VAL); \
        } else {                                                              \
            LOC_LOG("%s %s line %d " #SPEC,                                   \
                     ID, WHAT, __LINE__, VAL);                                \
        }                                                                     \
    } while(0)


#define LOG_I(ID, WHAT, SPEC, VAL) LOG_(LOC_LOGI, ID, WHAT, SPEC, VAL)
#define LOG_V(ID, WHAT, SPEC, VAL) LOG_(LOC_LOGV, ID, WHAT, SPEC, VAL)

#define ENTRY_LOG() LOG_V(ENTRY_TAG, __func__, %s, "")
#define EXIT_LOG(SPEC, VAL) LOG_V(EXIT_TAG, __func__, SPEC, VAL)


// Used for logging callflow from Android Framework
#define ENTRY_LOG_CALLFLOW() LOG_I(FROM_AFW, __func__, %s, "")
// Used for logging callflow to Modem
#define EXIT_LOG_CALLFLOW(SPEC, VAL) LOG_I(TO_MODEM, __func__, SPEC, VAL)
// Used for logging callflow from Modem(TO_MODEM, __func__, %s, "")
#define MODEM_LOG_CALLFLOW(SPEC, VAL) LOG_I(FROM_MODEM, __func__, SPEC, VAL)
// Used for logging callflow to Android Framework
#define CALLBACK_LOG_CALLFLOW(CB, SPEC, VAL) LOG_I(TO_AFW, CB, SPEC, VAL)

#ifdef __cplusplus
}
#endif

#endif // __LOG_UTIL_H__
