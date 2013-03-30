/*
 * Copyright (C) 2012 The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.internal.telephony;

import static com.android.internal.telephony.RILConstants.*;

import android.content.Context;
import android.os.AsyncResult;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Parcel;
import android.telephony.SignalStrength;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.telephony.IccCardApplicationStatus;
import com.android.internal.telephony.IccCardStatus;

import java.util.ArrayList;

/**
 * Qualcomm RIL class for basebands that do not send the SIM status
 * piggybacked in RIL_UNSOL_RESPONSE_RADIO_STATE_CHANGED. Instead,
 * these radios will send radio state and we have to query for SIM
 * status separately.
 *
 * {@hide}
 */
public class HTC8960RIL extends QualcommSharedRIL implements CommandsInterface {

    private static final int RIL_UNSOL_ENTER_LPM = 1523;
    private static final int RIL_UNSOL_CDMA_3G_INDICATOR = 3009;
    private static final int RIL_UNSOL_CDMA_ENHANCE_ROAMING_INDICATOR = 3012;
    private static final int RIL_UNSOL_RESPONSE_PHONE_MODE_CHANGE = 6002;
    private static final int RIL_UNSOL_RESPONSE_VOICE_RADIO_TECH_CHANGED = 21004;
    private static final int RIL_UNSOL_RESPONSE_IMS_NETWORK_STATE_CHANGED = 21005;
    private static final int RIL_UNSOL_RESPONSE_DATA_NETWORK_STATE_CHANGED = 21007;

    public HTC8960RIL(Context context, int networkMode, int cdmaSubscription) {
        super(context, networkMode, cdmaSubscription);
    }

    @Override
    protected Object
    responseIccCardStatus(Parcel p) {
        IccCardApplicationStatus appStatus;

        // use old needsOldRilFeature method for feature. it would be redundant to make
        // a new method just for naming sake.
        boolean oldRil = needsOldRilFeature("icccardstatus");

        IccCardStatus cardStatus = new IccCardStatus();
        cardStatus.setCardState(p.readInt());
        cardStatus.setUniversalPinState(p.readInt());
        cardStatus.mGsmUmtsSubscriptionAppIndex = p.readInt();
        cardStatus.mCdmaSubscriptionAppIndex = p.readInt();

        if (!oldRil)
            cardStatus.mImsSubscriptionAppIndex = p.readInt();

        int numApplications = p.readInt();

        // limit to maximum allowed applications
        if (numApplications > IccCardStatus.CARD_MAX_APPS) {
            numApplications = IccCardStatus.CARD_MAX_APPS;
        }
        cardStatus.mApplications = new IccCardApplicationStatus[numApplications];

        for (int i = 0 ; i < numApplications ; i++) {
            appStatus = new IccCardApplicationStatus();
            appStatus.app_type       = appStatus.AppTypeFromRILInt(p.readInt());
            appStatus.app_state      = appStatus.AppStateFromRILInt(p.readInt());
            appStatus.perso_substate = appStatus.PersoSubstateFromRILInt(p.readInt());
            if ((appStatus.app_state == IccCardApplicationStatus.AppState.APPSTATE_SUBSCRIPTION_PERSO) &&
                ((appStatus.perso_substate == IccCardApplicationStatus.PersoSubState.PERSOSUBSTATE_READY) ||
                (appStatus.perso_substate == IccCardApplicationStatus.PersoSubState.PERSOSUBSTATE_UNKNOWN))) {
                // ridiculous HTC hack
                appStatus.app_state = IccCardApplicationStatus.AppState.APPSTATE_UNKNOWN;
                Log.d(LOG_TAG, "ca.app_state == AppState.APPSTATE_SUBSCRIPTION_PERSO");
                Log.d(LOG_TAG, "ca.perso_substate == PersoSubState.PERSOSUBSTATE_READY");
            }
            appStatus.aid            = p.readString();
            appStatus.app_label      = p.readString();
            appStatus.pin1_replaced  = p.readInt();
            appStatus.pin1           = appStatus.PinStateFromRILInt(p.readInt());
            appStatus.pin2           = appStatus.PinStateFromRILInt(p.readInt());
            cardStatus.mApplications[i] = appStatus;
        }
        int appIndex = -1;
        if (mPhoneType == RILConstants.CDMA_PHONE && !skipCdmaSubcription) {
            appIndex = cardStatus.mCdmaSubscriptionAppIndex;
            Log.d(LOG_TAG, "This is a CDMA PHONE " + appIndex);
        } else {
            appIndex = cardStatus.mGsmUmtsSubscriptionAppIndex;
            Log.d(LOG_TAG, "This is a GSM PHONE " + appIndex);
        }

        if (numApplications > 0) {
            IccCardApplicationStatus application = cardStatus.mApplications[appIndex];
            mAid = application.aid;
            mUSIM = application.app_type
                      == IccCardApplicationStatus.AppType.APPTYPE_USIM;
            mSetPreferredNetworkType = mPreferredNetworkType;

            if (TextUtils.isEmpty(mAid))
               mAid = "";
            Log.d(LOG_TAG, "mAid " + mAid);
        }
        return cardStatus;
    }

