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
#ifndef LOC_ENG_MSG_H
#define LOC_ENG_MSG_H


#include <hardware/gps.h>
#include <stdlib.h>
#include <string.h>
#include "log_util.h"
#include "loc.h"
#include <loc_eng_log.h>
#include "loc_eng_msg_id.h"

#ifndef SSID_BUF_SIZE
    #define SSID_BUF_SIZE (32+1)
#endif

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

struct LocPosMode
{
    LocPositionMode mode;
    GpsPositionRecurrence recurrence;
    uint32_t min_interval;
    uint32_t preferred_accuracy;
    uint32_t preferred_time;
    char credentials[14];
    char provider[8];
    LocPosMode(LocPositionMode m, GpsPositionRecurrence recr,
               uint32_t gap, uint32_t accu, uint32_t time,
               const char* cred, const char* prov) :
        mode(m), recurrence(recr),
        min_interval(gap < MIN_POSSIBLE_FIX_INTERVAL ? MIN_POSSIBLE_FIX_INTERVAL : gap),
        preferred_accuracy(accu), preferred_time(time) {
        memset(credentials, 0, sizeof(credentials));
        memset(provider, 0, sizeof(provider));
        if (NULL != cred) {
            memcpy(credentials, cred, sizeof(credentials)-1);
        }
        if (NULL != prov) {
            memcpy(provider, prov, sizeof(provider)-1);
        }
    }

    LocPosMode() :
        mode(LOC_POSITION_MODE_MS_BASED), recurrence(GPS_POSITION_RECURRENCE_PERIODIC),
        min_interval(MIN_POSSIBLE_FIX_INTERVAL), preferred_accuracy(50), preferred_time(120000) {
        memset(credentials, 0, sizeof(credentials));
        memset(provider, 0, sizeof(provider));
    }

    inline bool equals(const LocPosMode &anotherMode) const
    {
        return anotherMode.mode == mode &&
            anotherMode.recurrence == recurrence &&
            anotherMode.min_interval == min_interval &&
            anotherMode.preferred_accuracy == preferred_accuracy &&
            anotherMode.preferred_time == preferred_time &&
            !strncmp(anotherMode.credentials, credentials, sizeof(credentials)-1) &&
            !strncmp(anotherMode.provider, provider, sizeof(provider)-1);
    }

    inline void logv() const
    {
        LOC_LOGV ("Position mode: %s\n  Position recurrence: %s\n  min interval: %d\n  preferred accuracy: %d\n  preferred time: %d\n  credentials: %s  provider: %s",
                  loc_get_position_mode_name(mode),
                  loc_get_position_recurrence_name(recurrence),
                  min_interval,
                  preferred_accuracy,
                  preferred_time,
                  credentials,
                  provider);
    }
};

/** Flags to indicate which values are valid in a GpsLocationExtended. */
typedef uint16_t GpsLocationExtendedFlags;
/** GpsLocationExtended has valid pdop, hdop, vdop. */
#define GPS_LOCATION_EXTENDED_HAS_DOP 0x0001
/** GpsLocationExtended has valid altitude mean sea level. */
#define GPS_LOCATION_EXTENDED_HAS_ALTITUDE_MEAN_SEA_LEVEL 0x0002
/** GpsLocation has valid magnetic deviation. */
#define GPS_LOCATION_EXTENDED_HAS_MAG_DEV 0x0004
/** GpsLocation has valid mode indicator. */
#define GPS_LOCATION_EXTENDED_HAS_MODE_IND 0x0008

/** Represents gps location extended. */
typedef struct {
    /** set to sizeof(GpsLocationExtended) */
    size_t          size;
    /** Contains GpsLocationExtendedFlags bits. */
    uint16_t        flags;
    /** Contains the Altitude wrt mean sea level */
    float           altitudeMeanSeaLevel;
    /** Contains Position Dilusion of Precision. */
    float           pdop;
    /** Contains Horizontal Dilusion of Precision. */
    float           hdop;
    /** Contains Vertical Dilusion of Precision. */
    float           vdop;
    /** Contains Magnetic Deviation. */
    float           magneticDeviation;
} GpsLocationExtended;

typedef enum {
  LOC_ENG_IF_REQUEST_TYPE_SUPL = 0,
  LOC_ENG_IF_REQUEST_TYPE_WIFI,
  LOC_ENG_IF_REQUEST_TYPE_ANY
} loc_if_req_type_e_type;

typedef enum {
  LOC_ENG_IF_REQUEST_SENDER_ID_QUIPC = 0,
  LOC_ENG_IF_REQUEST_SENDER_ID_MSAPM,
  LOC_ENG_IF_REQUEST_SENDER_ID_MSAPU,
  LOC_ENG_IF_REQUEST_SENDER_ID_GPSONE_DAEMON,
  LOC_ENG_IF_REQUEST_SENDER_ID_MODEM,
  LOC_ENG_IF_REQUEST_SENDER_ID_UNKNOWN
} loc_if_req_sender_id_e_type;

