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

#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <ctype.h>
#include <math.h>
#include <pthread.h>
#include <arpa/inet.h>
#include <netinet/in.h>         /* struct sockaddr_in */
#include <sys/socket.h>
#include <sys/time.h>
#include <netdb.h>
#include <time.h>

#include "LocApiAdapter.h"

#include <cutils/sched_policy.h>
#include <utils/SystemClock.h>
#include <utils/Log.h>
#include <string.h>

#include <loc_eng.h>
#include <loc_eng_ni.h>
#include <loc_eng_dmn_conn.h>
#include <loc_eng_dmn_conn_handler.h>
#include <loc_eng_msg.h>
#include <loc_eng_msg_id.h>
#include <loc_eng_nmea.h>
#include <msg_q.h>
#include <loc.h>

#include "log_util.h"
#include "loc_eng_log.h"

#define SUCCESS TRUE
#define FAILURE FALSE

static void loc_eng_deferred_action_thread(void* context);
static void* loc_eng_create_msg_q();
static void loc_eng_free_msg(void* msg);

pthread_mutex_t LocEngContext::lock = PTHREAD_MUTEX_INITIALIZER;
pthread_cond_t LocEngContext::cond = PTHREAD_COND_INITIALIZER;
LocEngContext* LocEngContext::me = NULL;
boolean gpsConfigAlreadyRead = false;

loc_gps_cfg_s_type gps_conf;

/* Parameter spec table */
static loc_param_s_type loc_parameter_table[] =
{
  {"INTERMEDIATE_POS",               &gps_conf.INTERMEDIATE_POS,               NULL, 'n'},
  {"ACCURACY_THRES",                 &gps_conf.ACCURACY_THRES,                 NULL, 'n'},
  {"ENABLE_WIPER",                   &gps_conf.ENABLE_WIPER,                   NULL, 'n'},
  {"NMEA_PROVIDER",                  &gps_conf.NMEA_PROVIDER,                  NULL, 'n'},
  {"SUPL_VER",                       &gps_conf.SUPL_VER,                       NULL, 'n'},
  {"CAPABILITIES",                   &gps_conf.CAPABILITIES,                   NULL, 'n'},
  {"GYRO_BIAS_RANDOM_WALK",          &gps_conf.GYRO_BIAS_RANDOM_WALK,          &gps_conf.GYRO_BIAS_RANDOM_WALK_VALID, 'f'},
  {"ACCEL_RANDOM_WALK_SPECTRAL_DENSITY",     &gps_conf.ACCEL_RANDOM_WALK_SPECTRAL_DENSITY,    &gps_conf.ACCEL_RANDOM_WALK_SPECTRAL_DENSITY_VALID, 'f'},
  {"ANGLE_RANDOM_WALK_SPECTRAL_DENSITY",     &gps_conf.ANGLE_RANDOM_WALK_SPECTRAL_DENSITY,    &gps_conf.ANGLE_RANDOM_WALK_SPECTRAL_DENSITY_VALID, 'f'},
  {"RATE_RANDOM_WALK_SPECTRAL_DENSITY",      &gps_conf.RATE_RANDOM_WALK_SPECTRAL_DENSITY,     &gps_conf.RATE_RANDOM_WALK_SPECTRAL_DENSITY_VALID, 'f'},
  {"VELOCITY_RANDOM_WALK_SPECTRAL_DENSITY",  &gps_conf.VELOCITY_RANDOM_WALK_SPECTRAL_DENSITY, &gps_conf.VELOCITY_RANDOM_WALK_SPECTRAL_DENSITY_VALID, 'f'},
  {"SENSOR_ACCEL_BATCHES_PER_SEC",   &gps_conf.SENSOR_ACCEL_BATCHES_PER_SEC,   NULL, 'n'},
  {"SENSOR_ACCEL_SAMPLES_PER_BATCH", &gps_conf.SENSOR_ACCEL_SAMPLES_PER_BATCH, NULL, 'n'},
  {"SENSOR_GYRO_BATCHES_PER_SEC",    &gps_conf.SENSOR_GYRO_BATCHES_PER_SEC,    NULL, 'n'},
  {"SENSOR_GYRO_SAMPLES_PER_BATCH",  &gps_conf.SENSOR_GYRO_SAMPLES_PER_BATCH,  NULL, 'n'},
  {"SENSOR_ACCEL_BATCHES_PER_SEC_HIGH",   &gps_conf.SENSOR_ACCEL_BATCHES_PER_SEC_HIGH,   NULL, 'n'},
  {"SENSOR_ACCEL_SAMPLES_PER_BATCH_HIGH", &gps_conf.SENSOR_ACCEL_SAMPLES_PER_BATCH_HIGH, NULL, 'n'},
  {"SENSOR_GYRO_BATCHES_PER_SEC_HIGH",    &gps_conf.SENSOR_GYRO_BATCHES_PER_SEC_HIGH,    NULL, 'n'},
  {"SENSOR_GYRO_SAMPLES_PER_BATCH_HIGH",  &gps_conf.SENSOR_GYRO_SAMPLES_PER_BATCH_HIGH,  NULL, 'n'},
  {"SENSOR_CONTROL_MODE",            &gps_conf.SENSOR_CONTROL_MODE,            NULL, 'n'},
  {"SENSOR_USAGE",                   &gps_conf.SENSOR_USAGE,                   NULL, 'n'},
  {"SENSOR_ALGORITHM_CONFIG_MASK",   &gps_conf.SENSOR_ALGORITHM_CONFIG_MASK,   NULL, 'n'},
  {"QUIPC_ENABLED",                  &gps_conf.QUIPC_ENABLED,                  NULL, 'n'},
  {"LPP_PROFILE",                    &gps_conf.LPP_PROFILE,                    NULL, 'n'},
  {"A_GLONASS_POS_PROTOCOL_SELECT",  &gps_conf.A_GLONASS_POS_PROTOCOL_SELECT,  NULL, 'n'},
};

static void loc_default_parameters(void)
{
   /* defaults */
   gps_conf.INTERMEDIATE_POS = 0;
   gps_conf.ACCURACY_THRES = 0;
   gps_conf.ENABLE_WIPER = 0;
   gps_conf.NMEA_PROVIDER = 0;
   gps_conf.SUPL_VER = 0x10000;
   gps_conf.CAPABILITIES = 0x7;

   gps_conf.GYRO_BIAS_RANDOM_WALK = 0;
   gps_conf.SENSOR_ACCEL_BATCHES_PER_SEC = 2;
   gps_conf.SENSOR_ACCEL_SAMPLES_PER_BATCH = 5;
   gps_conf.SENSOR_GYRO_BATCHES_PER_SEC = 2;
   gps_conf.SENSOR_GYRO_SAMPLES_PER_BATCH = 5;
   gps_conf.SENSOR_ACCEL_BATCHES_PER_SEC_HIGH = 4;
   gps_conf.SENSOR_ACCEL_SAMPLES_PER_BATCH_HIGH = 25;
   gps_conf.SENSOR_GYRO_BATCHES_PER_SEC_HIGH = 4;
   gps_conf.SENSOR_GYRO_SAMPLES_PER_BATCH_HIGH = 25;
   gps_conf.SENSOR_CONTROL_MODE = 0; /* AUTO */
   gps_conf.SENSOR_USAGE = 0; /* Enabled */
   gps_conf.SENSOR_ALGORITHM_CONFIG_MASK = 0; /* INS Disabled = FALSE*/

   /* Values MUST be set by OEMs in configuration for sensor-assisted
      navigation to work. There are NO default values */
   gps_conf.ACCEL_RANDOM_WALK_SPECTRAL_DENSITY = 0;
   gps_conf.ANGLE_RANDOM_WALK_SPECTRAL_DENSITY = 0;
   gps_conf.RATE_RANDOM_WALK_SPECTRAL_DENSITY = 0;
   gps_conf.VELOCITY_RANDOM_WALK_SPECTRAL_DENSITY = 0;

   gps_conf.GYRO_BIAS_RANDOM_WALK_VALID = 0;
   gps_conf.ACCEL_RANDOM_WALK_SPECTRAL_DENSITY_VALID = 0;
   gps_conf.ANGLE_RANDOM_WALK_SPECTRAL_DENSITY_VALID = 0;
   gps_conf.RATE_RANDOM_WALK_SPECTRAL_DENSITY_VALID = 0;
   gps_conf.VELOCITY_RANDOM_WALK_SPECTRAL_DENSITY_VALID = 0;

      /* LTE Positioning Profile configuration is disable by default*/
   gps_conf.LPP_PROFILE = 0;

   /*By default no positioning protocol is selected on A-GLONASS system*/
   gps_conf.A_GLONASS_POS_PROTOCOL_SELECT = 0;
}

LocEngContext::LocEngContext(gps_create_thread threadCreator) :
    deferred_q((const void*)loc_eng_create_msg_q()),
#ifdef FEATURE_ULP
    //TODO: should we conditionally create ulp msg q?
    ulp_q((const void*)loc_eng_create_msg_q()),
#endif
    deferred_action_thread(threadCreator("loc_eng",loc_eng_deferred_action_thread, this)),
    counter(0)
{
    LOC_LOGV("LocEngContext %d : %d pthread_id %ld\n",
             getpid(), gettid(),
             deferred_action_thread);
}

LocEngContext* LocEngContext::get(gps_create_thread threadCreator)
{
    pthread_mutex_lock(&lock);
    // gonna need mutex protection here...
    if (NULL == me) {
        me = new LocEngContext(threadCreator);
    }
    me->counter++;

    pthread_mutex_unlock(&lock);
    return me;
}

void LocEngContext::drop()
{
    if (deferred_action_thread != pthread_self()) {
        pthread_mutex_lock(&lock);
        counter--;
        if (counter == 0) {
            loc_eng_msg *msg(new loc_eng_msg(this, LOC_ENG_MSG_QUIT));
            msg_q_snd((void*)deferred_q, msg, loc_eng_free_msg);

            // I am not sure if this is going to be hazardous. The calling thread
            // might be blocked for a while, if the q is loaded.  I am wondering
            // if we should just dump all the msgs in the q upon QUIT.
            pthread_cond_wait(&cond, &lock);

            msg_q_destroy((void**)&deferred_q);
#ifdef FEATURE_ULP
            msg_q_destroy((void**)&ulp_q);
#endif
            delete me;
            me = NULL;
        }
        pthread_mutex_unlock(&lock);
    } else {
        LOC_LOGE("The HAL thread cannot free itself");
    }
}

// 2nd half of init(), singled out for
// modem restart to use.
static int loc_eng_reinit(loc_eng_data_s_type &loc_eng_data);
static void loc_eng_agps_reinit(loc_eng_data_s_type &loc_eng_data);

