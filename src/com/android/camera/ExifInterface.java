/*
 * Copyright (C) 2007 The Android Open Source Project
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

import java.util.HashMap;
import java.util.Iterator;

import android.util.Config;
import android.util.Log;

// Wrapper for native Exif library

public class ExifInterface {

    private String mFilename;

    // Constants used for the Orientation Exif tag.
    static final int ORIENTATION_UNDEFINED = 0;
    static final int ORIENTATION_NORMAL = 1;
    static final int ORIENTATION_FLIP_HORIZONTAL = 2;   // left right reversed mirror
    static final int ORIENTATION_ROTATE_180 = 3;
    static final int ORIENTATION_FLIP_VERTICAL = 4;     // upside down mirror
    static final int ORIENTATION_TRANSPOSE = 5;         // flipped about top-left <--> bottom-right axis
    static final int ORIENTATION_ROTATE_90 = 6;         // rotate 90 cw to right it
    static final int ORIENTATION_TRANSVERSE = 7;        // flipped about top-right <--> bottom-left axis
    static final int ORIENTATION_ROTATE_270 = 8;        // rotate 270 to right it

    // The Exif tag names
    static final String TAG_ORIENTATION = "Orientation";
    static final String TAG_DATE_TIME_ORIGINAL = "DateTimeOriginal";
    static final String TAG_MAKE = "Make";
    static final String TAG_MODEL = "Model";
    static final String TAG_FLASH = "Flash";
    static final String TAG_IMAGE_WIDTH = "ImageWidth";
    static final String TAG_IMAGE_LENGTH = "ImageLength";

    static final String TAG_GPS_LATITUDE = "GPSLatitude";
    static final String TAG_GPS_LONGITUDE = "GPSLongitude";
    
    static final String TAG_GPS_LATITUDE_REF = "GPSLatitudeRef";
    static final String TAG_GPS_LONGITUDE_REF = "GPSLongitudeRef";

    private boolean mSavedAttributes = false;
    private boolean mHasThumbnail = false;
    private HashMap<String, String> mCachedAttributes = null;

    static {
        System.loadLibrary("exif");
    }

    public ExifInterface(String fileName) {
        mFilename = fileName;
    }

    /**
     * Given a HashMap of Exif tags and associated values, an Exif section in the JPG file
     * is created and loaded with the tag data. saveAttributes() is expensive because it involves
     * copying all the JPG data from one file to another and deleting the old file and renaming the other.
     * It's best to collect all the attributes to write and make a single call rather than multiple
     *  calls for each attribute. You must call "commitChanges()" at some point to commit the changes.
     */
    public void saveAttributes(HashMap<String, String> attributes) {
        // format of string passed to native C code:
        // "attrCnt attr1=valueLen value1attr2=value2Len value2..."
        // example: "4 attrPtr ImageLength=4 1024Model=6 FooImageWidth=4 1280Make=3 FOO"
        StringBuilder sb = new StringBuilder();
        int size = attributes.size();
        if (attributes.containsKey("hasThumbnail")) {
            --size;
        }
        sb.append(size + " ");
        Iterator keyIterator = attributes.keySet().iterator();
        while (keyIterator.hasNext()) {
            String key = (String)keyIterator.next();
            if (key.equals("hasThumbnail")) {
                continue;       // this is a fake attribute not saved as an exif tag
            }
            String val = (String)attributes.get(key);
            sb.append(key + "=");
            sb.append(val.length() + " ");
            sb.append(val);
        }
        String s = sb.toString();
        if (android.util.Config.LOGV)
            android.util.Log.v("camera", "saving exif data: " + s);
        saveAttributesNative(mFilename, s);
        mSavedAttributes = true;
    }

    /**
     * Returns a HashMap loaded with the Exif attributes of the file. The key is the standard
     * tag name and the value is the tag's value: e.g. Model -> Nikon. Numeric values are
     * returned as strings.
     */
    public HashMap<String, String> getAttributes() {
        if (mCachedAttributes != null) {
            return mCachedAttributes;
        }
        // format of string passed from native C code:
        // "attrCnt attr1=valueLen value1attr2=value2Len value2..."
        // example: "4 attrPtr ImageLength=4 1024Model=6 FooImageWidth=4 1280Make=3 FOO"
        mCachedAttributes = new HashMap<String, String>();

        String attrStr = getAttributesNative(mFilename);

        // get count
        int ptr = attrStr.indexOf(' ');
        int count = Integer.parseInt(attrStr.substring(0, ptr));
        ++ptr;  // skip past the space between item count and the rest of the attributes

        for (int i = 0; i < count; i++) {
            // extract the attribute name
            int equalPos = attrStr.indexOf('=', ptr);
            String attrName = attrStr.substring(ptr, equalPos);
            ptr = equalPos + 1;     // skip past =

            // extract the attribute value length
            int lenPos = attrStr.indexOf(' ', ptr);
            int attrLen = Integer.parseInt(attrStr.substring(ptr, lenPos));
            ptr = lenPos + 1;       // skip pas the space

            // extract the attribute value
            String attrValue = attrStr.substring(ptr, ptr + attrLen);
            ptr += attrLen;

            if (attrName.equals("hasThumbnail")) {
                mHasThumbnail = attrValue.equalsIgnoreCase("true");
            } else {
                mCachedAttributes.put(attrName, attrValue);
            }
        }
        return mCachedAttributes;
    }

    /**
     * Given a numerical orientation, return a human-readable string describing the orientation.
     */
    static public String orientationToString(int orientation) {
        // TODO: this function needs to be localized and use string resource ids rather than strings
        String orientationString;
        switch (orientation) {
            case ORIENTATION_NORMAL:            orientationString = "Normal";   break;
            case ORIENTATION_FLIP_HORIZONTAL:   orientationString = "Flipped horizontal";   break;
            case ORIENTATION_ROTATE_180:        orientationString = "Rotated 180 degrees";   break;
            case ORIENTATION_FLIP_VERTICAL:     orientationString = "Upside down mirror";   break;
            case ORIENTATION_TRANSPOSE:         orientationString = "Transposed";   break;
            case ORIENTATION_ROTATE_90:         orientationString = "Rotated 90 degrees";   break;
            case ORIENTATION_TRANSVERSE:        orientationString = "Transversed";   break;
            case ORIENTATION_ROTATE_270:        orientationString = "Rotated 270 degrees";   break;
            default:                            orientationString = "Undefined";   break;
        }
        return orientationString;
    }

    /**
     * Copies the thumbnail data out of the filename and puts it in the Exif data associated
     * with the file used to create this object. You must call "commitChanges()" at some point
     * to commit the changes.
     */
    public boolean appendThumbnail(String thumbnailFileName) {
        if (!mSavedAttributes) {
            throw new RuntimeException("Must call saveAttributes before calling appendThumbnail");
        }
        mHasThumbnail = appendThumbnailNative(mFilename, thumbnailFileName);
        return mHasThumbnail;
    }

    /**
     * Saves the changes (added Exif tags, added thumbnail) to the JPG file. You have to call
     * saveAttributes() before committing the changes.
     */
    public void commitChanges() {
        if (!mSavedAttributes) {
            throw new RuntimeException("Must call saveAttributes before calling commitChanges");
        }
        commitChangesNative(mFilename);
    }

    public boolean hasThumbnail() {
        if (!mSavedAttributes) {
            getAttributes();
        }
        return mHasThumbnail;
    }

    public byte[] getThumbnail() {
        return getThumbnailNative(mFilename);
    }

    static public String convertRationalLatLonToDecimalString(String rationalString, String ref, boolean usePositiveNegative) {
        try {
            String [] parts = rationalString.split(",");

            String [] pair;
            pair = parts[0].split("/");
            int degrees = (int) (Float.parseFloat(pair[0].trim()) / Float.parseFloat(pair[1].trim()));

            pair = parts[1].split("/");
            int minutes = (int) ((Float.parseFloat(pair[0].trim()) / Float.parseFloat(pair[1].trim())));

            pair = parts[2].split("/");
            float seconds = Float.parseFloat(pair[0].trim()) / Float.parseFloat(pair[1].trim());

            float result = degrees + (minutes/60F) + (seconds/(60F*60F));
            
            String preliminaryResult = String.valueOf(result);
            if (usePositiveNegative) {
                String neg = (ref.equals("S") || ref.equals("E")) ? "-" : "";
                return neg + preliminaryResult;
            } else {
                return preliminaryResult + String.valueOf((char)186) + " " + ref; 
            }
        } catch (Exception ex) {
            // if for whatever reason we can't parse the lat long then return null
            return null;
        }
    }
        
    static public String makeLatLongString(double d) {
        d = Math.abs(d);
        
        int degrees = (int) d;
        
        double remainder = d - (double)degrees;
        int minutes = (int) (remainder * 60D);
        int seconds = (int) (((remainder * 60D) - minutes) * 60D * 1000D);  // really seconds * 1000
        
        String retVal = degrees + "/1," + minutes + "/1," + (int)seconds + "/1000";
        return retVal;
    }
    
    static public String makeLatStringRef(double lat) {
        return lat >= 0D ? "N" : "S";
    }
    
    static public String makeLonStringRef(double lon) {
        return lon >= 0D ? "W" : "E";
    }

    private native boolean appendThumbnailNative(String fileName, String thumbnailFileName);

    private native void saveAttributesNative(String fileName, String compressedAttributes);

    private native String getAttributesNative(String fileName);

    private native void commitChangesNative(String fileName);

    private native byte[] getThumbnailNative(String fileName);
}