struct loc_eng_msg {
    const void* owner;
    const int msgid;
    inline loc_eng_msg(void* instance, int id) :
        owner(instance), msgid(id)
    {
        LOC_LOGV("creating msg %s", loc_get_msg_name(msgid));
        LOC_LOGV("creating msg ox%x", msgid);
    }
    virtual ~loc_eng_msg()
    {
        LOC_LOGV("deleting msg %s", loc_get_msg_name(msgid));
        LOC_LOGV("deleting msg ox%x", msgid);
    }
};

struct loc_eng_msg_a_glonass_protocol : public loc_eng_msg {
    const unsigned long a_glonass_protocol;
    inline loc_eng_msg_a_glonass_protocol(void* instance, unsigned long protocol) :
        loc_eng_msg(instance, LOC_ENG_MSG_A_GLONASS_PROTOCOL),
        a_glonass_protocol(protocol)
        {
            LOC_LOGV("A-GLONASS protocol: 0x%lx", protocol);
        }
};

struct loc_eng_msg_suple_version : public loc_eng_msg {
    const int supl_version;
    inline loc_eng_msg_suple_version(void* instance, int version) :
        loc_eng_msg(instance, LOC_ENG_MSG_SUPL_VERSION),
        supl_version(version)
        {
            LOC_LOGV("SUPL Version: %d", version);
        }
};

struct loc_eng_msg_lpp_config : public loc_eng_msg {
    const int lpp_config;
    inline loc_eng_msg_lpp_config(void *instance, int profile) :
        loc_eng_msg(instance, LOC_ENG_MSG_LPP_CONFIG),
        lpp_config(profile)
        {
            LOC_LOGV("lpp profile: %d", profile);
        }
};

struct loc_eng_msg_ext_power_config : public loc_eng_msg {
    const int isBatteryCharging;
    inline loc_eng_msg_ext_power_config(void* instance, int isBattCharging) :
            loc_eng_msg(instance, LOC_ENG_MSG_EXT_POWER_CONFIG),
            isBatteryCharging(isBattCharging)
        {
            LOC_LOGV("isBatteryCharging: %d", isBatteryCharging);
        }
};

struct loc_eng_msg_sensor_control_config : public loc_eng_msg {
    const int sensorsDisabled;
    inline loc_eng_msg_sensor_control_config(void* instance, int disabled) :
            loc_eng_msg(instance, LOC_ENG_MSG_SET_SENSOR_CONTROL_CONFIG),
            sensorsDisabled(disabled)
        {
            LOC_LOGV("Sensors Disabled: %d", disabled);
        }
};

struct loc_eng_msg_sensor_properties : public loc_eng_msg {
    const bool gyroBiasVarianceRandomWalk_valid;
    const float gyroBiasVarianceRandomWalk;
    const bool accelRandomWalk_valid;
    const float accelRandomWalk;
    const bool angleRandomWalk_valid;
    const float angleRandomWalk;
    const bool rateRandomWalk_valid;
    const float rateRandomWalk;
    const bool velocityRandomWalk_valid;
    const float velocityRandomWalk;
    inline loc_eng_msg_sensor_properties(void* instance, bool gyroBiasRandomWalk_valid, float gyroBiasRandomWalk,
                                          bool accelRandomWalk_valid, float accelRandomWalk,
                                          bool angleRandomWalk_valid, float angleRandomWalk,
                                          bool rateRandomWalk_valid, float rateRandomWalk,
                                          bool velocityRandomWalk_valid, float velocityRandomWalk) :
            loc_eng_msg(instance, LOC_ENG_MSG_SET_SENSOR_PROPERTIES),
            gyroBiasVarianceRandomWalk_valid(gyroBiasRandomWalk_valid),
            gyroBiasVarianceRandomWalk(gyroBiasRandomWalk),
            accelRandomWalk_valid(accelRandomWalk_valid),
            accelRandomWalk(accelRandomWalk),
            angleRandomWalk_valid(angleRandomWalk_valid),
            angleRandomWalk(angleRandomWalk),
            rateRandomWalk_valid(rateRandomWalk_valid),
            rateRandomWalk(rateRandomWalk),
            velocityRandomWalk_valid(velocityRandomWalk_valid),
            velocityRandomWalk(velocityRandomWalk)
        {
            LOC_LOGV("Sensor properties validity, Gyro Random walk: %d Accel Random Walk: %d "
                     "Angle Random Walk: %d Rate Random Walk: %d "
                     "Velocity Random Walk: %d",
                     gyroBiasRandomWalk_valid,
                     accelRandomWalk_valid,
                     angleRandomWalk_valid,
                     rateRandomWalk_valid,
                     velocityRandomWalk_valid
                     );
            LOC_LOGV("Sensor properties, Gyro Random walk: %f Accel Random Walk: %f "
                     "Angle Random Walk: %f Rate Random Walk: %f "
                     "Velocity Random Walk: %f",
                     gyroBiasRandomWalk,
                     accelRandomWalk,
                     angleRandomWalk,
                     rateRandomWalk,
                     velocityRandomWalk
                     );
        }
};

