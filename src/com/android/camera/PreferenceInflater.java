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

import android.content.Context;
import android.util.AttributeSet;
import android.util.Xml;
import android.view.InflateException;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Inflate <code>CameraPreference</code> from XML resource.
 */
public class PreferenceInflater {
    private static final String PACKAGE_NAME =
            PreferenceInflater.class.getPackage().getName();

    private static final Class<?>[] CTOR_SIGNATURE =
            new Class[] {Context.class, AttributeSet.class};
    private static final HashMap<String, Constructor<?>> sConstructorMap =
            new HashMap<String, Constructor<?>>();

    private Context mContext;

    public PreferenceInflater(Context context) {
        mContext = context;
    }

    public CameraPreference inflate(int resId) {
        return inflate(mContext.getResources().getXml(resId));
    }

    private CameraPreference newPreference(String tagName, Object[] args) {
        String name = PACKAGE_NAME + "." + tagName;
        Constructor<?> constructor = sConstructorMap.get(name);
        try {
            if (constructor == null) {
                // Class not found in the cache, see if it's real, and try to
                // add it
                Class<?> clazz = mContext.getClassLoader().loadClass(name);
                constructor = clazz.getConstructor(CTOR_SIGNATURE);
                sConstructorMap.put(name, constructor);
            }
            return (CameraPreference) constructor.newInstance(args);
        } catch (NoSuchMethodException e) {
            throw new InflateException("Error inflating class " + name, e);
        } catch (ClassNotFoundException e) {
            throw new InflateException("No such class: " + name, e);
        } catch (Exception e) {
            throw new InflateException("While create instance of" + name, e);
        }
    }

    private CameraPreference inflate(XmlPullParser parser) {

        AttributeSet attrs = Xml.asAttributeSet(parser);
        ArrayList<CameraPreference> list = new ArrayList<CameraPreference>();
        Object args[] = new Object[]{mContext, attrs};

        try {
            for (int type = parser.next();
                    type != XmlPullParser.END_DOCUMENT; type = parser.next()) {
                if (type != XmlPullParser.START_TAG) continue;
                CameraPreference pref = newPreference(parser.getName(), args);

                int depth = parser.getDepth();
                if (depth > list.size()) {
                    list.add(pref);
                } else {
                    list.set(depth - 1, pref);
                }
                if (depth > 1) {
                    ((PreferenceGroup) list.get(depth - 2)).addChild(pref);
                }
            }

            if (list.size() == 0) {
                throw new InflateException("No root element found");
            }
            return list.get(0);
        } catch (XmlPullParserException e) {
            throw new InflateException(e);
        } catch (IOException e) {
            throw new InflateException(parser.getPositionDescription(), e);
        }
    }
}