static int loc_eng_set_server(loc_eng_data_s_type &loc_eng_data,
                              LocServerType type, const char *hostname, int port);
// Internal functions
static void loc_inform_gps_status(loc_eng_data_s_type &loc_eng_data,
                                  GpsStatusValue status);
static void loc_eng_report_status(loc_eng_data_s_type &loc_eng_data,
                                  GpsStatusValue status);
static void loc_eng_process_conn_request(loc_eng_data_s_type &loc_eng_data,
                                         int connHandle, AGpsType agps_type);
static void loc_eng_agps_close_status(loc_eng_data_s_type &loc_eng_data, int is_succ);
static void loc_eng_handle_engine_down(loc_eng_data_s_type &loc_eng_data) ;
static void loc_eng_handle_engine_up(loc_eng_data_s_type &loc_eng_data) ;

static char extra_data[100];
/*********************************************************************
 * Initialization checking macros
 *********************************************************************/
#define STATE_CHECK(ctx, x, ret) \
    if (!(ctx))                  \
  {                              \
      /* Not intialized, abort */\
      LOC_LOGE("%s: log_eng state error: %s", __func__, x); \
      EXIT_LOG(%s, x);                                            \
      ret;                                                        \
  }
#define INIT_CHECK(ctx, ret) STATE_CHECK(ctx, "instance not initialized", ret)

void loc_eng_msg_sender(void* loc_eng_data_p, void* msg)
{
    LocEngContext* loc_eng_context = (LocEngContext*)((loc_eng_data_s_type*)loc_eng_data_p)->context;
    msg_q_snd((void*)loc_eng_context->deferred_q, msg, loc_eng_free_msg);
}

static void* loc_eng_create_msg_q()
{
    void* q = NULL;
    if (eMSG_Q_SUCCESS != msg_q_init(&q)) {
        LOC_LOGE("loc_eng_create_msg_q Q init failed.");
        q = NULL;
    }
    return q;
}

static void loc_eng_free_msg(void* msg)
{
    delete (loc_eng_msg*)msg;
}

/*===========================================================================
FUNCTION    loc_eng_init

DESCRIPTION
   Initialize the location engine, this include setting up global datas
   and registers location engien with loc api service.

DEPENDENCIES
   None

RETURN VALUE
   0: success

SIDE EFFECTS
   N/A

===========================================================================*/
int loc_eng_init(loc_eng_data_s_type &loc_eng_data, LocCallbacks* callbacks,
                 LOC_API_ADAPTER_EVENT_MASK_T event,
                  void (*loc_external_msg_sender) (void*, void*))

{
    int ret_val =-1;

    ENTRY_LOG_CALLFLOW();
    if (NULL == callbacks || 0 == event) {
        LOC_LOGE("loc_eng_init: bad parameters cb %p eMask %d", callbacks, event);
        EXIT_LOG(%d, ret_val);
        return ret_val;
    }

    STATE_CHECK((NULL == loc_eng_data.context),
                "instance already initialized", return 0);

    memset(&loc_eng_data, 0, sizeof (loc_eng_data));

    // Create context (msg q + thread) (if not yet created)
    // This will also parse gps.conf, if not done.
    loc_eng_data.context = (void*)LocEngContext::get(callbacks->create_thread_cb);
    if (NULL != callbacks->set_capabilities_cb) {
        callbacks->set_capabilities_cb(gps_conf.CAPABILITIES);
    }

    // Save callbacks
    loc_eng_data.location_cb  = callbacks->location_cb;
    loc_eng_data.sv_status_cb = callbacks->sv_status_cb;
    loc_eng_data.status_cb    = callbacks->status_cb;
    loc_eng_data.nmea_cb      = callbacks->nmea_cb;
    loc_eng_data.acquire_wakelock_cb = callbacks->acquire_wakelock_cb;
    loc_eng_data.release_wakelock_cb = callbacks->release_wakelock_cb;
    loc_eng_data.request_utc_time_cb = callbacks->request_utc_time_cb;
    loc_eng_data.intermediateFix = gps_conf.INTERMEDIATE_POS;

    // initial states taken care of by the memset above
    // loc_eng_data.engine_status -- GPS_STATUS_NONE;
    // loc_eng_data.fix_session_status -- GPS_STATUS_NONE;
    // loc_eng_data.mute_session_state -- LOC_MUTE_SESS_NONE;

    if ((event & LOC_API_ADAPTER_BIT_NMEA_1HZ_REPORT) && (gps_conf.NMEA_PROVIDER == NMEA_PROVIDER_AP))
    {
        event = event ^ LOC_API_ADAPTER_BIT_NMEA_1HZ_REPORT; // unregister for modem NMEA report
        loc_eng_data.generateNmea = true;
    }
    else
    {
        loc_eng_data.generateNmea = false;
    }

#ifdef FEATURE_ULP
    LocEng locEngHandle(&loc_eng_data, event, loc_eng_data.acquire_wakelock_cb,
                        loc_eng_data.release_wakelock_cb, loc_eng_msg_sender, loc_external_msg_sender,
                        callbacks->location_ext_parser, callbacks->sv_ext_parser);
#else
    LocEng locEngHandle(&loc_eng_data, event, loc_eng_data.acquire_wakelock_cb,
                        loc_eng_data.release_wakelock_cb, loc_eng_msg_sender,
                        callbacks->location_ext_parser, callbacks->sv_ext_parser);
#endif
    loc_eng_data.client_handle = LocApiAdapter::getLocApiAdapter(locEngHandle);

    if (NULL == loc_eng_data.client_handle) {
        // drop the context and declare failure
        ((LocEngContext*)(loc_eng_data.context))->drop();
        loc_eng_data.context = NULL;
    } else {
        LOC_LOGD("loc_eng_init created client, id = %p\n", loc_eng_data.client_handle);

        // call reinit to send initialization messages
       int tries = 30;
       while (tries > 0 &&
              LOC_API_ADAPTER_ERR_SUCCESS != (ret_val = loc_eng_reinit(loc_eng_data))) {
           tries--;
           LOC_LOGD("loc_eng_init client open failed, %d more tries", tries);
           sleep(1);
       }
    }

    EXIT_LOG(%d, ret_val);
    return ret_val;
}

static int loc_eng_reinit(loc_eng_data_s_type &loc_eng_data)
{
    ENTRY_LOG();
    int ret_val = loc_eng_data.client_handle->reinit();

    if (LOC_API_ADAPTER_ERR_SUCCESS == ret_val) {
        LOC_LOGD("loc_eng_reinit reinit() successful");

        loc_eng_msg_suple_version *supl_msg(new loc_eng_msg_suple_version(&loc_eng_data,
                                                                          gps_conf.SUPL_VER));
        msg_q_snd((void*)((LocEngContext*)(loc_eng_data.context))->deferred_q,
                  supl_msg, loc_eng_free_msg);

        loc_eng_msg_lpp_config *lpp_msg(new loc_eng_msg_lpp_config(&loc_eng_data,
                                                                          gps_conf.LPP_PROFILE));
        msg_q_snd((void*)((LocEngContext*)(loc_eng_data.context))->deferred_q,
                  lpp_msg, loc_eng_free_msg);

        loc_eng_msg_sensor_control_config *sensor_control_config_msg(
            new loc_eng_msg_sensor_control_config(&loc_eng_data, gps_conf.SENSOR_USAGE));
        msg_q_snd((void*)((LocEngContext*)(loc_eng_data.context))->deferred_q,
                  sensor_control_config_msg, loc_eng_free_msg);

        loc_eng_msg_a_glonass_protocol *a_glonass_protocol_msg(new loc_eng_msg_a_glonass_protocol(&loc_eng_data,
                                                                          gps_conf.A_GLONASS_POS_PROTOCOL_SELECT));
        msg_q_snd((void*)((LocEngContext*)(loc_eng_data.context))->deferred_q,
                  a_glonass_protocol_msg, loc_eng_free_msg);

        /* Make sure at least one of the sensor property is specified by the user in the gps.conf file. */
        if( gps_conf.GYRO_BIAS_RANDOM_WALK_VALID ||
            gps_conf.ACCEL_RANDOM_WALK_SPECTRAL_DENSITY_VALID ||
            gps_conf.ANGLE_RANDOM_WALK_SPECTRAL_DENSITY_VALID ||
            gps_conf.RATE_RANDOM_WALK_SPECTRAL_DENSITY_VALID ||
            gps_conf.VELOCITY_RANDOM_WALK_SPECTRAL_DENSITY_VALID )
        {
            loc_eng_msg_sensor_properties *sensor_properties_msg(
                new loc_eng_msg_sensor_properties(&loc_eng_data,
                                                   gps_conf.GYRO_BIAS_RANDOM_WALK_VALID,
                                                   gps_conf.GYRO_BIAS_RANDOM_WALK,
                                                   gps_conf.ACCEL_RANDOM_WALK_SPECTRAL_DENSITY_VALID,
                                                   gps_conf.ACCEL_RANDOM_WALK_SPECTRAL_DENSITY,
                                                   gps_conf.ANGLE_RANDOM_WALK_SPECTRAL_DENSITY_VALID,
                                                   gps_conf.ANGLE_RANDOM_WALK_SPECTRAL_DENSITY,
                                                   gps_conf.RATE_RANDOM_WALK_SPECTRAL_DENSITY_VALID,
                                                   gps_conf.RATE_RANDOM_WALK_SPECTRAL_DENSITY,
                                                   gps_conf.VELOCITY_RANDOM_WALK_SPECTRAL_DENSITY_VALID,
                                                   gps_conf.VELOCITY_RANDOM_WALK_SPECTRAL_DENSITY));
            msg_q_snd((void*)((LocEngContext*)(loc_eng_data.context))->deferred_q,
                      sensor_properties_msg, loc_eng_free_msg);
        }

        loc_eng_msg_sensor_perf_control_config *sensor_perf_control_conf_msg(
            new loc_eng_msg_sensor_perf_control_config(&loc_eng_data,
                                                       gps_conf.SENSOR_CONTROL_MODE,
                                                       gps_conf.SENSOR_ACCEL_SAMPLES_PER_BATCH,
                                                       gps_conf.SENSOR_ACCEL_BATCHES_PER_SEC,
                                                       gps_conf.SENSOR_GYRO_SAMPLES_PER_BATCH,
                                                       gps_conf.SENSOR_GYRO_BATCHES_PER_SEC,
                                                       gps_conf.SENSOR_ACCEL_SAMPLES_PER_BATCH_HIGH,
                                                       gps_conf.SENSOR_ACCEL_BATCHES_PER_SEC_HIGH,
                                                       gps_conf.SENSOR_GYRO_SAMPLES_PER_BATCH_HIGH,
                                                       gps_conf.SENSOR_GYRO_BATCHES_PER_SEC_HIGH,
                                                       gps_conf.SENSOR_ALGORITHM_CONFIG_MASK));
        msg_q_snd((void*)((LocEngContext*)(loc_eng_data.context))->deferred_q,
                  sensor_perf_control_conf_msg, loc_eng_free_msg);
    }

    EXIT_LOG(%d, ret_val);
    return ret_val;
}