struct loc_eng_msg_sensor_perf_control_config : public loc_eng_msg {
    const int controlMode;
    const int accelSamplesPerBatch;
    const int accelBatchesPerSec;
    const int gyroSamplesPerBatch;
    const int gyroBatchesPerSec;
    const int accelSamplesPerBatchHigh;
    const int accelBatchesPerSecHigh;
    const int gyroSamplesPerBatchHigh;
    const int gyroBatchesPerSecHigh;
    const int algorithmConfig;
    inline loc_eng_msg_sensor_perf_control_config(void* instance, int controlMode,
                                                  int accelSamplesPerBatch, int accelBatchesPerSec,
                                                  int gyroSamplesPerBatch, int gyroBatchesPerSec,
                                                  int accelSamplesPerBatchHigh, int accelBatchesPerSecHigh,
                                                  int gyroSamplesPerBatchHigh, int gyroBatchesPerSecHigh,
                                                  int algorithmConfig) :
            loc_eng_msg(instance, LOC_ENG_MSG_SET_SENSOR_PERF_CONTROL_CONFIG),
            controlMode(controlMode),
            accelSamplesPerBatch(accelSamplesPerBatch),
            accelBatchesPerSec(accelBatchesPerSec),
            gyroSamplesPerBatch(gyroSamplesPerBatch),
            gyroBatchesPerSec(gyroBatchesPerSec),
            accelSamplesPerBatchHigh(accelSamplesPerBatchHigh),
            accelBatchesPerSecHigh(accelBatchesPerSecHigh),
            gyroSamplesPerBatchHigh(gyroSamplesPerBatchHigh),
            gyroBatchesPerSecHigh(gyroBatchesPerSecHigh),
            algorithmConfig(algorithmConfig)
        {
            LOC_LOGV("Sensor Perf Control Config (performanceControlMode)(%u) "
                "accel(#smp,#batches) (%u,%u) gyro(#smp,#batches) (%u,%u), "
                "accel_high(#smp,#batches) (%u,%u) gyro_high(#smp,#batches) (%u,%u), "
                "algorithmConfig(%u)\n",
                controlMode,
                accelSamplesPerBatch,
                accelBatchesPerSec,
                gyroSamplesPerBatch,
                gyroBatchesPerSec,
                accelSamplesPerBatchHigh,
                accelBatchesPerSecHigh,
                gyroSamplesPerBatchHigh,
                gyroBatchesPerSecHigh,
                algorithmConfig
                );
        }
};


struct loc_eng_msg_position_mode : public loc_eng_msg {
    const LocPosMode pMode;
    inline loc_eng_msg_position_mode(void* instance,
                                     LocPosMode &mode) :
        loc_eng_msg(instance, LOC_ENG_MSG_SET_POSITION_MODE),
        pMode(mode)
    {
        pMode.logv();
    }
};

struct loc_eng_msg_set_time : public loc_eng_msg {
    const GpsUtcTime time;
    const int64_t timeReference;
    const int uncertainty;
    inline loc_eng_msg_set_time(void* instance,
                                GpsUtcTime t,
                                int64_t tf,
                                int unc) :
        loc_eng_msg(instance, LOC_ENG_MSG_SET_TIME),
        time(t), timeReference(tf), uncertainty(unc)
    {
        LOC_LOGV("time: %lld\n  timeReference: %lld\n  uncertainty: %d",
                 time, timeReference, uncertainty);
    }
};

struct loc_eng_msg_inject_location : public loc_eng_msg {
    const double latitude;
    const double longitude;
    const float accuracy;
    inline loc_eng_msg_inject_location(void* instance, double lat,
                                       double longi, float accur) :
        loc_eng_msg(instance, LOC_ENG_MSG_INJECT_LOCATION),
        latitude(lat), longitude(longi), accuracy(accur)
    {
        LOC_LOGV("latitude: %f\n  longitude: %f\n  accuracy: %f",
                 latitude, longitude, accuracy);
    }
};

struct loc_eng_msg_delete_aiding_data : public loc_eng_msg {
    const GpsAidingData type;
    inline loc_eng_msg_delete_aiding_data(void* instance, GpsAidingData data) :
        loc_eng_msg(instance, LOC_ENG_MSG_DELETE_AIDING_DATA), type(data)
    {
        LOC_LOGV("aiding data msak %d", type);
    }
};

