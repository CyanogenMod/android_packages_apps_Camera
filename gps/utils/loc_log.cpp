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

#define LOG_NDDEBUG 0

#include <stdio.h>
#include <stdlib.h>
#include <sys/time.h>
#include "loc_log.h"
#include "msg_q.h"

#include "log_util.h"

// Logging Improvements
const char *loc_logger_boolStr[]={"False","True"};
const char VOID_RET[]   = "None";
const char FROM_AFW[]   = "===>";
const char TO_MODEM[]   = "--->";
const char FROM_MODEM[] = "<---";
const char TO_AFW[]     = "<===";
const char EXIT_TAG[]   = "Exiting";
const char ENTRY_TAG[]  = "Entering";

/* Logging Mechanism */
loc_logger_s_type loc_logger;

/* Get names from value */
const char* loc_get_name_from_mask(loc_name_val_s_type table[], int table_size, long mask)
{
   int i;
   for (i = 0; i < table_size; i++)
   {
      if (table[i].val & (long) mask)
      {
         return table[i].name;
      }
   }
   return UNKNOWN_STR;
}

/* Get names from value */
const char* loc_get_name_from_val(loc_name_val_s_type table[], int table_size, long value)
{
   int i;
   for (i = 0; i < table_size; i++)
   {
      if (table[i].val == (long) value)
      {
         return table[i].name;
      }
   }
   return UNKNOWN_STR;
}

static loc_name_val_s_type loc_msg_q_status[] =
{
    NAME_VAL( eMSG_Q_SUCCESS ),
    NAME_VAL( eMSG_Q_FAILURE_GENERAL ),
    NAME_VAL( eMSG_Q_INVALID_PARAMETER ),
    NAME_VAL( eMSG_Q_INVALID_HANDLE ),
    NAME_VAL( eMSG_Q_UNAVAILABLE_RESOURCE ),
    NAME_VAL( eMSG_Q_INSUFFICIENT_BUFFER )
};
static int loc_msg_q_status_num = sizeof(loc_msg_q_status) / sizeof(loc_name_val_s_type);

/* Find msg_q status name */
const char* loc_get_msg_q_status(int status)
{
   return loc_get_name_from_val(loc_msg_q_status, loc_msg_q_status_num, (long) status);
}

const char* log_succ_fail_string(int is_succ)
{
   return is_succ? "successful" : "failed";
}


/*===========================================================================

FUNCTION loc_get_time

DESCRIPTION
   Logs a callback event header.
   The pointer time_string should point to a buffer of at least 13 bytes:

   XX:XX:XX.000\0

RETURN VALUE
   The time string

===========================================================================*/
char *loc_get_time(char *time_string, unsigned long buf_size)
{
   struct timeval now;     /* sec and usec     */
   struct tm now_tm;       /* broken-down time */
   char hms_string[80];    /* HH:MM:SS         */

   gettimeofday(&now, NULL);
   localtime_r(&now.tv_sec, &now_tm);

   strftime(hms_string, sizeof hms_string, "%H:%M:%S", &now_tm);
   snprintf(time_string, buf_size, "%s.%03d", hms_string, (int) (now.tv_usec / 1000));

   return time_string;
}


/*===========================================================================
FUNCTION loc_logger_init

DESCRIPTION
   Initializes the state of DEBUG_LEVEL and TIMESTAMP

DEPENDENCIES
   N/A

RETURN VALUE
   None

SIDE EFFECTS
   N/A
===========================================================================*/
void loc_logger_init(unsigned long debug, unsigned long timestamp)
{
   loc_logger.DEBUG_LEVEL = debug;
   loc_logger.TIMESTAMP   = timestamp;
}


/*===========================================================================
FUNCTION get_timestamp

DESCRIPTION
   Generates a timestamp using the current system time

DEPENDENCIES
   N/A

RETURN VALUE
   Char pointer to the parameter str

SIDE EFFECTS
   N/A
===========================================================================*/
char * get_timestamp(char *str, unsigned long buf_size)
{
  struct timeval tv;
  struct timezone tz;
  int hh, mm, ss;
  gettimeofday(&tv, &tz);
  hh = tv.tv_sec/3600%24;
  mm = (tv.tv_sec%3600)/60;
  ss = tv.tv_sec%60;
  snprintf(str, buf_size, "%02d:%02d:%02d.%06ld", hh, mm, ss, tv.tv_usec);
  return str;
}