/*===========================================================================
FUNCTION    loc_eng_cleanup

DESCRIPTION
   Cleans location engine. The location client handle will be released.

DEPENDENCIES
   None

RETURN VALUE
   None

SIDE EFFECTS
   N/A

===========================================================================*/
void loc_eng_cleanup(loc_eng_data_s_type &loc_eng_data)
{
    ENTRY_LOG_CALLFLOW();
    INIT_CHECK(loc_eng_data.context, return);

    // XTRA has no state, so we are fine with it.

    // we need to check and clear NI
#if 0
    // we need to check and clear ATL
    if (NULL != loc_eng_data.agnss_nif) {
        delete loc_eng_data.agnss_nif;
        loc_eng_data.agnss_nif = NULL;
    }
    if (NULL != loc_eng_data.internet_nif) {
        delete loc_eng_data.internet_nif;
        loc_eng_data.internet_nif = NULL;
    }
#endif
    if (loc_eng_data.client_handle->isInSession())
    {
        LOC_LOGD("loc_eng_cleanup: fix not stopped. stop it now.");
        loc_eng_stop(loc_eng_data);
    }

#if 0 // can't afford to actually clean up, for many reason.

    ((LocEngContext*)(loc_eng_data.context))->drop();
    loc_eng_data.context = NULL;

#ifdef FEATURE_ULP
    // De-initialize ulp
    if (locEngUlpInf != NULL)
    {
        locEngUlpInf = NULL;
        msg_q_destroy( &loc_eng_data.ulp_q);
    }
#endif

    if (loc_eng_data.client_handle != NULL)
    {
        LOC_LOGD("loc_eng_init: client opened. close it now.");
        delete loc_eng_data.client_handle;
        loc_eng_data.client_handle = NULL;
    }

    loc_eng_dmn_conn_loc_api_server_unblock();
    loc_eng_dmn_conn_loc_api_server_join();

#endif

    EXIT_LOG(%s, VOID_RET);
}


/*===========================================================================
FUNCTION    loc_eng_start

DESCRIPTION
   Starts the tracking session

DEPENDENCIES
   None

RETURN VALUE
   0: success

SIDE EFFECTS
   N/A

===========================================================================*/
int loc_eng_start(loc_eng_data_s_type &loc_eng_data)
{
   ENTRY_LOG_CALLFLOW();
   INIT_CHECK(loc_eng_data.context, return -1);

#ifdef FEATURE_ULP
   if((loc_eng_data.ulp_initialized == true) && (gps_conf.CAPABILITIES & ULP_CAPABILITY))
   {
       //Pass the start messgage to ULP if present & activated
       loc_eng_msg *msg(new loc_eng_msg(&loc_eng_data, ULP_MSG_START_FIX));
       msg_q_snd( (void*)((LocEngContext*)(loc_eng_data.context))->ulp_q,
                  msg, loc_eng_free_msg);
   }
   else
#endif
   {
       loc_eng_msg *msg(new loc_eng_msg(&loc_eng_data, LOC_ENG_MSG_START_FIX));
       msg_q_snd((void*)((LocEngContext*)(loc_eng_data.context))->deferred_q,
                 msg, loc_eng_free_msg);
   }
   EXIT_LOG(%d, 0);
   return 0;
}

static int loc_eng_start_handler(loc_eng_data_s_type &loc_eng_data)
{
   ENTRY_LOG();
   int ret_val = LOC_API_ADAPTER_ERR_SUCCESS;

   if (!loc_eng_data.client_handle->isInSession()) {
       ret_val = loc_eng_data.client_handle->startFix();

       if (ret_val == LOC_API_ADAPTER_ERR_SUCCESS ||
           ret_val == LOC_API_ADAPTER_ERR_ENGINE_DOWN)
       {
           loc_eng_data.client_handle->setInSession(TRUE);
           loc_inform_gps_status(loc_eng_data, GPS_STATUS_SESSION_BEGIN);
       }
   }

   EXIT_LOG(%d, ret_val);
   return ret_val;
}

/*===========================================================================
FUNCTION    loc_eng_stop_wrapper

DESCRIPTION
   Stops the tracking session

DEPENDENCIES
   None

RETURN VALUE
   0: success

SIDE EFFECTS
   N/A

===========================================================================*/
int loc_eng_stop(loc_eng_data_s_type &loc_eng_data)
{
    ENTRY_LOG_CALLFLOW();
    INIT_CHECK(loc_eng_data.context, return -1);

#ifdef FEATURE_ULP
    if((loc_eng_data.ulp_initialized == true) && (gps_conf.CAPABILITIES & ULP_CAPABILITY))
    {
        //Pass the start messgage to ULP if present & activated
        loc_eng_msg *msg(new loc_eng_msg(&loc_eng_data, ULP_MSG_STOP_FIX));
        msg_q_snd( (void*)((LocEngContext*)(loc_eng_data.context))->ulp_q,
                   msg, loc_eng_free_msg);
    }
    else
#endif
    {
        loc_eng_msg *msg(new loc_eng_msg(&loc_eng_data, LOC_ENG_MSG_STOP_FIX));
        msg_q_snd((void*)((LocEngContext*)(loc_eng_data.context))->deferred_q,
                  msg, loc_eng_free_msg);
    }

    EXIT_LOG(%d, 0);
    return 0;
}

static int loc_eng_stop_handler(loc_eng_data_s_type &loc_eng_data)
{
   ENTRY_LOG();
   int ret_val = LOC_API_ADAPTER_ERR_SUCCESS;

   if (loc_eng_data.client_handle->isInSession()) {

       ret_val = loc_eng_data.client_handle->stopFix();
       if (ret_val == LOC_API_ADAPTER_ERR_SUCCESS)
       {
           loc_inform_gps_status(loc_eng_data, GPS_STATUS_SESSION_END);
       }

       loc_eng_data.client_handle->setInSession(FALSE);
   }

    EXIT_LOG(%d, ret_val);
    return ret_val;
}

/*===========================================================================
FUNCTION    loc_eng_mute_one_session

DESCRIPTION
   Mutes one session

DEPENDENCIES
   None

RETURN VALUE
   0: Success

SIDE EFFECTS
   N/A

===========================================================================*/
void loc_eng_mute_one_session(loc_eng_data_s_type &loc_eng_data)
{
    ENTRY_LOG();
    loc_eng_data.mute_session_state = LOC_MUTE_SESS_WAIT;
    EXIT_LOG(%s, VOID_RET);
}

/*===========================================================================
FUNCTION    loc_eng_set_position_mode

DESCRIPTION
   Sets the mode and fix frequency for the tracking session.

DEPENDENCIES
   None

RETURN VALUE
   0: success

SIDE EFFECTS
   N/A

===========================================================================*/
int loc_eng_set_position_mode(loc_eng_data_s_type &loc_eng_data,
                              LocPosMode &params)
{
    ENTRY_LOG_CALLFLOW();
    INIT_CHECK(loc_eng_data.context, return -1);
    loc_eng_msg_position_mode *msg(
        new loc_eng_msg_position_mode(&loc_eng_data, params));
    msg_q_snd((void*)((LocEngContext*)(loc_eng_data.context))->deferred_q,
              msg, loc_eng_free_msg);

    EXIT_LOG(%d, 0);
    return 0;
}

/*===========================================================================
FUNCTION    loc_eng_inject_time

DESCRIPTION
   This is used by Java native function to do time injection.

DEPENDENCIES
   None

RETURN VALUE
   0

SIDE EFFECTS
   N/A

===========================================================================*/
int loc_eng_inject_time(loc_eng_data_s_type &loc_eng_data, GpsUtcTime time,
                        int64_t timeReference, int uncertainty)
{
    ENTRY_LOG_CALLFLOW();
    INIT_CHECK(loc_eng_data.context, return -1);
    loc_eng_msg_set_time *msg(
        new loc_eng_msg_set_time(&loc_eng_data,
                                 time,
                                 timeReference,
                                 uncertainty));
    msg_q_snd((void*)((LocEngContext*)(loc_eng_data.context))->deferred_q,
              msg, loc_eng_free_msg);
    EXIT_LOG(%d, 0);
    return 0;
}


/*===========================================================================
FUNCTION    loc_eng_inject_location

DESCRIPTION
   This is used by Java native function to do location injection.

DEPENDENCIES
   None

RETURN VALUE
   0          : Successful
   error code : Failure

SIDE EFFECTS
   N/A
===========================================================================*/
int loc_eng_inject_location(loc_eng_data_s_type &loc_eng_data, double latitude,
                            double longitude, float accuracy)
{
    ENTRY_LOG_CALLFLOW();
    INIT_CHECK(loc_eng_data.context, return -1);
    loc_eng_msg_inject_location *msg(
        new loc_eng_msg_inject_location(&loc_eng_data,
                                        latitude,
                                        longitude,
                                        accuracy));
    msg_q_snd((void*)((LocEngContext*)(loc_eng_data.context))->deferred_q,
              msg, loc_eng_free_msg);

    EXIT_LOG(%d, 0);
    return 0;
}


/*===========================================================================
FUNCTION    loc_eng_delete_aiding_data

DESCRIPTION
   This is used by Java native function to delete the aiding data. The function
   updates the global variable for the aiding data to be deleted. If the GPS
   engine is off, the aiding data will be deleted. Otherwise, the actual action
   will happen when gps engine is turned off.

DEPENDENCIES
   Assumes the aiding data type specified in GpsAidingData matches with
   LOC API specification.

RETURN VALUE
   None

SIDE EFFECTS
   N/A

===========================================================================*/
void loc_eng_delete_aiding_data(loc_eng_data_s_type &loc_eng_data, GpsAidingData f)
{
    ENTRY_LOG_CALLFLOW();
    INIT_CHECK(loc_eng_data.context, return);

    loc_eng_msg_delete_aiding_data *msg(
        new loc_eng_msg_delete_aiding_data(&loc_eng_data,
                                           f));
    msg_q_snd((void*)((LocEngContext*)(loc_eng_data.context))->deferred_q,
              msg, loc_eng_free_msg);

    EXIT_LOG(%s, VOID_RET);
}