struct loc_eng_msg_report_position : public loc_eng_msg {
    const GpsLocation location;
    const GpsLocationExtended locationExtended;
    const void* locationExt;
    const enum loc_sess_status status;
    const LocPosTechMask technology_mask;
    inline loc_eng_msg_report_position(void* instance, GpsLocation &loc, GpsLocationExtended &locExtended, void* locExt,
                                       enum loc_sess_status st) :
        loc_eng_msg(instance, LOC_ENG_MSG_REPORT_POSITION),
        location(loc), locationExtended(locExtended), locationExt(locExt), status(st), technology_mask(LOC_POS_TECH_MASK_DEFAULT)
    {
#ifdef FEATURE_ULP
        LOC_LOGV("flags: %d\n  source: %d\n  latitude: %f\n  longitude: %f\n  altitude: %f\n  speed: %f\n  bearing: %f\n  accuracy: %f\n  timestamp: %lld\n  rawDataSize: %d\n  rawData: %p\n  Session status: %d\n Technology mask: %u",
                 location.flags, location.position_source, location.latitude, location.longitude,
                 location.altitude, location.speed, location.bearing, location.accuracy,
                 location.timestamp, location.rawDataSize, location.rawData,status,technology_mask);
#else
        LOC_LOGV("flags: %d\n  latitude: %f\n  longitude: %f\n  altitude: %f\n  speed: %f\n  bearing: %f\n  accuracy: %f\n  timestamp: %lld\n  Session status: %d\n Technology mask: %u",
                 location.flags, location.latitude, location.longitude,
                 location.altitude, location.speed, location.bearing, location.accuracy,
                 location.timestamp, status,technology_mask);
#endif
    }
    inline loc_eng_msg_report_position(void* instance, GpsLocation &loc, GpsLocationExtended &locExtended, void* locExt,
                                       enum loc_sess_status st, LocPosTechMask technology) :
        loc_eng_msg(instance, LOC_ENG_MSG_REPORT_POSITION),
        location(loc), locationExtended(locExtended), locationExt(locExt), status(st), technology_mask(technology)
    {
#ifdef FEATURE_ULP
        LOC_LOGV("flags: %d\n  source: %d\n  latitude: %f\n  longitude: %f\n  altitude: %f\n  speed: %f\n  bearing: %f\n  accuracy: %f\n  timestamp: %lld\n  rawDataSize: %d\n  rawData: %p\n  Session status: %d\n Technology mask: %u",
                 location.flags, location.position_source, location.latitude, location.longitude,
                 location.altitude, location.speed, location.bearing, location.accuracy,
                 location.timestamp, location.rawDataSize, location.rawData,status,technology_mask);
#else
        LOC_LOGV("flags: %d\n  latitude: %f\n  longitude: %f\n  altitude: %f\n  speed: %f\n  bearing: %f\n  accuracy: %f\n  timestamp: %lld\n  Session status: %d\n Technology mask: %u",
                 location.flags, location.latitude, location.longitude,
                 location.altitude, location.speed, location.bearing, location.accuracy,
                 location.timestamp, status,technology_mask);
#endif
    }
};

struct loc_eng_msg_report_sv : public loc_eng_msg {
    const GpsSvStatus svStatus;
    const GpsLocationExtended locationExtended;
    const void* svExt;
    inline loc_eng_msg_report_sv(void* instance, GpsSvStatus &sv, GpsLocationExtended &locExtended, void* ext) :
        loc_eng_msg(instance, LOC_ENG_MSG_REPORT_SV), svStatus(sv), locationExtended(locExtended), svExt(ext)
    {
        LOC_LOGV("num sv: %d\n  ephemeris mask: %dxn  almanac mask: %x\n  used in fix mask: %x\n      sv: prn         snr       elevation      azimuth",
                 svStatus.num_svs, svStatus.ephemeris_mask, svStatus.almanac_mask, svStatus.used_in_fix_mask);
        for (int i = 0; i < svStatus.num_svs && i < GPS_MAX_SVS; i++) {
            LOC_LOGV("   %d:   %d    %f    %f    %f\n  ",
                     i,
                     svStatus.sv_list[i].prn,
                     svStatus.sv_list[i].snr,
                     svStatus.sv_list[i].elevation,
                     svStatus.sv_list[i].azimuth);
        }
    }
};

struct loc_eng_msg_report_status : public loc_eng_msg {
    const GpsStatusValue status;
    inline loc_eng_msg_report_status(void* instance, GpsStatusValue engineStatus) :
        loc_eng_msg(instance, LOC_ENG_MSG_REPORT_STATUS), status(engineStatus)
    {
        LOC_LOGV("status: %s", loc_get_gps_status_name(status));
    }
};

struct loc_eng_msg_report_nmea : public loc_eng_msg {
    char* const nmea;
    const int length;
    inline loc_eng_msg_report_nmea(void* instance,
                                   const char* data,
                                   int len) :
        loc_eng_msg(instance, LOC_ENG_MSG_REPORT_NMEA),
        nmea(new char[len]), length(len)
    {
        memcpy((void*)nmea, (void*)data, len);
        LOC_LOGV("length: %d\n  nmea: %p - %c%c%c",
                 length, nmea, nmea[3], nmea[4], nmea[5]);
    }
    inline ~loc_eng_msg_report_nmea()
    {
        delete[] nmea;
    }
};

