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
#ifndef LOC_API_ADAPTER_H
#define LOC_API_ADAPTER_H

#include <ctype.h>
#include <hardware/gps.h>
#include <loc.h>
#include <loc_eng_log.h>
#include <log_util.h>
#include <loc_eng_msg.h>

#define MAX_APN_LEN 100
#define MAX_URL_LEN 256
#define smaller_of(a, b) (((a) > (b)) ? (b) : (a))

enum loc_api_adapter_err {
    LOC_API_ADAPTER_ERR_SUCCESS             = 0,
    LOC_API_ADAPTER_ERR_GENERAL_FAILURE     = 1,
    LOC_API_ADAPTER_ERR_UNSUPPORTED         = 2,
    LOC_API_ADAPTER_ERR_INVALID_HANDLE      = 4,
    LOC_API_ADAPTER_ERR_INVALID_PARAMETER   = 5,
    LOC_API_ADAPTER_ERR_ENGINE_BUSY         = 6,
    LOC_API_ADAPTER_ERR_PHONE_OFFLINE       = 7,
    LOC_API_ADAPTER_ERR_TIMEOUT             = 8,
    LOC_API_ADAPTER_ERR_SERVICE_NOT_PRESENT = 9,

    LOC_API_ADAPTER_ERR_ENGINE_DOWN         = 100,
    LOC_API_ADAPTER_ERR_FAILURE,
    LOC_API_ADAPTER_ERR_UNKNOWN
};

enum loc_api_adapter_event_index {
    LOC_API_ADAPTER_REPORT_POSITION = 0,       // Position report comes in loc_parsed_position_s_type
    LOC_API_ADAPTER_REPORT_SATELLITE,          // Satellite in view report
    LOC_API_ADAPTER_REPORT_NMEA_1HZ,           // NMEA report at 1HZ rate
    LOC_API_ADAPTER_REPORT_NMEA_POSITION,      // NMEA report at position report rate
    LOC_API_ADAPTER_REQUEST_NI_NOTIFY_VERIFY,  // NI notification/verification request
    LOC_API_ADAPTER_REQUEST_ASSISTANCE_DATA,   // Assistance data, eg: time, predicted orbits request
    LOC_API_ADAPTER_REQUEST_LOCATION_SERVER,   // Request for location server
    LOC_API_ADAPTER_REPORT_IOCTL,              // Callback report for loc_ioctl
    LOC_API_ADAPTER_REPORT_STATUS,             // Misc status report: eg, engine state

    LOC_API_ADAPTER_EVENT_MAX
};

#define LOC_API_ADAPTER_BIT_PARSED_POSITION_REPORT   (1<<LOC_API_ADAPTER_REPORT_POSITION)
#define LOC_API_ADAPTER_BIT_SATELLITE_REPORT         (1<<LOC_API_ADAPTER_REPORT_SATELLITE)
#define LOC_API_ADAPTER_BIT_NMEA_1HZ_REPORT          (1<<LOC_API_ADAPTER_REPORT_NMEA_1HZ)
#define LOC_API_ADAPTER_BIT_NMEA_POSITION_REPORT     (1<<LOC_API_ADAPTER_REPORT_NMEA_POSITION)
#define LOC_API_ADAPTER_BIT_NI_NOTIFY_VERIFY_REQUEST (1<<LOC_API_ADAPTER_REQUEST_NI_NOTIFY_VERIFY)
#define LOC_API_ADAPTER_BIT_ASSISTANCE_DATA_REQUEST  (1<<LOC_API_ADAPTER_REQUEST_ASSISTANCE_DATA)
#define LOC_API_ADAPTER_BIT_LOCATION_SERVER_REQUEST  (1<<LOC_API_ADAPTER_REQUEST_LOCATION_SERVER)
#define LOC_API_ADAPTER_BIT_IOCTL_REPORT             (1<<LOC_API_ADAPTER_REPORT_IOCTL)
#define LOC_API_ADAPTER_BIT_STATUS_REPORT            (1<<LOC_API_ADAPTER_REPORT_STATUS)

typedef unsigned int LOC_API_ADAPTER_EVENT_MASK_T;
typedef void (*loc_msg_sender)(void* loc_eng_data_p, void* msgp);

struct LocEng {
    void* owner;
    LOC_API_ADAPTER_EVENT_MASK_T eventMask;
    const gps_acquire_wakelock acquireWakelock;
    const gps_release_wakelock releaseWakeLock;
    const loc_msg_sender       sendMsge;
#ifdef FEATURE_ULP
    const loc_msg_sender       sendUlpMsg;
#endif
    const loc_ext_parser       extPosInfo;
    const loc_ext_parser       extSvInfo;