/*===========================================================================

FUNCTION    loc_inform_gps_state

DESCRIPTION
   Informs the GPS Provider about the GPS status

DEPENDENCIES
   None

RETURN VALUE
   None

SIDE EFFECTS
   N/A

===========================================================================*/
static void loc_inform_gps_status(loc_eng_data_s_type &loc_eng_data, GpsStatusValue status)
{
    ENTRY_LOG();

    static GpsStatusValue last_status = GPS_STATUS_NONE;

    GpsStatus gs = { sizeof(gs),status };


    if (loc_eng_data.status_cb)
    {
        CALLBACK_LOG_CALLFLOW("status_cb", %s, loc_get_gps_status_name(gs.status));
        loc_eng_data.status_cb(&gs);
    }

    last_status = status;

    EXIT_LOG(%s, VOID_RET);
}

/*===========================================================================
FUNCTION    loc_eng_agps_reinit

DESCRIPTION
   2nd half of loc_eng_agps_init(), singled out for modem restart to use.

DEPENDENCIES
   NONE

RETURN VALUE
   0

SIDE EFFECTS
   N/A

===========================================================================*/
static void loc_eng_agps_reinit(loc_eng_data_s_type &loc_eng_data)
{
    ENTRY_LOG();

    // Set server addresses which came before init
    if (loc_eng_data.supl_host_set)
    {
        loc_eng_set_server(loc_eng_data, LOC_AGPS_SUPL_SERVER,
                           loc_eng_data.supl_host_buf,
                           loc_eng_data.supl_port_buf);
    }

    if (loc_eng_data.c2k_host_set)
    {
        loc_eng_set_server(loc_eng_data, LOC_AGPS_CDMA_PDE_SERVER,
                           loc_eng_data.c2k_host_buf,
                           loc_eng_data.c2k_port_buf);
    }
    EXIT_LOG(%s, VOID_RET);
}
/*===========================================================================
FUNCTION    loc_eng_agps_init

DESCRIPTION
   Initialize the AGps interface.

DEPENDENCIES
   NONE

RETURN VALUE
   0

SIDE EFFECTS
   N/A

===========================================================================*/
void loc_eng_agps_init(loc_eng_data_s_type &loc_eng_data, AGpsCallbacks* callbacks)
{
    ENTRY_LOG_CALLFLOW();
    INIT_CHECK(loc_eng_data.context, return);
    STATE_CHECK((NULL == loc_eng_data.agps_status_cb),
                "agps instance already initialized",
                return);
    if(callbacks == NULL) {
        LOC_LOGE("loc_eng_agps_init: bad parameters cb %p", callbacks);
        EXIT_LOG(%s, VOID_RET);
        return;
    }
    loc_eng_data.agps_status_cb = callbacks->status_cb;

    loc_eng_data.agnss_nif = new AgpsStateMachine(loc_eng_data.agps_status_cb,
                                                  AGPS_TYPE_SUPL,
                                                  false);
#ifdef FEATURE_IPV6
    loc_eng_data.internet_nif = new AgpsStateMachine(loc_eng_data.agps_status_cb,
                                                     AGPS_TYPE_WWAN_ANY,
                                                     false);
    loc_eng_data.wifi_nif = new AgpsStateMachine(loc_eng_data.agps_status_cb,
                                                 AGPS_TYPE_WIFI,
                                                 true);
#endif

    loc_eng_dmn_conn_loc_api_server_launch(callbacks->create_thread_cb,
                                                   NULL, NULL, &loc_eng_data);

    loc_eng_agps_reinit(loc_eng_data);
    EXIT_LOG(%s, VOID_RET);
}

/*===========================================================================
FUNCTION    loc_eng_agps_open

DESCRIPTION
   This function is called when on-demand data connection opening is successful.
It should inform engine about the data open result.

DEPENDENCIES
   NONE

RETURN VALUE
   0

SIDE EFFECTS
   N/A

===========================================================================*/
#ifdef FEATURE_IPV6
int loc_eng_agps_open(loc_eng_data_s_type &loc_eng_data, AGpsType agpsType,
                     const char* apn, AGpsBearerType bearerType)
{
    ENTRY_LOG_CALLFLOW();
    INIT_CHECK(loc_eng_data.context && loc_eng_data.agps_status_cb,
               return -1);

    if (apn == NULL)
    {
        LOC_LOGE("APN Name NULL\n");
        return 0;
    }

    LOC_LOGD("loc_eng_agps_open APN name = [%s]", apn);

    int apn_len = smaller_of(strlen (apn), MAX_APN_LEN);
    loc_eng_msg_atl_open_success *msg(
        new loc_eng_msg_atl_open_success(&loc_eng_data, agpsType, apn,
                                        apn_len, bearerType));
    msg_q_snd((void*)((LocEngContext*)(loc_eng_data.context))->deferred_q,
              msg, loc_eng_free_msg);

    EXIT_LOG(%d, 0);
    return 0;
}
#else
int loc_eng_agps_open(loc_eng_data_s_type &loc_eng_data,
                     const char* apn)
{
    ENTRY_LOG_CALLFLOW();
    INIT_CHECK(loc_eng_data.context && loc_eng_data.agps_status_cb,
               return -1);

    if (apn == NULL)
    {
        LOC_LOGE("APN Name NULL\n");
        return 0;
    }

    LOC_LOGD("loc_eng_agps_open APN name = [%s]", apn);

    int apn_len = smaller_of(strlen (apn), MAX_APN_LEN);
    loc_eng_msg_atl_open_success *msg(
        new loc_eng_msg_atl_open_success(&loc_eng_data, apn,
                                        apn_len));
    msg_q_snd((void*)((LocEngContext*)(loc_eng_data.context))->deferred_q,
              msg, loc_eng_free_msg);

    EXIT_LOG(%d, 0);
    return 0;
}
#endif

/*===========================================================================
FUNCTION    loc_eng_agps_closed

DESCRIPTION
   This function is called when on-demand data connection closing is done.
It should inform engine about the data close result.

DEPENDENCIES
   NONE

RETURN VALUE
   0

SIDE EFFECTS
   N/A

===========================================================================*/
#ifdef FEATURE_IPV6
int loc_eng_agps_closed(loc_eng_data_s_type &loc_eng_data, AGpsType agpsType)
{
    ENTRY_LOG_CALLFLOW();
    INIT_CHECK(loc_eng_data.context && loc_eng_data.agps_status_cb,
               return -1);

    loc_eng_msg_atl_closed *msg(new loc_eng_msg_atl_closed(&loc_eng_data, agpsType));
    msg_q_snd((void*)((LocEngContext*)(loc_eng_data.context))->deferred_q,
              msg, loc_eng_free_msg);

    EXIT_LOG(%d, 0);
    return 0;
}
#else
int loc_eng_agps_closed(loc_eng_data_s_type &loc_eng_data)
{
    ENTRY_LOG_CALLFLOW();
    INIT_CHECK(loc_eng_data.context && loc_eng_data.agps_status_cb,
               return -1);

    loc_eng_msg_atl_closed *msg(new loc_eng_msg_atl_closed(&loc_eng_data));
    msg_q_snd((void*)((LocEngContext*)(loc_eng_data.context))->deferred_q,
              msg, loc_eng_free_msg);

    EXIT_LOG(%d, 0);
    return 0;
}
#endif

/*===========================================================================
FUNCTION    loc_eng_agps_open_failed

DESCRIPTION
   This function is called when on-demand data connection opening has failed.
It should inform engine about the data open result.

DEPENDENCIES
   NONE

RETURN VALUE
   0

SIDE EFFECTS
   N/A

===========================================================================*/
#ifdef FEATURE_IPV6
int loc_eng_agps_open_failed(loc_eng_data_s_type &loc_eng_data, AGpsType agpsType)
{
    ENTRY_LOG_CALLFLOW();
    INIT_CHECK(loc_eng_data.context && loc_eng_data.agps_status_cb,
               return -1);

    loc_eng_msg_atl_open_failed *msg(new loc_eng_msg_atl_open_failed(&loc_eng_data, agpsType));
    msg_q_snd((void*)((LocEngContext*)(loc_eng_data.context))->deferred_q,
              msg, loc_eng_free_msg);

    EXIT_LOG(%d, 0);
    return 0;
}
#else
int loc_eng_agps_open_failed(loc_eng_data_s_type &loc_eng_data)
{
    ENTRY_LOG_CALLFLOW();
    INIT_CHECK(loc_eng_data.context && loc_eng_data.agps_status_cb,
               return -1);

    loc_eng_msg_atl_open_failed *msg(new loc_eng_msg_atl_open_failed(&loc_eng_data));
    msg_q_snd((void*)((LocEngContext*)(loc_eng_data.context))->deferred_q,
              msg, loc_eng_free_msg);

    EXIT_LOG(%d, 0);
    return 0;
}
#endif

/*===========================================================================

FUNCTION resolve_in_addr

DESCRIPTION
   Translates a hostname to in_addr struct

DEPENDENCIES
   n/a

RETURN VALUE
   TRUE if successful

SIDE EFFECTS
   n/a

===========================================================================*/
static boolean resolve_in_addr(const char *host_addr, struct in_addr *in_addr_ptr)
{
    ENTRY_LOG();
    boolean ret_val = TRUE;

    struct hostent             *hp;
    hp = gethostbyname(host_addr);
    if (hp != NULL) /* DNS OK */
    {
        memcpy(in_addr_ptr, hp->h_addr_list[0], hp->h_length);
    }
    else
    {
        /* Try IP representation */
        if (inet_aton(host_addr, in_addr_ptr) == 0)
        {
            /* IP not valid */
            LOC_LOGE("DNS query on '%s' failed\n", host_addr);
            ret_val = FALSE;
        }
    }

    EXIT_LOG(%s, loc_logger_boolStr[ret_val!=0]);
    return ret_val;
}

/*===========================================================================
FUNCTION    loc_eng_set_server

DESCRIPTION
   This is used to set the default AGPS server. Server address is obtained
   from gps.conf.

DEPENDENCIES
   NONE

RETURN VALUE
   0

SIDE EFFECTS
   N/A

===========================================================================*/
static int loc_eng_set_server(loc_eng_data_s_type &loc_eng_data,
                              LocServerType type, const char* hostname, int port)
{
    ENTRY_LOG();
    int ret = 0;

    if (LOC_AGPS_SUPL_SERVER == type) {
        char url[MAX_URL_LEN];
        unsigned int len = snprintf(url, sizeof(url), "%s:%u", hostname, (unsigned) port);

        if (sizeof(url) > len) {
            loc_eng_msg_set_server_url *msg(new loc_eng_msg_set_server_url(&loc_eng_data,
                                                                           url, len));
            msg_q_snd((void*)((LocEngContext*)(loc_eng_data.context))->deferred_q,
                      msg, loc_eng_free_msg);
        }
    } else if (LOC_AGPS_CDMA_PDE_SERVER == type ||
               LOC_AGPS_CUSTOM_PDE_SERVER == type ||
               LOC_AGPS_MPC_SERVER == type) {
        struct in_addr addr;
        if (!resolve_in_addr(hostname, &addr))
        {
            LOC_LOGE("loc_eng_set_server, hostname %s cannot be resolved.\n", hostname);
            ret = -2;
        } else {
            unsigned int ip = htonl(addr.s_addr);
            loc_eng_msg_set_server_ipv4 *msg(new loc_eng_msg_set_server_ipv4(&loc_eng_data,
                                                                             ip,
                                                                             port,
                                                                             type));
            msg_q_snd((void*)((LocEngContext*)(loc_eng_data.context))->deferred_q,
                      msg, loc_eng_free_msg);
        }
    } else {
        LOC_LOGE("loc_eng_set_server, type %d cannot be resolved.\n", type);
    }

    EXIT_LOG(%d, ret);
    return ret;
}