struct loc_eng_msg_request_bit : public loc_eng_msg {
    const loc_if_req_type_e_type ifType;
    const int ipv4Addr;
    char* const ipv6Addr;
    inline loc_eng_msg_request_bit(void* instance,
                                   loc_if_req_type_e_type type,
                                   int ipv4,
                                   char* ipv6) :
        loc_eng_msg(instance, LOC_ENG_MSG_REQUEST_BIT),
        ifType(type), ipv4Addr(ipv4),
        ipv6Addr(NULL == ipv6 ? NULL : new char[16])
    {
        if (NULL != ipv6Addr)
            memcpy(ipv6Addr, ipv6, 16);
        LOC_LOGV("ifType: %d, ipv4: %d.%d.%d.%d, ipv6: %s", ifType,
                 (unsigned char)(ipv4>>24),
                 (unsigned char)(ipv4>>16),
                 (unsigned char)(ipv4>>8),
                 (unsigned char)ipv4,
                 NULL != ipv6Addr ? ipv6Addr : "");
    }

    inline ~loc_eng_msg_request_bit()
    {
        if (NULL != ipv6Addr) {
            delete[] ipv6Addr;
        }
    }
};

struct loc_eng_msg_request_wifi : public loc_eng_msg {
    const loc_if_req_type_e_type ifType;
    const loc_if_req_sender_id_e_type senderId;
    char* const ssid;
    char* const password;
    inline loc_eng_msg_request_wifi(void* instance,
                                   loc_if_req_type_e_type type,
                                   loc_if_req_sender_id_e_type sender_id,
                                   char* s,
                                   char* p) :
        loc_eng_msg(instance, LOC_ENG_MSG_REQUEST_WIFI),
        ifType(type), senderId(sender_id),
        ssid(NULL == s ? NULL : new char[SSID_BUF_SIZE]),
        password(NULL == p ? NULL : new char[SSID_BUF_SIZE])
    {
        if (NULL != ssid)
            strlcpy(ssid, s, SSID_BUF_SIZE);
        if (NULL != password)
            strlcpy(password, p, SSID_BUF_SIZE);
        LOC_LOGV("ifType: %d, senderId: %d, ssid: %s, password: %s",
                 ifType,
                 senderId,
                 NULL != ssid ? ssid : "",
                 NULL != password ? password : "");
    }

    inline ~loc_eng_msg_request_wifi()
    {
        if (NULL != ssid) {
            delete[] ssid;
        }
        if (NULL != password) {
            delete[] password;
        }
    }
};

struct loc_eng_msg_release_bit : public loc_eng_msg {
    const loc_if_req_type_e_type ifType;
    const int ipv4Addr;
    char* const ipv6Addr;
    inline loc_eng_msg_release_bit(void* instance,
                                   loc_if_req_type_e_type type,
                                   int ipv4,
                                   char* ipv6) :
        loc_eng_msg(instance, LOC_ENG_MSG_RELEASE_BIT),
        ifType(type), ipv4Addr(ipv4),
        ipv6Addr(NULL == ipv6 ? NULL : new char[16])
    {
        if (NULL != ipv6Addr)
            memcpy(ipv6Addr, ipv6, 16);
        LOC_LOGV("ifType: %d, ipv4: %d.%d.%d.%d, ipv6: %s", ifType,
                 (unsigned char)(ipv4>>24),
                 (unsigned char)(ipv4>>16),
                 (unsigned char)(ipv4>>8),
                 (unsigned char)ipv4,
                 NULL != ipv6Addr ? ipv6Addr : "");
    }

    inline ~loc_eng_msg_release_bit()
    {
        if (NULL != ipv6Addr) {
            delete[] ipv6Addr;
        }
    }
};

struct loc_eng_msg_release_wifi : public loc_eng_msg {
    const loc_if_req_type_e_type ifType;
    const loc_if_req_sender_id_e_type senderId;
    char* const ssid;
    char* const password;
    inline loc_eng_msg_release_wifi(void* instance,
                                   loc_if_req_type_e_type type,
                                   loc_if_req_sender_id_e_type sender_id,
                                   char* s,
                                   char* p) :
        loc_eng_msg(instance, LOC_ENG_MSG_RELEASE_WIFI),
        ifType(type), senderId(sender_id),
        ssid(NULL == s ? NULL : new char[SSID_BUF_SIZE]),
        password(NULL == p ? NULL : new char[SSID_BUF_SIZE])
    {
        if (NULL != s)
            strlcpy(ssid, s, SSID_BUF_SIZE);
        if (NULL != p)
            strlcpy(password, p, SSID_BUF_SIZE);
        LOC_LOGV("ifType: %d, senderId: %d, ssid: %s, password: %s",
                 ifType,
                 senderId,
                 NULL != ssid ? ssid : "",
                 NULL != password ? password : "");
    }

    inline ~loc_eng_msg_release_wifi()
    {
        if (NULL != ssid) {
            delete[] ssid;
        }
        if (NULL != password) {
            delete[] password;
        }
    }
};

