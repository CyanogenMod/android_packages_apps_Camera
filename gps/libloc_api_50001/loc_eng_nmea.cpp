/* Copyright (c) 2012, The Linux Foundation. All rights reserved.
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

#define LOG_NDDEBUG 0
#define LOG_TAG "LocSvc_eng_nmea"

#include <loc_eng.h>
#include <loc_eng_nmea.h>
#include <math.h>
#include "log_util.h"

/*===========================================================================
FUNCTION    loc_eng_nmea_send

DESCRIPTION
   send out NMEA sentence

DEPENDENCIES
   NONE

RETURN VALUE
   Total length of the nmea sentence

SIDE EFFECTS
   N/A

===========================================================================*/
void loc_eng_nmea_send(char *pNmea, int length, loc_eng_data_s_type *loc_eng_data_p)
{
    struct timeval tv;
    gettimeofday(&tv, (struct timezone *) NULL);
    int64_t now = tv.tv_sec * 1000LL + tv.tv_usec / 1000;
    CALLBACK_LOG_CALLFLOW("nmea_cb", %p, pNmea);
    loc_eng_data_p->nmea_cb(now, pNmea, length);
    LOC_LOGD("NMEA <%s", pNmea);
}

/*===========================================================================
FUNCTION    loc_eng_nmea_put_checksum

DESCRIPTION
   Generate NMEA sentences generated based on position report

DEPENDENCIES
   NONE

RETURN VALUE
   Total length of the nmea sentence

SIDE EFFECTS
   N/A

===========================================================================*/
int loc_eng_nmea_put_checksum(char *pNmea, int maxSize)
{
    uint8_t checksum = 0;
    int length = 0;

    pNmea++; //skip the $
    while (*pNmea != '\0')
    {
        checksum ^= *pNmea++;
        length++;
    }

    int checksumLength = snprintf(pNmea, maxSize,"*%02X\r\n", checksum);
    return (length + checksumLength);
}