/*===========================================================================
FUNCTION    loc_eng_set_server_proxy

DESCRIPTION
   If loc_eng_set_server is called before loc_eng_init, it doesn't work. This
   proxy buffers server settings and calls loc_eng_set_server when the client is
   open.

DEPENDENCIES
   NONE

RETURN VALUE
   0

SIDE EFFECTS
   N/A

===========================================================================*/
int loc_eng_set_server_proxy(loc_eng_data_s_type &loc_eng_data,
                             LocServerType type,
                             const char* hostname, int port)
{
    ENTRY_LOG_CALLFLOW();
    int ret_val = 0;

    if (NULL != loc_eng_data.context)
    {
        ret_val = loc_eng_set_server(loc_eng_data, type, hostname, port);
    } else {
        LOC_LOGW("set_server called before init. save the address, type: %d, hostname: %s, port: %d",
                 (int) type, hostname, port);
        switch (type)
        {
        case LOC_AGPS_SUPL_SERVER:
            strlcpy(loc_eng_data.supl_host_buf, hostname,
                    sizeof(loc_eng_data.supl_host_buf));
            loc_eng_data.supl_port_buf = port;
            loc_eng_data.supl_host_set = 1;
            break;
        case LOC_AGPS_CDMA_PDE_SERVER:
            strlcpy(loc_eng_data.c2k_host_buf, hostname,
                    sizeof(loc_eng_data.c2k_host_buf));
            loc_eng_data.c2k_port_buf = port;
            loc_eng_data.c2k_host_set = 1;
            break;
        default:
            LOC_LOGE("loc_eng_set_server_proxy, unknown server type = %d", (int) type);
        }
    }

    EXIT_LOG(%d, ret_val);
    return ret_val;
}

/*===========================================================================
FUNCTION    loc_eng_agps_ril_update_network_availability

DESCRIPTION
   Sets data call allow vs disallow flag to modem
   This is the only member of sLocEngAGpsRilInterface implemented.

DEPENDENCIES
   None

RETURN VALUE
   0: success

SIDE EFFECTS
   N/A

===========================================================================*/
void loc_eng_agps_ril_update_network_availability(loc_eng_data_s_type &loc_eng_data,
                                                  int available, const char* apn)
{
    ENTRY_LOG_CALLFLOW();
    INIT_CHECK(loc_eng_data.context, return);
    if (apn != NULL)
    {
        LOC_LOGD("loc_eng_agps_ril_update_network_availability: APN Name = [%s]\n", apn);
        int apn_len = smaller_of(strlen (apn), MAX_APN_LEN);
        loc_eng_msg_set_data_enable *msg(new loc_eng_msg_set_data_enable(&loc_eng_data, apn,
                                                                         apn_len, available));
        msg_q_snd((void*)((LocEngContext*)(loc_eng_data.context))->deferred_q,
                  msg, loc_eng_free_msg);
    }
    EXIT_LOG(%s, VOID_RET);
}

/*===========================================================================
FUNCTION    loc_eng_report_status

DESCRIPTION
   Reports GPS engine state to Java layer.

DEPENDENCIES
   N/A

RETURN VALUE
   N/A

SIDE EFFECTS
   N/A

===========================================================================*/
static void loc_eng_report_status (loc_eng_data_s_type &loc_eng_data, GpsStatusValue status)
{
    ENTRY_LOG();
    // Switch from WAIT to MUTE, for "engine on" or "session begin" event
    if (status == GPS_STATUS_SESSION_BEGIN || status == GPS_STATUS_ENGINE_ON)
    {
        if (loc_eng_data.mute_session_state == LOC_MUTE_SESS_WAIT)
        {
            LOC_LOGD("loc_eng_report_status: mute_session_state changed from WAIT to IN SESSION");
            loc_eng_data.mute_session_state = LOC_MUTE_SESS_IN_SESSION;
        }
    }

    // Switch off MUTE session
    if (loc_eng_data.mute_session_state == LOC_MUTE_SESS_IN_SESSION &&
        (status == GPS_STATUS_SESSION_END || status == GPS_STATUS_ENGINE_OFF))
    {
        LOC_LOGD("loc_eng_report_status: mute_session_state changed from IN SESSION to NONE");
        loc_eng_data.mute_session_state = LOC_MUTE_SESS_NONE;
    }

    // Session End is not reported during Android navigating state
    boolean navigating = loc_eng_data.client_handle->isInSession();
    if (status != GPS_STATUS_NONE &&
        !(status == GPS_STATUS_SESSION_END && navigating) &&
        !(status == GPS_STATUS_SESSION_BEGIN && !navigating))
    {
        if (loc_eng_data.mute_session_state != LOC_MUTE_SESS_IN_SESSION)
        {
            // Inform GpsLocationProvider about mNavigating status
            loc_inform_gps_status(loc_eng_data, status);
        }
        else {
            LOC_LOGD("loc_eng_report_status: muting the status report.");
        }
    }

    // Only keeps ENGINE ON/OFF in engine_status
    if (status == GPS_STATUS_ENGINE_ON || status == GPS_STATUS_ENGINE_OFF)
    {
        loc_eng_data.engine_status = status;
    }

    // Only keeps SESSION BEGIN/END in fix_session_status
    if (status == GPS_STATUS_SESSION_BEGIN || status == GPS_STATUS_SESSION_END)
    {
        loc_eng_data.fix_session_status = status;
    }
    EXIT_LOG(%s, VOID_RET);
}

/*===========================================================================
FUNCTION loc_eng_handle_engine_down
         loc_eng_handle_engine_up

DESCRIPTION
   Calls this function when it is detected that modem restart is happening.
   Either we detected the modem is down or received modem up event.
   This must be called from the deferred thread to avoid race condition.

DEPENDENCIES
   None

RETURN VALUE
   None

SIDE EFFECTS
   N/A

===========================================================================*/
void loc_eng_handle_engine_down(loc_eng_data_s_type &loc_eng_data)
{
    ENTRY_LOG();
    loc_eng_ni_reset_on_engine_restart(loc_eng_data);
    loc_eng_report_status(loc_eng_data, GPS_STATUS_ENGINE_OFF);
    EXIT_LOG(%s, VOID_RET);
}

void loc_eng_handle_engine_up(loc_eng_data_s_type &loc_eng_data)
{
    ENTRY_LOG();
    loc_eng_reinit(loc_eng_data);

    if (loc_eng_data.agps_status_cb != NULL) {
        loc_eng_data.agnss_nif->dropAllSubscribers();
#ifdef FEATURE_IPV6
        loc_eng_data.internet_nif->dropAllSubscribers();
#endif

        loc_eng_agps_reinit(loc_eng_data);
    }

    loc_eng_report_status(loc_eng_data, GPS_STATUS_ENGINE_ON);

    // modem is back up.  If we crashed in the middle of navigating, we restart.
    if (loc_eng_data.client_handle->isInSession()) {
        // This sets the copy in adapter to modem
        loc_eng_data.client_handle->setPositionMode(NULL);
        loc_eng_data.client_handle->setInSession(false);
        loc_eng_start_handler(loc_eng_data);
    }
    EXIT_LOG(%s, VOID_RET);
}

