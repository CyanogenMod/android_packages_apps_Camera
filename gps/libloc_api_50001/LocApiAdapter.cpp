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
#define LOG_TAG "LocSvc_adapter"

#include <dlfcn.h>
#include <LocApiAdapter.h>
#include "loc_eng_msg.h"
#include "loc_log.h"
#include "loc_eng_ni.h"

static void* noProc(void* data)
{
    return NULL;
}

LocEng::LocEng(void* caller,
               LOC_API_ADAPTER_EVENT_MASK_T emask,
               gps_acquire_wakelock acqwl,
               gps_release_wakelock relwl,
               loc_msg_sender msgSender,
#ifdef FEATURE_ULP
               loc_msg_sender msgUlpSender,
#endif
               loc_ext_parser posParser,
               loc_ext_parser svParser) :
        owner(caller),
        eventMask(emask), acquireWakelock(acqwl),
        releaseWakeLock(relwl), sendMsge(msgSender),
#ifdef FEATURE_ULP
        sendUlpMsg(msgUlpSender),
#endif
        extPosInfo(NULL == posParser ? noProc : posParser),
        extSvInfo(NULL == svParser ? noProc : svParser)
{
    LOC_LOGV("LocEng constructor %p, %p", posParser, svParser);
}

LocApiAdapter::LocApiAdapter(LocEng &locEng) :
    locEngHandle(locEng), fixCriteria(), navigating(false)
{
    LOC_LOGD("LocApiAdapter created");
}

LocApiAdapter::~LocApiAdapter()
{
    LOC_LOGV("LocApiAdapter deleted");
}

LocApiAdapter* LocApiAdapter::getLocApiAdapter(LocEng &locEng)
{
    void* handle;
    LocApiAdapter* adapter = NULL;

    handle = dlopen ("libloc_api_v02.so", RTLD_NOW);

    if (!handle) {
        handle = dlopen ("libloc_api-rpc-qc.so", RTLD_NOW);
    }

    if (!handle) {
        adapter = new LocApiAdapter(locEng);
    } else {
        getLocApiAdapter_t* getHandle = (getLocApiAdapter_t*)dlsym(handle, "getLocApiAdapter");

        adapter = (*getHandle)(locEng);
    }

    return adapter;
}

int LocApiAdapter::hexcode(char *hexstring, int string_size,
                        const char *data, int data_size)
{
   int i;
   for (i = 0; i < data_size; i++)
   {
      char ch = data[i];
      if (i*2 + 3 <= string_size)
      {
         snprintf(&hexstring[i*2], 3, "%02X", ch);
      }
      else {
         break;
      }
   }
   return i;
}

int LocApiAdapter::decodeAddress(char *addr_string, int string_size,
                               const char *data, int data_size)
{
    const char addr_prefix = 0x91;
    int i, idxOutput = 0;

    if (!data || !addr_string) { return 0; }

    if (data[0] != addr_prefix)
    {
        LOC_LOGW("decodeAddress: address prefix is not 0x%x but 0x%x", addr_prefix, data[0]);
        addr_string[0] = '\0';
        return 0; // prefix not correct
    }

    for (i = 1; i < data_size; i++)
    {
        unsigned char ch = data[i], low = ch & 0x0F, hi = ch >> 4;
        if (low <= 9 && idxOutput < string_size - 1) { addr_string[idxOutput++] = low + '0'; }
        if (hi <= 9 && idxOutput < string_size - 1) { addr_string[idxOutput++] = hi + '0'; }
    }

    addr_string[idxOutput] = '\0'; // Terminates the string

    return idxOutput;
}

void LocApiAdapter::reportPosition(GpsLocation &location,
                                   GpsLocationExtended &locationExtended,
                                   void* locationExt,
                                   enum loc_sess_status status,
                                   LocPosTechMask loc_technology_mask )
{
    loc_eng_msg_report_position *msg(new loc_eng_msg_report_position(locEngHandle.owner,
                                                                     location,
                                                                     locationExtended,
                                                                     locationExt,
                                                                     status,
                                                                     loc_technology_mask));
#ifdef FEATURE_ULP
    if (locEngHandle.sendUlpMsg) {
        locEngHandle.sendUlpMsg(locEngHandle.owner, msg);
    } else {
        locEngHandle.sendMsge(locEngHandle.owner, msg);
    }
#else
    locEngHandle.sendMsge(locEngHandle.owner, msg);
#endif
}