    LocEng(void* caller,
           LOC_API_ADAPTER_EVENT_MASK_T emask,
           gps_acquire_wakelock acqwl,
           gps_release_wakelock relwl,
           loc_msg_sender msgSender,
#ifdef FEATURE_ULP
           loc_msg_sender msgUlpSender,
#endif
           loc_ext_parser posParser,
           loc_ext_parser svParser);
};

class LocApiAdapter {
protected:
    const LocEng locEngHandle;
    LocPosMode fixCriteria;
    bool navigating;

    LocApiAdapter(LocEng &locEng);

public:
    //LocApiAdapter(int q, reportCb_t[LOC_API_ADAPTER_EVENT_MAX] callbackTable);
    virtual ~LocApiAdapter();

    static LocApiAdapter* getLocApiAdapter(LocEng &locEng);

    static int hexcode(char *hexstring, int string_size,
                       const char *data, int data_size);
    static int decodeAddress(char *addr_string, int string_size,
                             const char *data, int data_size);

    void reportPosition(GpsLocation &location,
                        GpsLocationExtended &locationExtended,
                        void* locationExt,
                        enum loc_sess_status status,
                        LocPosTechMask loc_technology_mask = LOC_POS_TECH_MASK_DEFAULT);
    void reportSv(GpsSvStatus &svStatus,
                  GpsLocationExtended &locationExtended,
                  void* svExt);
    void reportStatus(GpsStatusValue status);
    void reportNmea(const char* nmea, int length);
    void reportAgpsStatus(AGpsStatus &agpsStatus);
    void requestXtraData();
    void requestTime();
    void requestLocation();
    void requestATL(int connHandle, AGpsType agps_type);
    void releaseATL(int connHandle);
    void requestNiNotify(GpsNiNotification &notify, const void* data);
    void handleEngineDownEvent();
    void handleEngineUpEvent();