/*===========================================================================
FUNCTION loc_eng_deferred_action_thread

DESCRIPTION
   Main routine for the thread to execute loc_eng commands.

DEPENDENCIES
   None

RETURN VALUE
   None

SIDE EFFECTS
   N/A

===========================================================================*/
static void loc_eng_deferred_action_thread(void* arg)
{
    ENTRY_LOG();
    loc_eng_msg *msg;
    static int cnt = 0;
    LocEngContext* context = (LocEngContext*)arg;

    // make sure we do not run in background scheduling group
    set_sched_policy(gettid(), SP_FOREGROUND);

    while (1)
    {
        LOC_LOGD("%s:%d] %d listening ...\n", __func__, __LINE__, cnt++);

        // we are only sending / receiving msg pointers
        msq_q_err_type result = msg_q_rcv((void*)context->deferred_q, (void **) &msg);
        if (eMSG_Q_SUCCESS != result) {
            LOC_LOGE("%s:%d] fail receiving msg: %s\n", __func__, __LINE__,
                     loc_get_msg_q_status(result));
            return;
        }

        loc_eng_data_s_type* loc_eng_data_p = (loc_eng_data_s_type*)msg->owner;

        LOC_LOGD("%s:%d] received msg_id = %s context = %p\n",
                 __func__, __LINE__, loc_get_msg_name(msg->msgid), loc_eng_data_p->context);

        // need to ensure the instance data is valid
        STATE_CHECK(NULL != loc_eng_data_p->context,
                    "instance cleanup happened",
                    delete msg; return);

        switch(msg->msgid) {
        case LOC_ENG_MSG_QUIT:
        {
            LocEngContext* context = (LocEngContext*)loc_eng_data_p->context;
            pthread_mutex_lock(&(context->lock));
            pthread_cond_signal(&(context->cond));
            pthread_mutex_unlock(&(context->lock));
            EXIT_LOG(%s, "LOC_ENG_MSG_QUIT, signal the main thread and return");
        }
        return;

        case LOC_ENG_MSG_REQUEST_NI:
        {
            loc_eng_msg_request_ni *niMsg = (loc_eng_msg_request_ni*)msg;
            loc_eng_ni_request_handler(*loc_eng_data_p, &niMsg->notify, niMsg->passThroughData);
        }
        break;

        case LOC_ENG_MSG_INFORM_NI_RESPONSE:
        {
            loc_eng_msg_inform_ni_response *nrMsg = (loc_eng_msg_inform_ni_response*)msg;
            loc_eng_data_p->client_handle->informNiResponse(nrMsg->response,
                                                            nrMsg->passThroughData);
        }
        break;

        case LOC_ENG_MSG_START_FIX:
            loc_eng_start_handler(*loc_eng_data_p);
            break;

        case LOC_ENG_MSG_STOP_FIX:
            if (loc_eng_data_p->agps_request_pending)
            {
                loc_eng_data_p->stop_request_pending = true;
                LOC_LOGD("loc_eng_stop - deferring stop until AGPS data call is finished\n");
            } else {
                loc_eng_stop_handler(*loc_eng_data_p);
            }
            break;

        case LOC_ENG_MSG_SET_POSITION_MODE:
        {
            loc_eng_msg_position_mode *pmMsg = (loc_eng_msg_position_mode*)msg;
            loc_eng_data_p->client_handle->setPositionMode(&(pmMsg->pMode));
        }
        break;

        case LOC_ENG_MSG_SET_TIME:
        {
            loc_eng_msg_set_time *tMsg = (loc_eng_msg_set_time*)msg;
            loc_eng_data_p->client_handle->setTime(tMsg->time, tMsg->timeReference,
                                                   tMsg->uncertainty);
        }
        break;

        case LOC_ENG_MSG_INJECT_LOCATION:
        {
            loc_eng_msg_inject_location *ilMsg = (loc_eng_msg_inject_location*) msg;
            loc_eng_data_p->client_handle->injectPosition(ilMsg->latitude, ilMsg->longitude,
                                                          ilMsg->accuracy);
        }
        break;

        case LOC_ENG_MSG_SET_SERVER_IPV4:
        {
            loc_eng_msg_set_server_ipv4 *ssiMsg = (loc_eng_msg_set_server_ipv4*)msg;
            loc_eng_data_p->client_handle->setServer(ssiMsg->nl_addr,
                                                     ssiMsg->port,
                                                     ssiMsg->serverType);
        }
        break;

        case LOC_ENG_MSG_SET_SERVER_URL:
        {
            loc_eng_msg_set_server_url *ssuMsg = (loc_eng_msg_set_server_url*)msg;
            loc_eng_data_p->client_handle->setServer(ssuMsg->url, ssuMsg->len);
        }
        break;

        case LOC_ENG_MSG_A_GLONASS_PROTOCOL:
        {
            loc_eng_msg_a_glonass_protocol *svMsg = (loc_eng_msg_a_glonass_protocol*)msg;
            loc_eng_data_p->client_handle->setAGLONASSProtocol(svMsg->a_glonass_protocol);
        }
        break;

        case LOC_ENG_MSG_SUPL_VERSION:
        {
            loc_eng_msg_suple_version *svMsg = (loc_eng_msg_suple_version*)msg;
            loc_eng_data_p->client_handle->setSUPLVersion(svMsg->supl_version);
        }
        break;

        case LOC_ENG_MSG_LPP_CONFIG:
        {
            loc_eng_msg_lpp_config *svMsg = (loc_eng_msg_lpp_config*)msg;
            loc_eng_data_p->client_handle->setLPPConfig(svMsg->lpp_config);
        }
        break;

        case LOC_ENG_MSG_SET_SENSOR_CONTROL_CONFIG:
        {
            loc_eng_msg_sensor_control_config *sccMsg = (loc_eng_msg_sensor_control_config*)msg;
            loc_eng_data_p->client_handle->setSensorControlConfig(sccMsg->sensorsDisabled);
        }
        break;

        case LOC_ENG_MSG_SET_SENSOR_PROPERTIES:
        {
            loc_eng_msg_sensor_properties *spMsg = (loc_eng_msg_sensor_properties*)msg;
            loc_eng_data_p->client_handle->setSensorProperties(spMsg->gyroBiasVarianceRandomWalk_valid,
                                                               spMsg->gyroBiasVarianceRandomWalk,
                                                               spMsg->accelRandomWalk_valid,
                                                               spMsg->accelRandomWalk,
                                                               spMsg->angleRandomWalk_valid,
                                                               spMsg->angleRandomWalk,
                                                               spMsg->rateRandomWalk_valid,
                                                               spMsg->rateRandomWalk,
                                                               spMsg->velocityRandomWalk_valid,
                                                               spMsg->velocityRandomWalk);
        }
        break;

        case LOC_ENG_MSG_SET_SENSOR_PERF_CONTROL_CONFIG:
        {
            loc_eng_msg_sensor_perf_control_config *spccMsg = (loc_eng_msg_sensor_perf_control_config*)msg;
            loc_eng_data_p->client_handle->setSensorPerfControlConfig(spccMsg->controlMode, spccMsg->accelSamplesPerBatch, spccMsg->accelBatchesPerSec,
                                                                      spccMsg->gyroSamplesPerBatch, spccMsg->gyroBatchesPerSec,
                                                                      spccMsg->accelSamplesPerBatchHigh, spccMsg->accelBatchesPerSecHigh,
                                                                      spccMsg->gyroSamplesPerBatchHigh, spccMsg->gyroBatchesPerSecHigh,
                                                                      spccMsg->algorithmConfig);
        }
        break;

        case LOC_ENG_MSG_EXT_POWER_CONFIG:
        {
            loc_eng_msg_ext_power_config *pwrMsg = (loc_eng_msg_ext_power_config*)msg;
            loc_eng_data_p->client_handle->setExtPowerConfig(pwrMsg->isBatteryCharging);
        }
        break;

        case LOC_ENG_MSG_REPORT_POSITION:
            if (loc_eng_data_p->mute_session_state != LOC_MUTE_SESS_IN_SESSION)
            {
                bool reported = false;
                loc_eng_msg_report_position *rpMsg = (loc_eng_msg_report_position*)msg;
                if (loc_eng_data_p->location_cb != NULL) {
                    if (LOC_SESS_FAILURE == rpMsg->status) {
                        // in case we want to handle the failure case
                        loc_eng_data_p->location_cb(NULL, NULL);
                        reported = true;
                    }
                    // what's in the else if is... (line by line)
                    // 1. this is a good fix; or
                    //   1.1 there is source info; or
                    //   1.1.1 this is from hybrid provider;
                    //   1.2 it is a Satellite fix; or
                    //   1.2.1 it is a sensor fix
                    // 2. (must be intermediate fix... implicit)
                    //   2.1 we accepte intermediate; and
                    //   2.2 it is NOT the case that
                    //   2.2.1 there is inaccuracy; and
                    //   2.2.2 we care about inaccuracy; and
                    //   2.2.3 the inaccuracy exceeds our tolerance
                    else if ((LOC_SESS_SUCCESS == rpMsg->status && (
#ifdef FEATURE_ULP
                               ((LOCATION_HAS_SOURCE_INFO & rpMsg->location.flags) &&
                                ULP_LOCATION_IS_FROM_HYBRID == rpMsg->location.position_source) ||
#endif
                               ((LOC_POS_TECH_MASK_SATELLITE & rpMsg->technology_mask) ||
                                (LOC_POS_TECH_MASK_SENSORS & rpMsg->technology_mask)))) ||
                             (LOC_SESS_INTERMEDIATE == loc_eng_data_p->intermediateFix &&
                              !((rpMsg->location.flags & GPS_LOCATION_HAS_ACCURACY) &&
                                (gps_conf.ACCURACY_THRES != 0) &&
                                (rpMsg->location.accuracy > gps_conf.ACCURACY_THRES)))) {
                        loc_eng_data_p->location_cb((GpsLocation*)&(rpMsg->location),
                                                    (void*)rpMsg->locationExt);
                        reported = true;
                    }
                }

                // if we have reported this fix
                if (reported &&
                    // and if this is a singleshot
                    GPS_POSITION_RECURRENCE_SINGLE ==
                    loc_eng_data_p->client_handle->getPositionMode().recurrence) {
                    if (LOC_SESS_INTERMEDIATE == rpMsg->status) {
                        // modem could be still working for a final fix,
                        // although we no longer need it.  So stopFix().
                        loc_eng_data_p->client_handle->stopFix();
                    }
                    // turn off the session flag.
                    loc_eng_data_p->client_handle->setInSession(false);
                }

#ifdef FEATURE_ULP
                if (loc_eng_data_p->generateNmea && rpMsg->location.position_source == ULP_LOCATION_IS_FROM_GNSS)
                {
                    loc_eng_nmea_generate_pos(loc_eng_data_p, rpMsg->location, rpMsg->locationExtended);
                }
#else
                if (loc_eng_data_p->generateNmea && (LOC_POS_TECH_MASK_SATELLITE & rpMsg->technology_mask))
                {
                    loc_eng_nmea_generate_pos(loc_eng_data_p, rpMsg->location, rpMsg->locationExtended);
                }
#endif

#ifdef FEATURE_ULP
                // Free the allocated memory for rawData
                GpsLocation* gp = (GpsLocation*)&(rpMsg->location);
                if (gp != NULL && gp->rawData != NULL)
                {
                    delete (char*)gp->rawData;
                    gp->rawData = NULL;
                    gp->rawDataSize = 0;
                }
#endif
            }

            break;

        case LOC_ENG_MSG_REPORT_SV:
            if (loc_eng_data_p->mute_session_state != LOC_MUTE_SESS_IN_SESSION)
            {
                loc_eng_msg_report_sv *rsMsg = (loc_eng_msg_report_sv*)msg;
                if (loc_eng_data_p->sv_status_cb != NULL) {
                    loc_eng_data_p->sv_status_cb((GpsSvStatus*)&(rsMsg->svStatus),
                                                 (void*)rsMsg->svExt);
                }

                if (loc_eng_data_p->generateNmea)
                {
                    loc_eng_nmea_generate_sv(loc_eng_data_p, rsMsg->svStatus, rsMsg->locationExtended);
                }

            }
            break;

        case LOC_ENG_MSG_REPORT_STATUS:
            loc_eng_report_status(*loc_eng_data_p, ((loc_eng_msg_report_status*)msg)->status);
            break;

        case LOC_ENG_MSG_REPORT_NMEA:
            if (NULL != loc_eng_data_p->nmea_cb) {
                loc_eng_msg_report_nmea* nmMsg = (loc_eng_msg_report_nmea*)msg;
                struct timeval tv;
                gettimeofday(&tv, (struct timezone *) NULL);
                int64_t now = tv.tv_sec * 1000LL + tv.tv_usec / 1000;
                CALLBACK_LOG_CALLFLOW("nmea_cb", %p, nmMsg->nmea);
                loc_eng_data_p->nmea_cb(now, nmMsg->nmea, nmMsg->length);
            }
            break;

        case LOC_ENG_MSG_REQUEST_BIT:
        {
            AgpsStateMachine* stateMachine;
            loc_eng_msg_request_bit* brqMsg = (loc_eng_msg_request_bit*)msg;
            if (brqMsg->ifType == LOC_ENG_IF_REQUEST_TYPE_SUPL) {
                stateMachine = loc_eng_data_p->agnss_nif;
#ifdef FEATURE_IPV6
            } else if (brqMsg->ifType == LOC_ENG_IF_REQUEST_TYPE_ANY) {
                stateMachine = loc_eng_data_p->internet_nif;
#endif
            } else {
                LOC_LOGD("%s]%d: unknown I/F request type = 0x%x\n", __func__, __LINE__, brqMsg->ifType);
                break;
            }
            BITSubscriber subscriber(stateMachine, brqMsg->ipv4Addr, brqMsg->ipv6Addr);

            stateMachine->subscribeRsrc((Subscriber*)&subscriber);
        }
        break;

        case LOC_ENG_MSG_RELEASE_BIT:
        {
            AgpsStateMachine* stateMachine;
            loc_eng_msg_release_bit* brlMsg = (loc_eng_msg_release_bit*)msg;
            if (brlMsg->ifType == LOC_ENG_IF_REQUEST_TYPE_SUPL) {
                stateMachine = loc_eng_data_p->agnss_nif;
#ifdef FEATURE_IPV6
            } else if (brlMsg->ifType == LOC_ENG_IF_REQUEST_TYPE_ANY) {
                stateMachine = loc_eng_data_p->internet_nif;
#endif
            } else {
                LOC_LOGD("%s]%d: unknown I/F request type = 0x%x\n", __func__, __LINE__, brlMsg->ifType);
                break;
            }
            BITSubscriber subscriber(stateMachine, brlMsg->ipv4Addr, brlMsg->ipv6Addr);

            stateMachine->unsubscribeRsrc((Subscriber*)&subscriber);
        }
        break;

        case LOC_ENG_MSG_REQUEST_ATL:
        {
            loc_eng_msg_request_atl* arqMsg = (loc_eng_msg_request_atl*)msg;
#ifdef FEATURE_IPV6
            boolean backwardCompatibleMode = AGPS_TYPE_INVALID == arqMsg->type;
            AgpsStateMachine* stateMachine = (AGPS_TYPE_SUPL == arqMsg->type ||
                                              backwardCompatibleMode) ?
                                             loc_eng_data_p->agnss_nif :
                                             loc_eng_data_p->internet_nif;
#else
            boolean backwardCompatibleMode = false;
            AgpsStateMachine* stateMachine = loc_eng_data_p->agnss_nif;
#endif
            ATLSubscriber subscriber(arqMsg->handle,
                                     stateMachine,
                                     loc_eng_data_p->client_handle,
                                     backwardCompatibleMode);

            stateMachine->subscribeRsrc((Subscriber*)&subscriber);
        }
        break;

        case LOC_ENG_MSG_RELEASE_ATL:
        {
            loc_eng_msg_release_atl* arlMsg = (loc_eng_msg_release_atl*)msg;
            ATLSubscriber s1(arlMsg->handle,
                             loc_eng_data_p->agnss_nif,
                             loc_eng_data_p->client_handle,
                             false);
            // attempt to unsubscribe from agnss_nif first
            if (! loc_eng_data_p->agnss_nif->unsubscribeRsrc((Subscriber*)&s1)) {
#ifdef FEATURE_IPV6
                ATLSubscriber s2(arlMsg->handle,
                                 loc_eng_data_p->internet_nif,
                                 loc_eng_data_p->client_handle,
                                 false);
                // if unsuccessful, try internet_nif
                loc_eng_data_p->internet_nif->unsubscribeRsrc((Subscriber*)&s2);
#endif
            }
        }
        break;

#ifdef FEATURE_IPV6
        case LOC_ENG_MSG_REQUEST_WIFI:
        {
            loc_eng_msg_request_wifi *wrqMsg = (loc_eng_msg_request_wifi *)msg;
            if (wrqMsg->senderId == LOC_ENG_IF_REQUEST_SENDER_ID_QUIPC ||
                wrqMsg->senderId == LOC_ENG_IF_REQUEST_SENDER_ID_MSAPM ||
                wrqMsg->senderId == LOC_ENG_IF_REQUEST_SENDER_ID_MSAPU) {
              AgpsStateMachine* stateMachine = loc_eng_data_p->wifi_nif;
              WIFISubscriber subscriber(stateMachine, wrqMsg->ssid, wrqMsg->password, wrqMsg->senderId);
              stateMachine->subscribeRsrc((Subscriber*)&subscriber);
            } else {
              LOC_LOGE("%s]%d ERROR: unknown sender ID", __func__, __LINE__);
              break;
            }
        }
        break;

        case LOC_ENG_MSG_RELEASE_WIFI:
        {
            AgpsStateMachine* stateMachine = loc_eng_data_p->wifi_nif;
            loc_eng_msg_release_wifi* wrlMsg = (loc_eng_msg_release_wifi*)msg;
            WIFISubscriber subscriber(stateMachine, wrlMsg->ssid, wrlMsg->password, wrlMsg->senderId);
            stateMachine->unsubscribeRsrc((Subscriber*)&subscriber);
        }
        break;
#endif

        case LOC_ENG_MSG_REQUEST_XTRA_DATA:
            if (loc_eng_data_p->xtra_module_data.download_request_cb != NULL)
            {
                loc_eng_data_p->xtra_module_data.download_request_cb();
            }
            break;

        case LOC_ENG_MSG_REQUEST_TIME:
            if (loc_eng_data_p->request_utc_time_cb != NULL)
            {
                loc_eng_data_p->request_utc_time_cb();
            }
            else
            {
                LOC_LOGE("%s] ERROR: Callback function for request_time is NULL", __func__);
            }
            break;

        case LOC_ENG_MSG_REQUEST_POSITION:
            break;

        case LOC_ENG_MSG_DELETE_AIDING_DATA:
            loc_eng_data_p->aiding_data_for_deletion |= ((loc_eng_msg_delete_aiding_data*)msg)->type;
            break;

        case LOC_ENG_MSG_ENABLE_DATA:
        {
            loc_eng_msg_set_data_enable *unaMsg = (loc_eng_msg_set_data_enable*)msg;
            loc_eng_data_p->client_handle->enableData(unaMsg->enable);
            loc_eng_data_p->client_handle->setAPN(unaMsg->apn, unaMsg->length);
        }
        break;

        case LOC_ENG_MSG_INJECT_XTRA_DATA:
        {
            loc_eng_msg_inject_xtra_data *xdMsg = (loc_eng_msg_inject_xtra_data*)msg;
            loc_eng_data_p->client_handle->setXtraData(xdMsg->data, xdMsg->length);
        }
        break;

        case LOC_ENG_MSG_ATL_OPEN_SUCCESS:
        {
            loc_eng_msg_atl_open_success *aosMsg = (loc_eng_msg_atl_open_success*)msg;
            AgpsStateMachine* stateMachine;
#ifdef FEATURE_IPV6
            switch (aosMsg->agpsType) {
              case AGPS_TYPE_WIFI: {
                stateMachine = loc_eng_data_p->wifi_nif;
                break;
              }
              case AGPS_TYPE_SUPL: {
                stateMachine = loc_eng_data_p->agnss_nif;
                break;
              }
              default: {
                stateMachine  = loc_eng_data_p->internet_nif;
              }
            }

            stateMachine->setBearer(aosMsg->bearerType);
#else
            stateMachine = loc_eng_data_p->agnss_nif;
#endif
            stateMachine->setAPN(aosMsg->apn, aosMsg->length);
            stateMachine->onRsrcEvent(RSRC_GRANTED);
        }
        break;

        case LOC_ENG_MSG_ATL_CLOSED:
        {
            loc_eng_msg_atl_closed *acsMsg = (loc_eng_msg_atl_closed*)msg;
            AgpsStateMachine* stateMachine;
#ifdef FEATURE_IPV6
            switch (acsMsg->agpsType) {
              case AGPS_TYPE_WIFI: {
                stateMachine = loc_eng_data_p->wifi_nif;
                break;
              }
              case AGPS_TYPE_SUPL: {
                stateMachine = loc_eng_data_p->agnss_nif;
                break;
              }
              default: {
                stateMachine  = loc_eng_data_p->internet_nif;
              }
            }
#else
            stateMachine = loc_eng_data_p->agnss_nif;
#endif
            stateMachine->onRsrcEvent(RSRC_RELEASED);
        }
        break;

        case LOC_ENG_MSG_ATL_OPEN_FAILED:
        {
            loc_eng_msg_atl_open_failed *aofMsg = (loc_eng_msg_atl_open_failed*)msg;
            AgpsStateMachine* stateMachine;
#ifdef FEATURE_IPV6
            switch (aofMsg->agpsType) {
              case AGPS_TYPE_WIFI: {
                stateMachine = loc_eng_data_p->wifi_nif;
                break;
              }
              case AGPS_TYPE_SUPL: {
                stateMachine = loc_eng_data_p->agnss_nif;
                break;
              }
              default: {
                stateMachine  = loc_eng_data_p->internet_nif;
              }
            }
#else
            stateMachine = loc_eng_data_p->agnss_nif;
#endif
            stateMachine->onRsrcEvent(RSRC_DENIED);
        }
        break;

        case LOC_ENG_MSG_ENGINE_DOWN:
            loc_eng_handle_engine_down(*loc_eng_data_p);
            break;

        case LOC_ENG_MSG_ENGINE_UP:
            loc_eng_handle_engine_up(*loc_eng_data_p);
            break;

#ifdef FEATURE_ULP
        case LOC_ENG_MSG_REQUEST_NETWORK_POSIITON:
        {
            loc_eng_msg_request_network_position *nlprequestmsg = (loc_eng_msg_request_network_position*)msg;
            //loc_eng_handle_request_network_position(nlprequestmsg );
            LOC_LOGD("Received n/w position request from ULP.Request type %d Periodicity: %d\n",
                     nlprequestmsg->networkPosRequest.request_type,
                      nlprequestmsg->networkPosRequest.interval_ms);
            if(loc_eng_data_p->ulp_network_callback != NULL)
            {
                loc_eng_data_p->ulp_network_callback((UlpNetworkRequestPos*)&(nlprequestmsg->networkPosRequest));
            }
            else
                LOC_LOGE("Ulp Network call back not initialized");
        }
        break;

        case LOC_ENG_MSG_REQUEST_PHONE_CONTEXT:
        {
            loc_eng_msg_request_phone_context *contextReqMsg = (loc_eng_msg_request_phone_context*)msg;
            LOC_LOGD("Received phone context request from ULP.context_type 0x%x,request_type 0x%x  ",
                     contextReqMsg->contextRequest.context_type,contextReqMsg->contextRequest.request_type)
            if(loc_eng_data_p->ulp_phone_context_req_cb != NULL)
            {
                loc_eng_data_p->ulp_phone_context_req_cb((UlpPhoneContextRequest*)&(contextReqMsg->contextRequest));
            }
            else
                LOC_LOGE("Ulp Phone context request call back not initialized");
            }
        break;
#endif
        default:
            LOC_LOGE("unsupported msgid = %d\n", msg->msgid);
            break;
        }

        if ( (msg->msgid == LOC_ENG_MSG_ATL_OPEN_FAILED)  |
             (msg->msgid == LOC_ENG_MSG_ATL_CLOSED)  |
             (msg->msgid == LOC_ENG_MSG_ATL_OPEN_SUCCESS) )
        {
            loc_eng_data_p->agps_request_pending = false;
            if (loc_eng_data_p->stop_request_pending) {
                loc_eng_stop_handler(*loc_eng_data_p);
                loc_eng_data_p->stop_request_pending = false;
            }
        }
        loc_eng_data_p->stop_request_pending = false;

        if (loc_eng_data_p->engine_status != GPS_STATUS_ENGINE_ON &&
            loc_eng_data_p->aiding_data_for_deletion != 0)
        {
            loc_eng_data_p->client_handle->deleteAidingData(loc_eng_data_p->aiding_data_for_deletion);
            loc_eng_data_p->aiding_data_for_deletion = 0;
        }

        delete msg;
    }

    EXIT_LOG(%s, VOID_RET);
}
#ifdef FEATURE_ULP
/*===========================================================================
FUNCTION loc_eng_ulp_init

DESCRIPTION
   This function dynamically loads the libulp.so and calls
   its init function to start up the ulp module

DEPENDENCIES
   None

RETURN VALUE
   0: no error
   -1: errors

SIDE EFFECTS
   N/A

===========================================================================*/
int loc_eng_ulp_init(loc_eng_data_s_type &loc_eng_data, const ulpInterface * loc_eng_ulpInf)
{
    ENTRY_LOG();
    int ret_val=-1;
    if((loc_eng_ulpInf != NULL) && (((ulpInterface *)loc_eng_ulpInf)->init != NULL))
    {
        // Initialize the ULP interface
        ((ulpInterface *)loc_eng_ulpInf)->init(loc_eng_data);
        loc_eng_data.ulp_initialized = TRUE;
        ret_val = 0;
    }
    else
        LOC_LOGE("ulp not initialized. NULL parameter");
    EXIT_LOG(%d, ret_val);
    return ret_val;
}