void LocApiAdapter::reportSv(GpsSvStatus &svStatus, GpsLocationExtended &locationExtended, void* svExt)
{
    loc_eng_msg_report_sv *msg(new loc_eng_msg_report_sv(locEngHandle.owner, svStatus, locationExtended, svExt));

#ifdef FEATURE_ULP
    //We want to send SV info to ULP to help it in determining GNSS signal strength
    //ULP will forward the SV reports to HAL without any modifications
    if (locEngHandle.sendUlpMsg) {
        locEngHandle.sendUlpMsg(locEngHandle.owner, msg);
    } else {
        locEngHandle.sendMsge(locEngHandle.owner, msg);
    }
#else
    locEngHandle.sendMsge(locEngHandle.owner, msg);
#endif
}

void LocApiAdapter::reportStatus(GpsStatusValue status)
{
    loc_eng_msg_report_status *msg(new loc_eng_msg_report_status(locEngHandle.owner, status));
    locEngHandle.sendMsge(locEngHandle.owner, msg);
}

void LocApiAdapter::reportNmea(const char* nmea, int length)
{
    loc_eng_msg_report_nmea *msg(new loc_eng_msg_report_nmea(locEngHandle.owner, nmea, length));
    locEngHandle.sendMsge(locEngHandle.owner, msg);
}

void LocApiAdapter::requestATL(int connHandle, AGpsType agps_type)
{
    loc_eng_msg_request_atl *msg(new loc_eng_msg_request_atl(locEngHandle.owner, connHandle, agps_type));
    locEngHandle.sendMsge(locEngHandle.owner, msg);
}

void LocApiAdapter::releaseATL(int connHandle)
{
    loc_eng_msg_release_atl *msg(new loc_eng_msg_release_atl(locEngHandle.owner, connHandle));
    locEngHandle.sendMsge(locEngHandle.owner, msg);
}

void LocApiAdapter::requestXtraData()
{
    LOC_LOGD("XTRA download request");

    loc_eng_msg *msg(new loc_eng_msg(locEngHandle.owner, LOC_ENG_MSG_REQUEST_XTRA_DATA));
    locEngHandle.sendMsge(locEngHandle.owner, msg);
}

void LocApiAdapter::requestTime()
{
    LOC_LOGD("loc_event_cb: XTRA time download request");
    loc_eng_msg *msg(new loc_eng_msg(locEngHandle.owner, LOC_ENG_MSG_REQUEST_TIME));
    locEngHandle.sendMsge(locEngHandle.owner, msg);
}

void LocApiAdapter::requestLocation()
{
    LOC_LOGD("loc_event_cb: XTRA time download request... not supported");
    // loc_eng_msg *msg(new loc_eng_msg(locEngHandle.owner, LOC_ENG_MSG_REQUEST_POSITION));
    // locEngHandle.sendMsge(locEngHandle.owner, msg);
}

void LocApiAdapter::requestNiNotify(GpsNiNotification &notif, const void* data)
{
    notif.size = sizeof(notif);
    notif.timeout     = LOC_NI_NO_RESPONSE_TIME;

    loc_eng_msg_request_ni *msg(new loc_eng_msg_request_ni(locEngHandle.owner, notif, data));
    locEngHandle.sendMsge(locEngHandle.owner, msg);
}

void LocApiAdapter::handleEngineDownEvent()
{
    loc_eng_msg *msg(new loc_eng_msg(locEngHandle.owner, LOC_ENG_MSG_ENGINE_DOWN));
    locEngHandle.sendMsge(locEngHandle.owner, msg);
}

void LocApiAdapter::handleEngineUpEvent()
{
    loc_eng_msg *msg(new loc_eng_msg(locEngHandle.owner, LOC_ENG_MSG_ENGINE_UP));
    locEngHandle.sendMsge(locEngHandle.owner, msg);
}