    // All below functions are to be defined by adapter specific modules:
    // RPC, QMI, etc.  The default implementation is empty.
    inline virtual enum loc_api_adapter_err
        reinit()
    {LOC_LOGW("%s: default implementation invoked", __func__); return LOC_API_ADAPTER_ERR_SUCCESS;}
    inline virtual enum loc_api_adapter_err
        startFix()
    {LOC_LOGW("%s: default implementation invoked", __func__); return LOC_API_ADAPTER_ERR_SUCCESS;}
    inline virtual enum loc_api_adapter_err
        stopFix()
    {LOC_LOGW("%s: default implementation invoked", __func__); return LOC_API_ADAPTER_ERR_SUCCESS;}
    inline virtual enum loc_api_adapter_err
        deleteAidingData(GpsAidingData f)
    {LOC_LOGW("%s: default implementation invoked", __func__); return LOC_API_ADAPTER_ERR_SUCCESS;}
    inline virtual enum loc_api_adapter_err
        enableData(int enable)
    {LOC_LOGW("%s: default implementation invoked", __func__); return LOC_API_ADAPTER_ERR_SUCCESS;}
    inline virtual enum loc_api_adapter_err
        setAPN(char* apn, int len)
    {LOC_LOGW("%s: default implementation invoked", __func__); return LOC_API_ADAPTER_ERR_SUCCESS;}
    inline virtual enum loc_api_adapter_err
        injectPosition(double latitude, double longitude, float accuracy)
    {LOC_LOGW("%s: default implementation invoked", __func__); return LOC_API_ADAPTER_ERR_SUCCESS;}
    inline virtual enum loc_api_adapter_err
        setTime(GpsUtcTime time, int64_t timeReference, int uncertainty)
    {LOC_LOGW("%s: default implementation invoked", __func__); return LOC_API_ADAPTER_ERR_SUCCESS;}
    inline virtual enum loc_api_adapter_err
        setXtraData(char* data, int length)
    {LOC_LOGW("%s: default implementation invoked", __func__); return LOC_API_ADAPTER_ERR_SUCCESS;}
#ifdef FEATURE_IPV6
    inline virtual enum loc_api_adapter_err
        atlOpenStatus(int handle, int is_succ, char* apn, AGpsBearerType bear, AGpsType agpsType)
    {LOC_LOGW("%s: default implementation invoked", __func__); return LOC_API_ADAPTER_ERR_SUCCESS;}
#else
    inline virtual enum loc_api_adapter_err
        atlOpenStatus(int handle, int is_succ, char* apn, AGpsType agpsType)
    {LOC_LOGW("%s: default implementation invoked", __func__); return LOC_API_ADAPTER_ERR_SUCCESS;}
#endif
    inline virtual enum loc_api_adapter_err
        atlCloseStatus(int handle, int is_succ)
    {LOC_LOGW("%s: default implementation invoked", __func__); return LOC_API_ADAPTER_ERR_SUCCESS;}
    inline virtual enum loc_api_adapter_err
        setPositionMode(const LocPosMode *posMode)
    {LOC_LOGW("%s: default implementation invoked", __func__); return LOC_API_ADAPTER_ERR_SUCCESS;}
    inline virtual enum loc_api_adapter_err
        setServer(const char* url, int len)
    {LOC_LOGW("%s: default implementation invoked", __func__); return LOC_API_ADAPTER_ERR_SUCCESS;}
    inline virtual enum loc_api_adapter_err
        setServer(unsigned int ip, int port,
                  LocServerType type)
    {LOC_LOGW("%s: default implementation invoked", __func__); return LOC_API_ADAPTER_ERR_SUCCESS;}
    inline virtual enum loc_api_adapter_err
        informNiResponse(GpsUserResponseType userResponse, const void* passThroughData)
    {LOC_LOGW("%s: default implementation invoked", __func__); return LOC_API_ADAPTER_ERR_SUCCESS;}
    inline virtual enum loc_api_adapter_err
        setSUPLVersion(uint32_t version)
    {LOC_LOGW("%s: default implementation invoked", __func__); return LOC_API_ADAPTER_ERR_SUCCESS;}
    inline virtual enum loc_api_adapter_err
        setLPPConfig(uint32_t profile)
    {LOC_LOGW("%s: default implementation invoked", __func__);
     return LOC_API_ADAPTER_ERR_SUCCESS; }
    inline virtual enum loc_api_adapter_err
        setSensorControlConfig(int sensorUsage)
    {LOC_LOGW("%s: default implementation invoked", __func__); return LOC_API_ADAPTER_ERR_SUCCESS;}
    inline virtual enum loc_api_adapter_err
        setSensorProperties(bool gyroBiasVarianceRandomWalk_valid, float gyroBiasVarianceRandomWalk,
                            bool accelBiasVarianceRandomWalk_valid, float accelBiasVarianceRandomWalk,
                            bool angleBiasVarianceRandomWalk_valid, float angleBiasVarianceRandomWalk,
                            bool rateBiasVarianceRandomWalk_valid, float rateBiasVarianceRandomWalk,
                            bool velocityBiasVarianceRandomWalk_valid, float velocityBiasVarianceRandomWalk)
    {LOC_LOGW("%s: default implementation invoked", __func__); return LOC_API_ADAPTER_ERR_SUCCESS;}
    inline virtual enum loc_api_adapter_err
        setSensorPerfControlConfig(int controlMode, int accelSamplesPerBatch, int accelBatchesPerSec,
                            int gyroSamplesPerBatch, int gyroBatchesPerSec,
                            int accelSamplesPerBatchHigh, int accelBatchesPerSecHigh,
                            int gyroSamplesPerBatchHigh, int gyroBatchesPerSecHigh, int algorithmConfig)
    {LOC_LOGW("%s: default implementation invoked", __func__); return LOC_API_ADAPTER_ERR_SUCCESS;}
    inline virtual enum loc_api_adapter_err
        setExtPowerConfig(int isBatteryCharging)
    {LOC_LOGW("%s: default implementation invoked", __func__); return LOC_API_ADAPTER_ERR_SUCCESS;}
    inline virtual enum loc_api_adapter_err
        setAGLONASSProtocol(unsigned long aGlonassProtocol)
    {LOC_LOGW("%s: default implementation invoked", __func__); return LOC_API_ADAPTER_ERR_SUCCESS;}

    inline const LocPosMode& getPositionMode() const {return fixCriteria;}

    inline bool isInSession() { return navigating; }
    inline virtual void setInSession(bool inSession) { navigating = inSession; }
};

extern "C" LocApiAdapter* getLocApiAdapter(LocEng &locEng);

typedef LocApiAdapter* (getLocApiAdapter_t)(LocEng&);

#endif //LOC_API_RPC_ADAPTER_H