/*===========================================================================
FUNCTION    loc_eng_inject_raw_command

DESCRIPTION
   This is used to send special test modem commands from the applications
   down into the HAL
DEPENDENCIES
   N/A

RETURN VALUE
   0: success

SIDE EFFECTS
   N/A

===========================================================================*/
bool loc_eng_inject_raw_command(loc_eng_data_s_type &loc_eng_data,
                                char* command, int length)
{
    ENTRY_LOG_CALLFLOW();
    INIT_CHECK(loc_eng_data.context, return -1);
    boolean ret_val;
    LOC_LOGD("loc_eng_send_extra_command: %s\n", command);
    ret_val = TRUE;

    if((loc_eng_data.ulp_initialized == true) && (gps_conf.CAPABILITIES & ULP_CAPABILITY))
    {
        ulp_msg_inject_raw_command *msg(
            new ulp_msg_inject_raw_command(&loc_eng_data,command, length));
        msg_q_snd( (void*)((LocEngContext*)(loc_eng_data.context))->ulp_q
                   , msg, loc_eng_free_msg);
        ret_val = 0;
    }else
    {
        ret_val = -1;
    }


    EXIT_LOG(%s, loc_logger_boolStr[ret_val!=0]);
    return ret_val;
}
/*===========================================================================
FUNCTION    loc_eng_update_criteria

DESCRIPTION
   This is used to inform the ULP module of new unique criteria that are passed
   in by the applications
DEPENDENCIES
   N/A

RETURN VALUE
   0: success

SIDE EFFECTS
   N/A

===========================================================================*/
int loc_eng_update_criteria(loc_eng_data_s_type &loc_eng_data,
                            UlpLocationCriteria criteria)
{
    ENTRY_LOG_CALLFLOW();
    INIT_CHECK(loc_eng_data.context, return -1);
    int ret_val;

    if((loc_eng_data.ulp_initialized == true) && (gps_conf.CAPABILITIES & ULP_CAPABILITY))
    {
       LOC_LOGD("SJ:loc_eng_update_criteria: valid 0x%x action:%d, minTime:%ld, minDistance:%f, singleShot:%d, horizontalAccuracy:%d, powerRequirement:%d \n",
         criteria.valid_mask, criteria.action, criteria.min_interval, criteria.min_distance,  criteria.recurrence_type,  criteria.preferred_horizontal_accuracy,
              criteria.preferred_power_consumption );
     ulp_msg_update_criteria *msg(
         new ulp_msg_update_criteria(&loc_eng_data,criteria));
     msg_q_snd( (void*)((LocEngContext*)(loc_eng_data.context))->ulp_q
                , msg, loc_eng_free_msg);
     ret_val = 0;
    }else
    {
        ret_val = -1;
    }
    EXIT_LOG(%d, ret_val);
    return ret_val;
}

