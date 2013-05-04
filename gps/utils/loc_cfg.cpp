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
#define LOG_TAG "LocSvc_utils_cfg"

#include <stdio.h>
#include <stdlib.h>
#include <pthread.h>
#include <string.h>
#include <ctype.h>
#include <unistd.h>
#include <time.h>
#include <loc_cfg.h>
#include <log_util.h>

/*=============================================================================
 *
 *                          GLOBAL DATA DECLARATION
 *
 *============================================================================*/

/* Parameter data */
static uint8_t DEBUG_LEVEL = 3;
static uint8_t TIMESTAMP = 0;

/* Parameter spec table */
static loc_param_s_type loc_parameter_table[] =
{
  {"DEBUG_LEVEL",                    &DEBUG_LEVEL, NULL,                   'n'},
  {"TIMESTAMP",                      &TIMESTAMP,   NULL,                   'n'},
};

int loc_param_num = sizeof(loc_parameter_table) / sizeof(loc_param_s_type);

/*===========================================================================
FUNCTION loc_default_parameters

DESCRIPTION
   Resets the parameters to default

DEPENDENCIES
   N/A

RETURN VALUE
   None

SIDE EFFECTS
   N/A
===========================================================================*/

static void loc_default_parameters()
{
   /* defaults */
   DEBUG_LEVEL = 3; /* debug level */
   TIMESTAMP = 0;

   /* reset logging mechanism */
   loc_logger_init(DEBUG_LEVEL, TIMESTAMP);
}

/*===========================================================================
FUNCTION trim_space

DESCRIPTION
   Removes leading and trailing spaces of the string

DEPENDENCIES
   N/A

RETURN VALUE
   None

SIDE EFFECTS
   N/A
===========================================================================*/
void trim_space(char *org_string)
{
   char *scan_ptr, *write_ptr;
   char *first_nonspace = NULL, *last_nonspace = NULL;

   scan_ptr = write_ptr = org_string;

   while (*scan_ptr)
   {
      if ( !isspace(*scan_ptr) && first_nonspace == NULL)
      {
         first_nonspace = scan_ptr;
      }

      if (first_nonspace != NULL)
      {
         *(write_ptr++) = *scan_ptr;
         if ( !isspace(*scan_ptr))
         {
            last_nonspace = write_ptr;
         }
      }

      scan_ptr++;
   }

   if (last_nonspace) { *last_nonspace = '\0'; }
}

typedef struct loc_param_v_type
{
   char* param_name;

   char* param_str_value;
   int param_int_value;
   double param_double_value;
}loc_param_v_type;

/*===========================================================================
FUNCTION loc_set_config_entry

DESCRIPTION
   Potentially sets a given configuration table entry based on the passed in
   configuration value. This is done by using a string comparison of the
   parameter names and those found in the configuration file.

PARAMETERS:
   config_entry: configuration entry in the table to possibly set
   config_value: value to store in the entry if the parameter names match

DEPENDENCIES
   N/A

RETURN VALUE
   None

SIDE EFFECTS
   N/A
===========================================================================*/
void loc_set_config_entry(loc_param_s_type* config_entry, loc_param_v_type* config_value)
{
   if(NULL == config_entry || NULL == config_value)
   {
      LOC_LOGE("%s: INVALID config entry or parameter", __FUNCTION__);
      return;
   }

   if (strcmp(config_entry->param_name, config_value->param_name) == 0 &&
               config_entry->param_ptr)
   {
      switch (config_entry->param_type)
      {
      case 's':
         if (strcmp(config_value->param_str_value, "NULL") == 0)
         {
            *((char*)config_entry->param_ptr) = '\0';
         }
         else {
            strlcpy((char*) config_entry->param_ptr,
                  config_value->param_str_value,
                  LOC_MAX_PARAM_STRING + 1);
         }
         /* Log INI values */
         LOC_LOGD("%s: PARAM %s = %s", __FUNCTION__, config_entry->param_name, (char*)config_entry->param_ptr);

         if(NULL != config_entry->param_set)
         {
            *(config_entry->param_set) = 1;
         }
         break;
      case 'n':
         *((int *)config_entry->param_ptr) = config_value->param_int_value;
         /* Log INI values */
         LOC_LOGD("%s: PARAM %s = %d", __FUNCTION__, config_entry->param_name, config_value->param_int_value);

         if(NULL != config_entry->param_set)
         {
            *(config_entry->param_set) = 1;
         }
         break;
      case 'f':
         *((double *)config_entry->param_ptr) = config_value->param_double_value;
         /* Log INI values */
         LOC_LOGD("%s: PARAM %s = %f", __FUNCTION__, config_entry->param_name, config_value->param_double_value);

         if(NULL != config_entry->param_set)
         {
            *(config_entry->param_set) = 1;
         }
         break;
      default:
         LOC_LOGE("%s: PARAM %s parameter type must be n, f, or s", __FUNCTION__, config_entry->param_name);
      }
   }
}