/*===========================================================================
FUNCTION    loc_eng_nmea_generate_pos

DESCRIPTION
   Generate NMEA sentences generated based on position report

DEPENDENCIES
   NONE

RETURN VALUE
   0

SIDE EFFECTS
   N/A

===========================================================================*/
void loc_eng_nmea_generate_pos(loc_eng_data_s_type *loc_eng_data_p,
                               const GpsLocation &location, const GpsLocationExtended &locationExtended)
{
    ENTRY_LOG();

    char sentence[NMEA_SENTENCE_MAX_LENGTH] = {0};
    char* pMarker = sentence;
    int lengthRemaining = sizeof(sentence);
    int length = 0;

    time_t utcTime(location.timestamp/1000);
    tm * pTm = gmtime(&utcTime);
    int utcYear = pTm->tm_year % 100; // 2 digit year
    int utcMonth = pTm->tm_mon + 1; // tm_mon starts at zero
    int utcDay = pTm->tm_mday;
    int utcHours = pTm->tm_hour;
    int utcMinutes = pTm->tm_min;
    int utcSeconds = pTm->tm_sec;

    // ------------------
    // ------$GPGSA------
    // ------------------

    uint32_t svUsedCount = 0;
    uint32_t svUsedList[32] = {0};
    uint32_t mask = loc_eng_data_p->sv_used_mask;
    for (uint8_t i = 1; mask > 0 && svUsedCount < 32; i++)
    {
        if (mask & 1)
            svUsedList[svUsedCount++] = i;
        mask = mask >> 1;
    }
    // clear the cache so they can't be used again
    loc_eng_data_p->sv_used_mask = 0;

    char fixType;
    if (svUsedCount == 0)
        fixType = '1'; // no fix
    else if (svUsedCount <= 3)
        fixType = '2'; // 2D fix
    else
        fixType = '3'; // 3D fix

    length = snprintf(pMarker, lengthRemaining, "$GPGSA,A,%c,", fixType);

    if (length < 0 || length >= lengthRemaining)
    {
        LOC_LOGE("NMEA Error in string formatting");
        return;
    }
    pMarker += length;
    lengthRemaining -= length;

    for (uint8_t i = 0; i < 12; i++) // only the first 12 sv go in sentence
    {
        if (i < svUsedCount)
            length = snprintf(pMarker, lengthRemaining, "%02d,", svUsedList[i]);
        else
            length = snprintf(pMarker, lengthRemaining, ",");

        if (length < 0 || length >= lengthRemaining)
        {
            LOC_LOGE("NMEA Error in string formatting");
            return;
        }
        pMarker += length;
        lengthRemaining -= length;
    }

    if (locationExtended.flags & GPS_LOCATION_EXTENDED_HAS_DOP)
    {   // dop is in locationExtended, (QMI)
        length = snprintf(pMarker, lengthRemaining, "%.1f,%.1f,%.1f",
                          locationExtended.pdop,
                          locationExtended.hdop,
                          locationExtended.vdop);
    }
    else if (loc_eng_data_p->pdop > 0 && loc_eng_data_p->hdop > 0 && loc_eng_data_p->vdop > 0)
    {   // dop was cached from sv report (RPC)
        length = snprintf(pMarker, lengthRemaining, "%.1f,%.1f,%.1f",
                          loc_eng_data_p->pdop,
                          loc_eng_data_p->hdop,
                          loc_eng_data_p->vdop);
    }
    else
    {   // no dop
        length = snprintf(pMarker, lengthRemaining, ",,");
    }

    length = loc_eng_nmea_put_checksum(sentence, sizeof(sentence));
    loc_eng_nmea_send(sentence, length, loc_eng_data_p);

    // ------------------
    // ------$GPVTG------
    // ------------------

    pMarker = sentence;
    lengthRemaining = sizeof(sentence);

    if (location.flags & GPS_LOCATION_HAS_BEARING)
    {
        float magTrack = location.bearing;
        if (locationExtended.flags & GPS_LOCATION_EXTENDED_HAS_MAG_DEV)
        {
            float magTrack = location.bearing - locationExtended.magneticDeviation;
            if (magTrack < 0.0)
                magTrack += 360.0;
            else if (magTrack > 360.0)
                magTrack -= 360.0;
        }

        length = snprintf(pMarker, lengthRemaining, "$GPVTG,%.1lf,T,%.1lf,M,", location.bearing, magTrack);
    }
    else
    {
        length = snprintf(pMarker, lengthRemaining, "$GPVTG,,T,,M,");
    }

    if (length < 0 || length >= lengthRemaining)
    {
        LOC_LOGE("NMEA Error in string formatting");
        return;
    }
    pMarker += length;
    lengthRemaining -= length;

    if (location.flags & GPS_LOCATION_HAS_SPEED)
    {
        float speedKnots = location.speed * (3600.0/1852.0);
        float speedKmPerHour = location.speed * 3.6;

        length = snprintf(pMarker, lengthRemaining, "%.1lf,N,%.1lf,K,", speedKnots, speedKmPerHour);
    }
    else
    {
        length = snprintf(pMarker, lengthRemaining, ",N,,K,");
    }

    if (length < 0 || length >= lengthRemaining)
    {
        LOC_LOGE("NMEA Error in string formatting");
        return;
    }
    pMarker += length;
    lengthRemaining -= length;

    if (!(location.flags & GPS_LOCATION_HAS_LAT_LONG))
        length = snprintf(pMarker, lengthRemaining, "%c", 'N'); // N means no fix
    else if (LOC_POSITION_MODE_STANDALONE == loc_eng_data_p->client_handle->getPositionMode().mode)
        length = snprintf(pMarker, lengthRemaining, "%c", 'A'); // A means autonomous
    else
        length = snprintf(pMarker, lengthRemaining, "%c", 'D'); // D means differential

    length = loc_eng_nmea_put_checksum(sentence, sizeof(sentence));
    loc_eng_nmea_send(sentence, length, loc_eng_data_p);

    // ------------------
    // ------$GPRMC------
    // ------------------

    pMarker = sentence;
    lengthRemaining = sizeof(sentence);

    length = snprintf(pMarker, lengthRemaining, "$GPRMC,%02d%02d%02d,A," ,
                      utcHours, utcMinutes, utcSeconds);

    if (length < 0 || length >= lengthRemaining)
    {
        LOC_LOGE("NMEA Error in string formatting");
        return;
    }
    pMarker += length;
    lengthRemaining -= length;

    if (location.flags & GPS_LOCATION_HAS_LAT_LONG)
    {
        double latitude = location.latitude;
        double longitude = location.longitude;
        char latHemisphere;
        char lonHemisphere;
        double latMinutes;
        double lonMinutes;

        if (latitude > 0)
        {
            latHemisphere = 'N';
        }
        else
        {
            latHemisphere = 'S';
            latitude *= -1.0;
        }

        if (longitude < 0)
        {
            lonHemisphere = 'W';
            longitude *= -1.0;
        }
        else
        {
            lonHemisphere = 'E';
        }

        latMinutes = fmod(latitude * 60.0 , 60.0);
        lonMinutes = fmod(longitude * 60.0 , 60.0);

        length = snprintf(pMarker, lengthRemaining, "%02d%09.6lf,%c,%03d%09.6lf,%c,",
                          (uint8_t)floor(latitude), latMinutes, latHemisphere,
                          (uint8_t)floor(longitude),lonMinutes, lonHemisphere);
    }
    else
    {
        length = snprintf(pMarker, lengthRemaining,",,,,");
    }

    if (length < 0 || length >= lengthRemaining)
    {
        LOC_LOGE("NMEA Error in string formatting");
        return;
    }
    pMarker += length;
    lengthRemaining -= length;

    if (location.flags & GPS_LOCATION_HAS_SPEED)
    {
        float speedKnots = location.speed * (3600.0/1852.0);
        length = snprintf(pMarker, lengthRemaining, "%.1lf,", speedKnots);
    }
    else
    {
        length = snprintf(pMarker, lengthRemaining, ",");
    }

    if (length < 0 || length >= lengthRemaining)
    {
        LOC_LOGE("NMEA Error in string formatting");
        return;
    }
    pMarker += length;
    lengthRemaining -= length;

    if (location.flags & GPS_LOCATION_HAS_BEARING)
    {
        length = snprintf(pMarker, lengthRemaining, "%.1lf,", location.bearing);
    }
    else
    {
        length = snprintf(pMarker, lengthRemaining, ",");
    }

    if (length < 0 || length >= lengthRemaining)
    {
        LOC_LOGE("NMEA Error in string formatting");
        return;
    }
    pMarker += length;
    lengthRemaining -= length;

    length = snprintf(pMarker, lengthRemaining, "%2.2d%2.2d%2.2d,",
                      utcDay, utcMonth, utcYear);

    if (length < 0 || length >= lengthRemaining)
    {
        LOC_LOGE("NMEA Error in string formatting");
        return;
    }
    pMarker += length;
    lengthRemaining -= length;

    if (locationExtended.flags & GPS_LOCATION_EXTENDED_HAS_MAG_DEV)
    {
        float magneticVariation = locationExtended.magneticDeviation;
        char direction;
        if (magneticVariation < 0.0)
        {
            direction = 'W';
            magneticVariation *= -1.0;
        }
        else
        {
            direction = 'E';
        }

        length = snprintf(pMarker, lengthRemaining, "%.1lf,%c,",
                          magneticVariation, direction);
    }
    else
    {
        length = snprintf(pMarker, lengthRemaining, ",,");
    }

    if (length < 0 || length >= lengthRemaining)
    {
        LOC_LOGE("NMEA Error in string formatting");
        return;
    }
    pMarker += length;
    lengthRemaining -= length;

    if (!(location.flags & GPS_LOCATION_HAS_LAT_LONG))
        length = snprintf(pMarker, lengthRemaining, "%c", 'N'); // N means no fix
    else if (LOC_POSITION_MODE_STANDALONE == loc_eng_data_p->client_handle->getPositionMode().mode)
        length = snprintf(pMarker, lengthRemaining, "%c", 'A'); // A means autonomous
    else
        length = snprintf(pMarker, lengthRemaining, "%c", 'D'); // D means differential

    length = loc_eng_nmea_put_checksum(sentence, sizeof(sentence));
    loc_eng_nmea_send(sentence, length, loc_eng_data_p);

    // ------------------
    // ------$GPGGA------
    // ------------------

    pMarker = sentence;
    lengthRemaining = sizeof(sentence);

    length = snprintf(pMarker, lengthRemaining, "$GPGGA,%02d%02d%02d," ,
                      utcHours, utcMinutes, utcSeconds);

    if (length < 0 || length >= lengthRemaining)
    {
        LOC_LOGE("NMEA Error in string formatting");
        return;
    }
    pMarker += length;
    lengthRemaining -= length;

    if (location.flags & GPS_LOCATION_HAS_LAT_LONG)
    {
        double latitude = location.latitude;
        double longitude = location.longitude;
        char latHemisphere;
        char lonHemisphere;
        double latMinutes;
        double lonMinutes;

        if (latitude > 0)
        {
            latHemisphere = 'N';
        }
        else
        {
            latHemisphere = 'S';
            latitude *= -1.0;
        }

        if (longitude < 0)
        {
            lonHemisphere = 'W';
            longitude *= -1.0;
        }
        else
        {
            lonHemisphere = 'E';
        }

        latMinutes = fmod(latitude * 60.0 , 60.0);
        lonMinutes = fmod(longitude * 60.0 , 60.0);

        length = snprintf(pMarker, lengthRemaining, "%02d%09.6lf,%c,%03d%09.6lf,%c,",
                          (uint8_t)floor(latitude), latMinutes, latHemisphere,
                          (uint8_t)floor(longitude),lonMinutes, lonHemisphere);
    }
    else
    {
        length = snprintf(pMarker, lengthRemaining,",,,,");
    }

    if (length < 0 || length >= lengthRemaining)
    {
        LOC_LOGE("NMEA Error in string formatting");
        return;
    }
    pMarker += length;
    lengthRemaining -= length;

    char gpsQuality;
    if (!(location.flags & GPS_LOCATION_HAS_LAT_LONG))
        gpsQuality = '0'; // 0 means no fix
    else if (LOC_POSITION_MODE_STANDALONE == loc_eng_data_p->client_handle->getPositionMode().mode)
        gpsQuality = '1'; // 1 means GPS fix
    else
        gpsQuality = '2'; // 2 means DGPS fix

    if (locationExtended.flags & GPS_LOCATION_EXTENDED_HAS_DOP)
    {   // dop is in locationExtended, (QMI)
        length = snprintf(pMarker, lengthRemaining, "%c,%02d,%.1f,",
                          gpsQuality, svUsedCount, locationExtended.hdop);
    }
    else if (loc_eng_data_p->pdop > 0 && loc_eng_data_p->hdop > 0 && loc_eng_data_p->vdop > 0)
    {   // dop was cached from sv report (RPC)
        length = snprintf(pMarker, lengthRemaining, "%c,%02d,%.1f,",
                          gpsQuality, svUsedCount, loc_eng_data_p->hdop);
    }
    else
    {   // no hdop
        length = snprintf(pMarker, lengthRemaining, "%c,%02d,,",
                          gpsQuality, svUsedCount);
    }

    if (length < 0 || length >= lengthRemaining)
    {
        LOC_LOGE("NMEA Error in string formatting");
        return;
    }
    pMarker += length;
    lengthRemaining -= length;

    if (locationExtended.flags & GPS_LOCATION_EXTENDED_HAS_ALTITUDE_MEAN_SEA_LEVEL)
    {
        length = snprintf(pMarker, lengthRemaining, "%.1lf,M,",
                          locationExtended.altitudeMeanSeaLevel);
    }
    else
    {
        length = snprintf(pMarker, lengthRemaining,",,");
    }

    if (length < 0 || length >= lengthRemaining)
    {
        LOC_LOGE("NMEA Error in string formatting");
        return;
    }
    pMarker += length;
    lengthRemaining -= length;

    if ((location.flags & GPS_LOCATION_HAS_ALTITUDE) &&
        (locationExtended.flags & GPS_LOCATION_EXTENDED_HAS_ALTITUDE_MEAN_SEA_LEVEL))
    {
        length = snprintf(pMarker, lengthRemaining, "%.1lf,M,,",
                          location.altitude - locationExtended.altitudeMeanSeaLevel);
    }
    else
    {
        length = snprintf(pMarker, lengthRemaining,",,,");
    }

    length = loc_eng_nmea_put_checksum(sentence, sizeof(sentence));
    loc_eng_nmea_send(sentence, length, loc_eng_data_p);

    // clear the dop cache so they can't be used again
    loc_eng_data_p->pdop = 0;
    loc_eng_data_p->hdop = 0;
    loc_eng_data_p->vdop = 0;

    EXIT_LOG(%d, 0);
}