/*===========================================================================
FUNCTION    loc_eng_ulp_phone_context_settings_update

DESCRIPTION
   This is used to inform the ULP module of phone settings changes carried out
   by the users
DEPENDENCIES
   N/A

RETURN VALUE
   0: success

SIDE EFFECTS
   N/A

===========================================================================*/

int loc_eng_ulp_phone_context_settings_update(loc_eng_data_s_type &loc_eng_data,
                                              UlpPhoneContextSettings *settings)
{
    ENTRY_LOG();
    int ret_val = -1;

    LOC_LOGD("loc_eng_ulp_phone_context_settings: context_type - 0x%x is_agps_enabled - %d "
             "is_battery_charging %d ,is_gps_enabled %d, is_network_position_available %d,"
             "is_wifi_setting_enabled %d, is_agps_setting_enabled %d, is_enh_location_services_enabled %d\n",
             settings->context_type ,settings->is_agps_enabled,settings->is_battery_charging,
             settings->is_gps_enabled, settings->is_network_position_available,
             settings->is_wifi_setting_enabled, settings->is_agps_enabled,
             settings->is_enh_location_services_enabled );

    if((loc_eng_data.ulp_initialized == true) && (gps_conf.CAPABILITIES & ULP_CAPABILITY))
    {
        ulp_msg_inject_phone_context_settings *msg
         (new ulp_msg_inject_phone_context_settings(&loc_eng_data, *settings));
        msg_q_snd( (void*)((LocEngContext*)(loc_eng_data.context))->ulp_q, msg, loc_eng_free_msg);
        ret_val = 0;
    }

    // Send battery information to modem for processing.
    if(settings->context_type & ULP_PHONE_CONTEXT_BATTERY_CHARGING_STATE)
    {
        loc_eng_msg_ext_power_config *msg(new loc_eng_msg_ext_power_config(&loc_eng_data, settings->is_battery_charging));
        msg_q_snd( (void*)((LocEngContext*)(loc_eng_data.context))->deferred_q, msg, loc_eng_free_msg);
    }

    EXIT_LOG(%d, ret_val);
    return ret_val;
}
/*===========================================================================
FUNCTION    loc_eng_ulp_network_init

DESCRIPTION
   Initialize the ULP network interface.

DEPENDENCIES
   NONE

RETURN VALUE
   0

SIDE EFFECTS
   N/A

===========================================================================*/
int loc_eng_ulp_phone_context_init(loc_eng_data_s_type &loc_eng_data,UlpPhoneContextCallbacks *callback)
{
    int ret_val = -1;
    ENTRY_LOG();
    if(callback != NULL) {
        loc_eng_data.ulp_phone_context_req_cb = callback->ulp_request_phone_context_cb ;
        ret_val = 0;
    }
    else
        LOC_LOGE("loc_eng_ulp_phone_context_init: bad parameters cb %p", callback);
    EXIT_LOG(%d, ret_val);
    return ret_val;
}

/*===========================================================================
FUNCTION    loc_eng_ulp_network_init

DESCRIPTION
   Initialize the ULP network interface.

DEPENDENCIES
   NONE

RETURN VALUE
   0

SIDE EFFECTS
   N/A

===========================================================================*/
int loc_eng_ulp_network_init(loc_eng_data_s_type &loc_eng_data,
                             UlpNetworkLocationCallbacks *callbacks)
{
    int ret_val = -1;
    ENTRY_LOG_CALLFLOW();
    if(callbacks != NULL) {
        loc_eng_data.ulp_network_callback = callbacks->ulp_network_location_request_cb;
        ret_val = 0;
    }
    else
        LOC_LOGE("loc_eng_ulp_network_init: bad parameters cb %p", callbacks);
    EXIT_LOG(%d, ret_val);
    return ret_val;
}


/*===========================================================================
FUNCTION    loc_eng_ulp_send_network_position

DESCRIPTION
   Ulp send data

DEPENDENCIES
   NONE

RETURN VALUE
   0

SIDE EFFECTS
   N/A

===========================================================================*/
int loc_eng_ulp_send_network_position(loc_eng_data_s_type &loc_eng_data,
                                             UlpNetworkPositionReport *position_report)
{
    ENTRY_LOG();
    int ret_val = 0;
    if((loc_eng_data.ulp_initialized == true) && (gps_conf.CAPABILITIES & ULP_CAPABILITY))
    {
     ulp_msg_inject_network_position *msg
         (new ulp_msg_inject_network_position(&loc_eng_data, *position_report));
     msg_q_snd( (void*)((LocEngContext*)(loc_eng_data.context))->ulp_q
                , msg, loc_eng_free_msg);
     ret_val = 0;
    }else
    {
        ret_val = -1;
    }
    EXIT_LOG(%d, ret_val);
    return ret_val;
}
#endif
/*===========================================================================
FUNCTION    loc_eng_read_config

DESCRIPTION
   Initiates the reading of the gps config file stored in /etc dir

DEPENDENCIES
   None

RETURN VALUE
   0: success

SIDE EFFECTS
   N/A

===========================================================================*/
int loc_eng_read_config(void)
{
    ENTRY_LOG_CALLFLOW();
    if(gpsConfigAlreadyRead == false)
    {
      // Initialize our defaults before reading of configuration file overwrites them.
      loc_default_parameters();
      // Ee only want to parse the conf file once. This is a good place to ensure that.
      // In fact one day the conf file should go into context.
      UTIL_READ_CONF(GPS_CONF_FILE, loc_parameter_table);
      gpsConfigAlreadyRead = true;
    } else {
      LOC_LOGV("GPS Config file has already been read\n");
    }

    EXIT_LOG(%d, 0);
    return 0;
}