struct loc_eng_msg_request_atl : public loc_eng_msg {
    const int handle;
    const AGpsType type;
    inline loc_eng_msg_request_atl(void* instance, int hndl,
                                   AGpsType agps_type) :
        loc_eng_msg(instance, LOC_ENG_MSG_REQUEST_ATL),
        handle(hndl), type(agps_type)
    {
        LOC_LOGV("handle: %d\n  agps type: %s",
                 handle,
                 loc_get_agps_type_name(type));
    }
};

struct loc_eng_msg_release_atl : public loc_eng_msg {
    const int handle;
    inline loc_eng_msg_release_atl(void* instance, int hndl) :
        loc_eng_msg(instance, LOC_ENG_MSG_RELEASE_ATL), handle(hndl)
    {
        LOC_LOGV("handle: %d", handle);
    }
};

struct loc_eng_msg_request_ni : public loc_eng_msg {
    const GpsNiNotification notify;
    const void *passThroughData;
    inline loc_eng_msg_request_ni(void* instance,
                                  GpsNiNotification &notif, const void* data) :
        loc_eng_msg(instance, LOC_ENG_MSG_REQUEST_NI),
        notify(notif), passThroughData(data)
    {
        LOC_LOGV("id: %d\n  type: %s\n  flags: %d\n  time out: %d\n  default response: %s\n  requestor id encoding: %s\n  text encoding: %s\n  passThroughData: %p",
                 notify.notification_id,
                 loc_get_ni_type_name(notify.ni_type),
                 notify.notify_flags,
                 notify.timeout,
                 loc_get_ni_response_name(notify.default_response),
                 loc_get_ni_encoding_name(notify.requestor_id_encoding),
                 loc_get_ni_encoding_name(notify.text_encoding),
                 passThroughData);
    }
};

struct loc_eng_msg_inform_ni_response : public loc_eng_msg {
    const GpsUserResponseType response;
    const void *passThroughData;
    inline loc_eng_msg_inform_ni_response(void* instance,
                                          GpsUserResponseType resp,
                                          const void* data) :
        loc_eng_msg(instance, LOC_ENG_MSG_INFORM_NI_RESPONSE),
        response(resp), passThroughData(data)
    {
        LOC_LOGV("response: %s\n  passThroughData: %p",
                 loc_get_ni_response_name(response),
                 passThroughData);
    }
    inline ~loc_eng_msg_inform_ni_response()
    {
        // this is a bit weird since passThroughData is not
        // allocated by this class.  But there is no better way.
        // passThroughData actually won't be NULL here.
        // But better safer than sorry.
        if (NULL != passThroughData) {
            free((void*)passThroughData);
        }
    }
};

struct loc_eng_msg_set_apn : public loc_eng_msg {
    char* const apn;
    inline loc_eng_msg_set_apn(void* instance, const char* name, int len) :
        loc_eng_msg(instance, LOC_ENG_MSG_SET_APN),
        apn(new char[len+1])
    {
        memcpy((void*)apn, (void*)name, len);
        apn[len] = 0;
        LOC_LOGV("apn: %s", apn);
    }
    inline ~loc_eng_msg_set_apn()
    {
        delete[] apn;
    }
};



struct loc_eng_msg_set_server_ipv4 : public loc_eng_msg {
    const unsigned int nl_addr;
    const int port;
    const LocServerType serverType;
    inline loc_eng_msg_set_server_ipv4(void* instance,
                                       unsigned int ip,
                                       int p,
                                       LocServerType type) :
        loc_eng_msg(instance, LOC_ENG_MSG_SET_SERVER_IPV4),
        nl_addr(ip), port(p), serverType(type)
    {
        LOC_LOGV("addr: %x\n  , port: %d\n type: %s", nl_addr, port, loc_get_server_type_name(serverType));
    }
};


struct loc_eng_msg_set_server_url : public loc_eng_msg {
    const int len;
    char* const url;
    inline loc_eng_msg_set_server_url(void* instance,
                                      const char* urlString,
                                      int url_len) :
        loc_eng_msg(instance, LOC_ENG_MSG_SET_SERVER_URL),
        len(url_len), url(new char[len+1])
    {
        memcpy((void*)url, (void*)urlString, url_len);
        url[len] = 0;
        LOC_LOGV("url: %s", url);
    }
    inline ~loc_eng_msg_set_server_url()
    {
        delete[] url;
    }
};

struct loc_eng_msg_inject_xtra_data : public loc_eng_msg {
    char* const data;
    const int length;
    inline loc_eng_msg_inject_xtra_data(void* instance, char* d, int l) :
        loc_eng_msg(instance, LOC_ENG_MSG_INJECT_XTRA_DATA),
        data(new char[l]), length(l)
    {
        memcpy((void*)data, (void*)d, l);
        LOC_LOGV("length: %d\n  data: %p", length, data);
    }
    inline ~loc_eng_msg_inject_xtra_data()
    {
        delete[] data;
    }
};

