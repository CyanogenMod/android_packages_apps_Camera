/*
 * Copyright (C) 2010 The Android Open Source Project
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

import android.util.Log;

import com.android.gallery3d.exif.ExifInvalidFormatException;
import com.android.gallery3d.exif.ExifParser;
import com.android.gallery3d.exif.ExifTag;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

public class Exif {
    private static final String TAG = "CameraExif";

    // Returns the degrees in clockwise. Values are 0, 90, 180, or 270.
    public static int getOrientation(byte[] jpeg) {
        if (jpeg == null) return 0;

        InputStream is = new ByteArrayInputStream(jpeg);

        try {
            ExifParser parser = ExifParser.parse(is, ExifParser.OPTION_IFD_0);
            int event = parser.next();
            while(event != ExifParser.EVENT_END) {
                if (event == ExifParser.EVENT_NEW_TAG) {
                    ExifTag tag = parser.getTag();
                    if (tag.getTagId() == ExifTag.TAG_ORIENTATION &&
                            tag.hasValue()) {
                        int orient = (int) tag.getValueAt(0);
                        switch (orient) {
                            case ExifTag.Orientation.TOP_LEFT:
                                return 0;
                            case ExifTag.Orientation.BOTTOM_LEFT:
                                return 180;
                            case ExifTag.Orientation.RIGHT_TOP:
                                return 90;
                            case ExifTag.Orientation.RIGHT_BOTTOM:
                                return 270;
                            default:
                                Log.i(TAG, "Unsupported orientation");
                                return 0;
                        }
                    }
                }
                event = parser.next();
            }
            Log.i(TAG, "Orientation not found");
            return 0;
        } catch (IOException e) {
            Log.w(TAG, "Failed to read EXIF orientation", e);
            return 0;
        } catch (ExifInvalidFormatException e) {
            Log.w(TAG, "Failed to read EXIF orientation", e);
            return 0;
        }
    }
}