    @Override
    protected Object
    responseSignalStrength(Parcel p) {
        /* HTC signal strength format:
         * 0: GW_SignalStrength
         * 1: GW_SignalStrength.bitErrorRate
         * 2: CDMA_SignalStrength.dbm
         * 3: CDMA_SignalStrength.ecio
         * 4: EVDO_SignalStrength.dbm
         * 5: EVDO_SignalStrength.ecio
         * 6: EVDO_SignalStrength.signalNoiseRatio
         * 7: ATT_SignalStrength.dbm
         * 8: ATT_SignalStrength.ecno
         * 9: LTE_SignalStrength.signalStrength
         * 10: LTE_SignalStrength.rsrp
         * 11: LTE_SignalStrength.rsrq
         * 12: LTE_SignalStrength.rssnr
         * 13: LTE_SignalStrength.cqi
         */

        int gsmSignalStrength = p.readInt();
        int gsmBitErrorRate = p.readInt();
        int cdmaDbm = p.readInt();
        int cdmaEcio = p.readInt();
        int evdoDbm = p.readInt();
        int evdoEcio = p.readInt();
        int evdoSnr = p.readInt();
        p.readInt(); // ATT_SignalStrength.dbm
        p.readInt(); // ATT_SignalStrength.ecno
        int lteSignalStrength = p.readInt();
        int lteRsrp = p.readInt();
        int lteRsrq = p.readInt();
        int lteRssnr = p.readInt();
        int lteCqi = p.readInt();
        boolean isGsm = (mPhoneType == RILConstants.GSM_PHONE);

        SignalStrength signalStrength = new SignalStrength(gsmSignalStrength,
                gsmBitErrorRate, cdmaDbm, cdmaEcio, evdoDbm, evdoEcio, evdoSnr,
                lteSignalStrength, lteRsrp, lteRsrq, lteRssnr, lteCqi, isGsm);

        return signalStrength;
    }

    @Override
    protected void
    processUnsolicited (Parcel p) {
        Object ret;
        int dataPosition = p.dataPosition(); // save off position within the Parcel
        int response = p.readInt();

        switch(response) {
            case RIL_UNSOL_ENTER_LPM: ret = responseVoid(p); break;
            case RIL_UNSOL_CDMA_3G_INDICATOR:  ret = responseInts(p); break;
            case RIL_UNSOL_CDMA_ENHANCE_ROAMING_INDICATOR:  ret = responseInts(p); break;
            case RIL_UNSOL_RESPONSE_PHONE_MODE_CHANGE:  ret = responseInts(p); break;
            case RIL_UNSOL_RESPONSE_VOICE_RADIO_TECH_CHANGED: ret = responseVoid(p); break;
            case RIL_UNSOL_RESPONSE_IMS_NETWORK_STATE_CHANGED: ret = responseVoid(p); break;
            case RIL_UNSOL_RESPONSE_DATA_NETWORK_STATE_CHANGED: ret = responseVoid(p); break;

            default:
                // Rewind the Parcel
                p.setDataPosition(dataPosition);

                // Forward responses that we are not overriding to the super class
                super.processUnsolicited(p);
                return;
        }

        switch(response) {
            case RIL_UNSOL_ENTER_LPM:
            case RIL_UNSOL_CDMA_3G_INDICATOR:
            case RIL_UNSOL_CDMA_ENHANCE_ROAMING_INDICATOR:
            case RIL_UNSOL_RESPONSE_PHONE_MODE_CHANGE:
            case RIL_UNSOL_RESPONSE_VOICE_RADIO_TECH_CHANGED:
            case RIL_UNSOL_RESPONSE_IMS_NETWORK_STATE_CHANGED:
            case RIL_UNSOL_RESPONSE_DATA_NETWORK_STATE_CHANGED:
                if (RILJ_LOGD) unsljLogRet(response, ret);

                if (mExitEmergencyCallbackModeRegistrants != null) {
                    mExitEmergencyCallbackModeRegistrants.notifyRegistrants(
                                        new AsyncResult (null, null, null));
                }
                break;
        }
    }

}