#ifdef FEATURE_IPV6
struct loc_eng_msg_atl_open_success : public loc_eng_msg {
    const AGpsStatusValue agpsType;
    const int length;
    char* const apn;
    const AGpsBearerType bearerType;
    inline loc_eng_msg_atl_open_success(void* instance,
                                        AGpsStatusValue atype,
                                        const char* name,
                                        int len,
                                        AGpsBearerType btype) :
        loc_eng_msg(instance, LOC_ENG_MSG_ATL_OPEN_SUCCESS),
        agpsType(atype), length(len),
        apn(new char[len+1]), bearerType(btype)
    {
        memcpy((void*)apn, (void*)name, len);
        apn[len] = 0;
        LOC_LOGV("agps type: %s\n  apn: %s\n  bearer type: %s",
                 loc_get_agps_type_name(agpsType),
                 apn,
                 loc_get_agps_bear_name(bearerType));
    }
    inline ~loc_eng_msg_atl_open_success()
    {
        delete[] apn;
    }
};
#else
struct loc_eng_msg_atl_open_success : public loc_eng_msg {
    const int length;
    char* const apn;
    inline loc_eng_msg_atl_open_success(void* instance,
                                        const char* name,
                                        int len) :
        loc_eng_msg(instance, LOC_ENG_MSG_ATL_OPEN_SUCCESS),
        length(len),
        apn(new char[len+1])
    {
        memcpy((void*)apn, (void*)name, len);
        apn[len] = 0;
        LOC_LOGV("apn: %s\n",
                 apn);
    }
    inline ~loc_eng_msg_atl_open_success()
    {
        delete[] apn;
    }
};
#endif

#ifdef FEATURE_IPV6
struct loc_eng_msg_atl_open_failed : public loc_eng_msg {
    const AGpsStatusValue agpsType;
    inline loc_eng_msg_atl_open_failed(void* instance,
                                       AGpsStatusValue atype) :
        loc_eng_msg(instance, LOC_ENG_MSG_ATL_OPEN_FAILED),
        agpsType(atype)
    {
        LOC_LOGV("agps type %s",
                 loc_get_agps_type_name(agpsType));
    }
};
#else
struct loc_eng_msg_atl_open_failed : public loc_eng_msg {
    inline loc_eng_msg_atl_open_failed(void* instance) :
        loc_eng_msg(instance, LOC_ENG_MSG_ATL_OPEN_FAILED)
    {
        LOC_LOGV("");
    }
};
#endif

#ifdef FEATURE_IPV6
struct loc_eng_msg_atl_closed : public loc_eng_msg {
    const AGpsStatusValue agpsType;
    inline loc_eng_msg_atl_closed(void* instance,
                                  AGpsStatusValue atype) :
        loc_eng_msg(instance, LOC_ENG_MSG_ATL_CLOSED),
        agpsType(atype)
    {
        LOC_LOGV("agps type %s",
                 loc_get_agps_type_name(agpsType));
    }
};
#else
struct loc_eng_msg_atl_closed : public loc_eng_msg {
    inline loc_eng_msg_atl_closed(void* instance) :
        loc_eng_msg(instance, LOC_ENG_MSG_ATL_CLOSED)
    {
        LOC_LOGV("");
    }
};
#endif

struct loc_eng_msg_set_data_enable : public loc_eng_msg {
    const int enable;
    char* const apn;
    const int length;
    inline loc_eng_msg_set_data_enable(void* instance,
                                       const char* name,
                                       int len,
                                       int yes) :
        loc_eng_msg(instance, LOC_ENG_MSG_ENABLE_DATA),
        enable(yes), apn(new char[len+1]), length(len)
    {
        memcpy((void*)apn, (void*)name, len);
        apn[len] = 0;
        LOC_LOGV("apn: %s\n  enable: %d", apn, enable);
    }
    inline ~loc_eng_msg_set_data_enable()
    {
        delete[] apn;
    }
};

#ifdef FEATURE_ULP
struct loc_eng_msg_request_network_position : public loc_eng_msg {
    const UlpNetworkRequestPos networkPosRequest;
    inline loc_eng_msg_request_network_position (void* instance, UlpNetworkRequestPos networkPosReq) :
        loc_eng_msg(instance, LOC_ENG_MSG_REQUEST_NETWORK_POSIITON),
        networkPosRequest(networkPosReq)
    {
        LOC_LOGV("network position request: desired pos source %d\n  request type: %d\n interval ms: %d ",
             networkPosReq.desired_position_source,
             networkPosReq.request_type,
             networkPosReq.interval_ms);
    }
};

struct loc_eng_msg_request_phone_context : public loc_eng_msg {
    const UlpPhoneContextRequest contextRequest;
    inline loc_eng_msg_request_phone_context (void* instance, UlpPhoneContextRequest contextReq) :
        loc_eng_msg(instance, LOC_ENG_MSG_REQUEST_PHONE_CONTEXT),
        contextRequest(contextReq)
    {
        LOC_LOGV("phone context request: request type 0x%x context type: 0x%x ",
             contextRequest.request_type,
             contextRequest.context_type);
    }
};