/*===========================================================================
FUNCTION    loc_eng_nmea_generate_sv

DESCRIPTION
   Generate NMEA sentences generated based on sv report

DEPENDENCIES
   NONE

RETURN VALUE
   0

SIDE EFFECTS
   N/A

===========================================================================*/
void loc_eng_nmea_generate_sv(loc_eng_data_s_type *loc_eng_data_p,
                              const GpsSvStatus &svStatus, const GpsLocationExtended &locationExtended)
{
    ENTRY_LOG();

    char sentence[NMEA_SENTENCE_MAX_LENGTH] = {0};
    char* pMarker = sentence;
    int lengthRemaining = sizeof(sentence);
    int length = 0;

    // ------------------
    // ------$GPGSV------
    // ------------------

    if (svStatus.num_svs <= 0)
    {
        // no svs in view, so just send a blank $GPGSV sentence
        strlcpy(sentence, "$GPGSV,1,1,0,", sizeof(sentence));
        length = loc_eng_nmea_put_checksum(sentence, sizeof(sentence));
        loc_eng_nmea_send(sentence, length, loc_eng_data_p);
    }
    else
    {
        int svCount = svStatus.num_svs;
        int sentenceCount = svCount / 4;
        if (svStatus.num_svs % 4)
            sentenceCount++;
        int sentenceNumber = 1;
        int svNumber = 1;

        while (sentenceNumber <= sentenceCount)
        {
            pMarker = sentence;
            lengthRemaining = sizeof(sentence);

            length = snprintf(pMarker, lengthRemaining, "$GPGSV,%d,%d,%02d",
                          sentenceCount, sentenceNumber, svCount);

            if (length < 0 || length >= lengthRemaining)
            {
                LOC_LOGE("NMEA Error in string formatting");
                return;
            }
            pMarker += length;
            lengthRemaining -= length;

            for (int i=0; (svNumber <= svCount) && (i < 4); i++, svNumber++)
            {
                length = snprintf(pMarker, lengthRemaining,",%02d,%02d,%03d,",
                                  svStatus.sv_list[svNumber-1].prn,
                                  (int)(0.5 + svStatus.sv_list[svNumber-1].elevation), //float to int
                                  (int)(0.5 + svStatus.sv_list[svNumber-1].azimuth)); //float to int

                if (length < 0 || length >= lengthRemaining)
                {
                    LOC_LOGE("NMEA Error in string formatting");
                    return;
                }
                pMarker += length;
                lengthRemaining -= length;

                if (svStatus.sv_list[svNumber-1].snr > 0)
                {
                    length = snprintf(pMarker, lengthRemaining,"%02d",
                                     (int)(0.5 + svStatus.sv_list[svNumber-1].snr)); //float to int

                    if (length < 0 || length >= lengthRemaining)
                    {
                        LOC_LOGE("NMEA Error in string formatting");
                        return;
                    }
                    pMarker += length;
                    lengthRemaining -= length;
                }
            }

            length = loc_eng_nmea_put_checksum(sentence, sizeof(sentence));
            loc_eng_nmea_send(sentence, length, loc_eng_data_p);
            sentenceNumber++;

        }
    }

    if (svStatus.used_in_fix_mask == 0)
    {   // No sv used, so there will be no position report, so send
        // blank NMEA sentences
        strlcpy(sentence, "$GPGSA,A,1,,,,,,,,,,,,,,,", sizeof(sentence));
        length = loc_eng_nmea_put_checksum(sentence, sizeof(sentence));
        loc_eng_nmea_send(sentence, length, loc_eng_data_p);

        strlcpy(sentence, "$GPVTG,,T,,M,,N,,K,N", sizeof(sentence));
        length = loc_eng_nmea_put_checksum(sentence, sizeof(sentence));
        loc_eng_nmea_send(sentence, length, loc_eng_data_p);

        strlcpy(sentence, "$GPRMC,,V,,,,,,,,,,N", sizeof(sentence));
        length = loc_eng_nmea_put_checksum(sentence, sizeof(sentence));
        loc_eng_nmea_send(sentence, length, loc_eng_data_p);

        strlcpy(sentence, "$GPGGA,,,,,,0,,,,,,,,", sizeof(sentence));
        length = loc_eng_nmea_put_checksum(sentence, sizeof(sentence));
        loc_eng_nmea_send(sentence, length, loc_eng_data_p);
    }
    else
    {   // cache the used in fix mask, as it will be needed to send $GPGSA
        // during the position report
        loc_eng_data_p->sv_used_mask = svStatus.used_in_fix_mask;

        // For RPC, the DOP are sent during sv report, so cache them
        // now to be sent during position report.
        // For QMI, the DOP will be in position report.
        if (locationExtended.flags & GPS_LOCATION_EXTENDED_HAS_DOP)
        {
            loc_eng_data_p->pdop = locationExtended.pdop;
            loc_eng_data_p->hdop = locationExtended.hdop;
            loc_eng_data_p->vdop = locationExtended.vdop;
        }
        else
        {
            loc_eng_data_p->pdop = 0;
            loc_eng_data_p->hdop = 0;
            loc_eng_data_p->vdop = 0;
        }

    }

    EXIT_LOG(%d, 0);
}