/*===========================================================================
FUNCTION loc_read_conf

DESCRIPTION
   Reads the specified configuration file and sets defined values based on
   the passed in configuration table. This table maps strings to values to
   set along with the type of each of these values.

PARAMETERS:
   conf_file_name: configuration file to read
   config_table: table definition of strings to places to store information
   table_length: length of the configuration table

DEPENDENCIES
   N/A

RETURN VALUE
   None

SIDE EFFECTS
   N/A
===========================================================================*/
void loc_read_conf(const char* conf_file_name, loc_param_s_type* config_table, uint32_t table_length)
{
   FILE *gps_conf_fp = NULL;
   char input_buf[LOC_MAX_PARAM_LINE];  /* declare a char array */
   char *lasts;
   loc_param_v_type config_value;
   uint32_t i;

   loc_default_parameters();

   if((gps_conf_fp = fopen(conf_file_name, "r")) != NULL)
   {
      LOC_LOGD("%s: using %s", __FUNCTION__, GPS_CONF_FILE);
   }
   else
   {
      LOC_LOGW("%s: no %s file found", __FUNCTION__, GPS_CONF_FILE);
      return; /* no parameter file */
   }

   /* Clear all validity bits */
   for(i = 0; NULL != config_table && i < table_length; i++)
   {
      if(NULL != config_table[i].param_set)
      {
         *(config_table[i].param_set) = 0;
      }
   }

   while(fgets(input_buf, LOC_MAX_PARAM_LINE, gps_conf_fp) != NULL)
   {
      memset(&config_value, 0, sizeof(config_value));

      /* Separate variable and value */
      config_value.param_name = strtok_r(input_buf, "=", &lasts);
      if (config_value.param_name == NULL) continue;       /* skip lines that do not contain "=" */
      config_value.param_str_value = strtok_r(NULL, "=", &lasts);
      if (config_value.param_str_value == NULL) continue;  /* skip lines that do not contain two operands */

      /* Trim leading and trailing spaces */
      trim_space(config_value.param_name);
      trim_space(config_value.param_str_value);

      /* Parse numerical value */
      if (config_value.param_str_value[0] == '0' && tolower(config_value.param_str_value[1]) == 'x')
      {
         /* hex */
         config_value.param_int_value = (int) strtol(&config_value.param_str_value[2], (char**) NULL, 16);
      }
      else {
         config_value.param_double_value = (double) atof(config_value.param_str_value); /* float */
         config_value.param_int_value = atoi(config_value.param_str_value); /* dec */
      }

      for(i = 0; NULL != config_table && i < table_length; i++)
      {
         loc_set_config_entry(&config_table[i], &config_value);
      }

      for(i = 0; i < loc_param_num; i++)
      {
         loc_set_config_entry(&loc_parameter_table[i], &config_value);
      }
   }

   fclose(gps_conf_fp);

   /* Initialize logging mechanism with parsed data */
   loc_logger_init(DEBUG_LEVEL, TIMESTAMP);
}