struct ulp_msg_update_criteria : public loc_eng_msg {
    const UlpLocationCriteria locationCriteria;
    inline ulp_msg_update_criteria (void* instance, UlpLocationCriteria criteria) :
        loc_eng_msg(instance, ULP_MSG_UPDATE_CRITERIA),
        locationCriteria(criteria)
    {
        LOC_LOGV("location criteria: aciton %d\n  valid mask: %d\n provider source: %d\n accuracy %d\n recurrence type %d\n min interval %d\n power consumption %d\n intermediate pos %d ",
             locationCriteria.action,
             locationCriteria.valid_mask,
             locationCriteria.provider_source,
             locationCriteria.preferred_horizontal_accuracy,
             locationCriteria.recurrence_type,
             locationCriteria.min_interval,
             locationCriteria.preferred_power_consumption,
             locationCriteria.intermediate_pos_report_enabled);
    }
};

struct ulp_msg_inject_raw_command : public loc_eng_msg {
    const char* rawCommand;
    const int rawCommandLength;
    inline ulp_msg_inject_raw_command (void* instance, char* command, int length) :
        loc_eng_msg(instance, ULP_MSG_INJECT_RAW_COMMAND),
        rawCommand(new char[length]),
        rawCommandLength(length)
    {
      memcpy((void*)rawCommand, (void*)command, length);
      LOC_LOGV("inject raw command: command %s\n  command length: %d\n ",
             rawCommand,
             rawCommandLength);
    }

    inline ~ulp_msg_inject_raw_command()
    {
        delete[] rawCommand;
    }
};

struct ulp_msg_inject_phone_context_settings : public loc_eng_msg {
    const UlpPhoneContextSettings phoneSetting;
    inline ulp_msg_inject_phone_context_settings(void* instance, UlpPhoneContextSettings setting) :
        loc_eng_msg(instance, ULP_MSG_INJECT_PHONE_CONTEXT_SETTINGS),
        phoneSetting(setting)
    {
        LOC_LOGV("context type: %d\n  gps enabled: %d\n network position available %d\n wifi setting enabled %d\n battery charging %d"
                 "is_agps_setting_enabled %d, is_enh_location_services_enabled %d\n",
             phoneSetting.context_type,
             phoneSetting.is_gps_enabled,
             phoneSetting.is_network_position_available,
             phoneSetting.is_wifi_setting_enabled,
             phoneSetting.is_battery_charging,
             phoneSetting.is_agps_enabled,
             phoneSetting.is_enh_location_services_enabled);
    }
};

struct ulp_msg_inject_network_position : public loc_eng_msg {
    const UlpNetworkPositionReport networkPosition;
    inline ulp_msg_inject_network_position(void* instance, UlpNetworkPositionReport networkPos) :
        loc_eng_msg(instance, ULP_MSG_INJECT_NETWORK_POSITION),
        networkPosition(networkPos)
    {
        LOC_LOGV("flags: %d\n  source: %d\n  latitude: %f\n  longitude: %f\n  accuracy %d",
             networkPosition.valid_flag,
             networkPosition.position.pos_source,
             networkPosition.position.latitude,
             networkPosition.position.longitude,
             networkPosition.position.HEPE);
    }
};

struct ulp_msg_report_quipc_position : public loc_eng_msg {
    const GpsLocation location;
    const int  quipc_error_code;
    inline ulp_msg_report_quipc_position(void* instance, GpsLocation &loc,
                                         int  quipc_err) :
        loc_eng_msg(instance, ULP_MSG_REPORT_QUIPC_POSITION),
        location(loc), quipc_error_code(quipc_err)
    {
        LOC_LOGV("flags: %d\n  source: %d\n  latitude: %f\n  longitude: %f\n  altitude: %f\n  speed: %f\n  bearing: %f\n  accuracy: %f\n  timestamp: %lld\n  rawDataSize: %d\n  rawData: %p\n  Quipc error: %d",
                 location.flags, location.position_source, location.latitude, location.longitude,
                 location.altitude, location.speed, location.bearing, location.accuracy,
                 location.timestamp, location.rawDataSize, location.rawData,
                 quipc_error_code);
    }
};
#endif

void loc_eng_msg_sender(void* loc_eng_data_p, void* msg);
int loc_eng_msgget(int * p_req_msgq);
int loc_eng_msgremove(int req_msgq);
int loc_eng_msgsnd(int msgqid, void * msgp);
int loc_eng_msgrcv(int msgqid, void ** msgp);
int loc_eng_msgsnd_raw(int msgqid, void * msgp, unsigned int msgsz);
int loc_eng_msgrcv_raw(int msgqid, void *msgp, unsigned int msgsz);
int loc_eng_msgflush(int msgqid);
int loc_eng_msgunblock(int msgqid);

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* LOC_ENG_MSG_H */
