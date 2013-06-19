/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.camera;

import android.content.Context;
import android.location.GpsStatus;
import android.location.Location;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;

/**
 * A class that handles everything about location.
 */
public class LocationManager implements android.location.GpsStatus.Listener {
    private static final String TAG = "LocationManager";
    private static final short LOCATION_UPDATE_INTERVAL = 1000;

    private Context mContext;
    private Listener mListener;
    private android.location.LocationManager mLocationManager;
    private boolean mRecordLocation;
    private boolean mHasGpsFix;
    private boolean mGpsEnabled;
    private long mLastLocationMillis;

    LocationListener[] mLocationListeners = new LocationListener[] {
            new LocationListener(android.location.LocationManager.GPS_PROVIDER),
            new LocationListener(android.location.LocationManager.NETWORK_PROVIDER)
    };

    public interface Listener {
        public void showGpsOnScreenIndicator(boolean enabled, boolean hasSignal);
        public void hideGpsOnScreenIndicator();
    }

    public LocationManager(Context context, Listener listener) {
        mContext = context;
        mListener = listener;
        mHasGpsFix = false;
        mGpsEnabled = false;
    }

    public Location getCurrentLocation() {
        if (!mRecordLocation) return null;

        // go in best to worst order
        for (int i = 0; i < mLocationListeners.length; i++) {
            Location l = mLocationListeners[i].current();
            if (l != null) return l;
        }
        Log.d(TAG, "No location received yet.");
        return null;
    }

    public void recordLocation(boolean recordLocation) {
        if (mRecordLocation != recordLocation) {
            mRecordLocation = recordLocation;
            if (recordLocation) {
                startReceivingLocationUpdates();
            } else {
                stopReceivingLocationUpdates();
            }
            updateGpsIndicator();
        }
    }

    private void startReceivingLocationUpdates() {
        if (mLocationManager == null) {
            mLocationManager = (android.location.LocationManager)
                    mContext.getSystemService(Context.LOCATION_SERVICE);
        }
        if (mLocationManager != null) {
            mLocationManager.addGpsStatusListener(this);
            try {
                mLocationManager.requestLocationUpdates(
                        android.location.LocationManager.NETWORK_PROVIDER,
                        LOCATION_UPDATE_INTERVAL,
                        0F,
                        mLocationListeners[1]);
            } catch (SecurityException ex) {
                Log.i(TAG, "fail to request location update, ignore", ex);
            } catch (IllegalArgumentException ex) {
                Log.d(TAG, "provider does not exist " + ex.getMessage());
            }
            try {
                mGpsEnabled = mLocationManager.isProviderEnabled(
                        android.location.LocationManager.GPS_PROVIDER);

                mLocationManager.requestLocationUpdates(
                        android.location.LocationManager.GPS_PROVIDER,
                        LOCATION_UPDATE_INTERVAL,
                        0F,
                        mLocationListeners[0]);
            } catch (SecurityException ex) {
                Log.i(TAG, "fail to request location update, ignore", ex);
            } catch (IllegalArgumentException ex) {
                Log.d(TAG, "provider does not exist " + ex.getMessage());
            }
            Log.d(TAG, "startReceivingLocationUpdates");
        }
    }

    private void stopReceivingLocationUpdates() {
        if (mLocationManager != null) {
            for (int i = 0; i < mLocationListeners.length; i++) {
                try {
                    mLocationManager.removeUpdates(mLocationListeners[i]);
                } catch (Exception ex) {
                    Log.i(TAG, "fail to remove location listners, ignore", ex);
                }
            }
            Log.d(TAG, "stopReceivingLocationUpdates");
        }

        if (mListener != null) {
            mListener.hideGpsOnScreenIndicator();
        }
    }

    @Override
    public void onGpsStatusChanged(int event) {
        switch (event) {
            case GpsStatus.GPS_EVENT_STARTED:
                break;
            case GpsStatus.GPS_EVENT_STOPPED:
                break;
            case GpsStatus.GPS_EVENT_FIRST_FIX:
                mHasGpsFix = true;
                break;
            case GpsStatus.GPS_EVENT_SATELLITE_STATUS:
                mHasGpsFix = (SystemClock.elapsedRealtime() - mLastLocationMillis) <
                        (LOCATION_UPDATE_INTERVAL * 2);
                break;
        }
        updateGpsIndicator();
    }

    public void updateGpsIndicator() {
        if (mListener == null) {
            return;
        }
        if (mRecordLocation) {
            mListener.showGpsOnScreenIndicator(mGpsEnabled, mHasGpsFix);
        } else {
            mListener.hideGpsOnScreenIndicator();
        }
    }

    private class LocationListener
            implements android.location.LocationListener {
        Location mLastLocation;
        boolean mValid = false;
        String mProvider;

        public LocationListener(String provider) {
            mProvider = provider;
            mLastLocation = new Location(mProvider);
        }

        @Override
        public void onLocationChanged(Location newLocation) {
            if (newLocation.getLatitude() == 0.0
                    && newLocation.getLongitude() == 0.0) {
                // Hack to filter out 0.0,0.0 locations
                return;
            }
            if (!mValid) {
                Log.d(TAG, "Got first location.");
            }
            if (android.location.LocationManager.GPS_PROVIDER.equals(mProvider)) {
                mLastLocationMillis = SystemClock.elapsedRealtime();
            }
            mLastLocation.set(newLocation);
            mValid = true;
        }

        @Override
        public void onProviderEnabled(String provider) {
            if (android.location.LocationManager.GPS_PROVIDER.equals(provider)) {
                mGpsEnabled = true;
                updateGpsIndicator();
            }
        }

        @Override
        public void onProviderDisabled(String provider) {
            mValid = false;
            if (android.location.LocationManager.GPS_PROVIDER.equals(provider)) {
                mGpsEnabled = false;
                updateGpsIndicator();
            }
        }

        @Override
        public void onStatusChanged(
                String provider, int status, Bundle extras) {
        }

        public Location current() {
            return mValid ? mLastLocation : null;
        }
    }
}
