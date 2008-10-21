/*
 * Copyright (C) 2006 The Android Open Source Project
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

import com.android.camera.ImageManager.IImage;
import com.android.internal.http.multipart.Part;
import com.android.internal.http.multipart.MultipartEntity;
import com.android.internal.http.multipart.PartBase;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.preference.PreferenceManager;
import android.sax.Element;
import android.sax.ElementListener;
import android.sax.EndTextElementListener;
import android.sax.RootElement;
import android.util.Config;
import android.util.Log;
import android.util.Xml;

import com.google.android.googleapps.GoogleLoginCredentialsResult;
import com.google.android.googlelogin.GoogleLoginServiceBlockingHelper;
import com.google.android.googlelogin.GoogleLoginServiceConstants;
import com.google.android.googlelogin.GoogleLoginServiceNotFoundException;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import android.net.http.AndroidHttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpGet;
import com.android.internal.http.multipart.StringPart;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicHeader;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EncodingUtils;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;

public class UploadService extends Service implements Runnable {
    private static final String TAG = "UploadService";

    static final boolean DEBUG = false;
    private static final boolean LOCAL_LOGV = DEBUG ? Config.LOGD : Config.LOGV;

    private GoogleLoginServiceBlockingHelper mGls;
    static public final int MSG_STATUS = 3;
    static public final int EVENT_UPLOAD_ERROR = 400;

    static public final String sPicasaService = "lh2";
    static public final String sYouTubeService = "youtube";
    static public final String sYouTubeUserService = "YouTubeUser";

    static public final String sUploadAlbumName = "android_upload";
    HashMap<String, Album> mAlbums;
    ArrayList<String> mAndroidUploadAlbumPhotos = null;
    HashMap<String, String> mGDataAuthTokenMap = new HashMap<String, String>();

    int mStartId;
    Thread mThread;

    android.os.Handler mHandler = new android.os.Handler() {

    };

    ArrayList<Runnable> mStatusListeners = new ArrayList<Runnable>();

    ArrayList<Uri> mUploadList = new ArrayList<Uri>();

    ImageManager.IImageList mImageList = null;

    String mPicasaUsername;
    String mPicasaAuthToken;
    String mYouTubeUsername;
    String mYouTubeAuthToken;

    AndroidHttpClient mClient = AndroidHttpClient.newInstance("Android-Camera/0.1");

    private static final ComponentName sLogin = new ComponentName(
            "com.google.android.googleapps",
            "com.google.android.googleapps.GoogleLoginService");

    public UploadService() {
        if (LOCAL_LOGV)
            Log.v(TAG, "UploadService Constructor !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
    }

    private void computeAuthToken() {
        if (LOCAL_LOGV) Log.v(TAG, "computeAuthToken()");
        if (mPicasaAuthToken != null) return;

        try {
            String account = mGls.getAccount(GoogleLoginServiceConstants.REQUIRE_GOOGLE);
            GoogleLoginCredentialsResult result =
                mGls.getCredentials(account, sPicasaService, true);
            mPicasaAuthToken = result.getCredentialsString();
            mPicasaUsername = result.getAccount();
            if (Config.LOGV)
                Log.v(TAG, "mPicasaUsername is " + mPicasaUsername);
        } catch (GoogleLoginServiceNotFoundException e) {
            Log.e(TAG, "Could not get auth token", e);
        }
    }

    private void computeYouTubeAuthToken() {
        if (LOCAL_LOGV) Log.v(TAG, "computeYouTubeAuthToken()");
        if (mYouTubeAuthToken != null) return;

        try {
            String account = mGls.getAccount(GoogleLoginServiceConstants.REQUIRE_GOOGLE);
            GoogleLoginCredentialsResult result =
                mGls.getCredentials(account, sYouTubeService, true);
            mYouTubeAuthToken = result.getCredentialsString();
            mYouTubeUsername = result.getAccount();
            if (mYouTubeAuthToken.equals("NoLinkedYouTubeAccount")) {
                // we successfully logged in to the google account, but it
                // is not linked to a YouTube username.
                if (Config.LOGV)
                    Log.v(TAG, "account " + mYouTubeUsername + " is not linked to a youtube account");
                mYouTubeAuthToken = null;
                return;
            }

            mYouTubeUsername = mGls.peekCredentials(mYouTubeUsername, sYouTubeUserService);
            // now mYouTubeUsername is the YouTube username linked to the
            // google account, which is probably what we want to display.

            if (Config.LOGV)
                Log.v(TAG, "3 mYouTubeUsername: " + mYouTubeUsername);
        } catch (GoogleLoginServiceNotFoundException e) {
            Log.e(TAG, "Could not get auth token", e);
        }
    }

    NotificationManager mNotificationManager;

    @Override
    public void onCreate() {

        try {
            mGls = new GoogleLoginServiceBlockingHelper(this);
        } catch (GoogleLoginServiceNotFoundException e) {
            Log.e(TAG, "Could not find google login service, stopping service");
            stopSelf();
        }

        if (mThread == null) {
            mThread = new Thread(this);
            mThread.start();
        }
        mNotificationManager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);

        IntentFilter intentFilter = new IntentFilter("com.android.camera.NEW_PICTURE");
        b = new android.content.BroadcastReceiver() {
            public void onReceive(android.content.Context ctx, Intent intent) {
                android.content.SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
                if (prefs.getBoolean("pref_camera_autoupload_key", false)) {
                    if (Config.LOGV)
                        Log.v(TAG, ">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> auto upload " + intent.getData());
                }
            }
        };
        registerReceiver(b, intentFilter);
    }
    
    android.content.BroadcastReceiver b = null;

    @Override
    public void onDestroy() {
        mGls.close();
        if (b != null) {
            unregisterReceiver(b);
        }
    }

    @Override
    public void onStart(Intent intent, int startId) {
        if (LOCAL_LOGV)
            Log.v(TAG, "UploadService.onStart; this is " + hashCode());

        if (mImageList == null) {
            mImageList = ImageManager.instance().allImages(
                this,
                getContentResolver(),
                ImageManager.DataLocation.ALL,
                ImageManager.INCLUDE_IMAGES | ImageManager.INCLUDE_VIDEOS,
                ImageManager.SORT_ASCENDING);
            mImageList.setOnChangeListener(new ImageManager.IImageList.OnChange() {
                public void onChange(ImageManager.IImageList list) {
                    /*
                    Log.v(TAG, "onChange <<<<<<<<<<<<<<<<<<<<<<<<<");
                    for (int i = 0; i < list.getCount(); i++) {
                        ImageManager.IImage img = list.getImageAt(i);
                        Log.v(TAG, "pos " + i + " " + img.fullSizeImageUri());
                        String picasaId = img.getPicasaId();
                        if (picasaId == null || picasaId.length() == 0) {
                            synchronized (mUploadList) {
                                Uri uri = img.fullSizeImageUri();
                                if (mUploadList.contains(uri)) {
                                    mUploadList.add(img.fullSizeImageUri());
                                    mUploadList.notify();
                                }
                            }
                        }
                    }
                    */
                }
            }, mHandler);
        }

        if (LOCAL_LOGV)
            Log.v(TAG, "got image list with count " + mImageList.getCount());

        synchronized (mUploadList) {
            mStartId = startId;
            String uriString = intent.getStringExtra("imageuri");

            if (LOCAL_LOGV)
                Log.v(TAG, "starting UploadService; startId = " + startId + " start uri: " + uriString);

            if (uriString != null) {
                Uri uri = Uri.parse(uriString);
                IImage image = mImageList.getImageForUri(uri);
                if (!mUploadList.contains(uri)) {
                    if (LOCAL_LOGV)
                        Log.v(TAG, "queing upload of " + image.fullSizeImageUri());
                    mUploadList.add(uri);
                }
            } else {
                // for now upload all applies to images only, not videos
                for (int i = 0; i < mImageList.getCount(); i++) {
                    IImage image = mImageList.getImageAt(i);
                    if (image instanceof ImageManager.Image) {
                        Uri uri = image.fullSizeImageUri();
                        if (!mUploadList.contains(uri)) {
                            if (LOCAL_LOGV)
                                Log.v(TAG, "queing upload of " + image.fullSizeImageUri());
                            mUploadList.add(uri);
                        }
                    }
                }
            }
            updateNotification();
        }

        synchronized(mUploadList) {
            mUploadList.notify();
        }
    }

    void updateNotification() {
        int videosCount = 0, imagesCount = 0;
        for (int i = 0;i < mUploadList.size(); i++) {
            // TODO yes this is a hack
            Uri uri = mUploadList.get(i);
            if (uri.toString().contains("video"))
                videosCount += 1;
            else
                imagesCount += 1;
        }
        updateNotification(imagesCount, videosCount);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    // This is the object that recieves interactions from clients.
    private final IBinder mBinder = new Binder() {
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
            return true;
        }
    };

    private void updateNotification(int pendingImagesCount, int pendingVideosCount) {
        final int mVideoUploadId = 1;
        final int mImageUploadId = 2;
        if (pendingImagesCount == 0) {
            if (mNotificationManager != null)
                mNotificationManager.cancel(mImageUploadId);
        } else {
            String detailedMsg = String.format(getResources().getString(R.string.uploadingNPhotos), pendingImagesCount);
            Notification n = new Notification(
                    this,
                    android.R.drawable.stat_sys_upload,
                    getResources().getString(R.string.uploading_photos),
                    System.currentTimeMillis(),
                    getResources().getString(R.string.uploading_photos_2),
                    detailedMsg,
                    null);
            mNotificationManager.notify(mImageUploadId, n);
        }
        if (pendingVideosCount == 0) {
            if (mNotificationManager != null)
                mNotificationManager.cancel(mVideoUploadId);
        } else {
            String detailedMsg = String.format(getResources().getString(R.string.uploadingNVideos), pendingImagesCount);
            Notification n = new Notification(
                    this,
                    android.R.drawable.stat_sys_upload,
                    getResources().getString(R.string.uploading_videos),
                    System.currentTimeMillis(),
                    getResources().getString(R.string.uploading_videos_2),
                    detailedMsg,
                    null);
            mNotificationManager.notify(mVideoUploadId, n);
        }
    }

    public void run() {
        try {
            if (Config.LOGV)
                Log.v(TAG, "running upload thread...");
            while (true) {
                IImage image = null;
                synchronized (mUploadList) {
                    if (LOCAL_LOGV)
                        Log.v(TAG, "mUploadList.size() is " + mUploadList.size());
                    if (mUploadList.size() == 0) {
                        try {
                            updateNotification(0, 0);
                            if (Config.LOGV)
                                Log.v(TAG, "waiting...");
                            mUploadList.wait(60000);
                            if (Config.LOGV)
                                Log.v(TAG, "done waiting...");
                        } catch (InterruptedException ex) {
                        }
                        if (mUploadList.size() == 0) {
//                          if (LOCAL_LOGV) Log.v(TAG, "exiting run, stoping service");
//                          stopSelf(mStartId);
//                          break;
                            continue;
                        }
                    }
                    Uri uri = mUploadList.get(0);
                    image = mImageList.getImageForUri(uri);
                    if (Config.LOGV)
                        Log.v(TAG, "got uri " + uri + " " + image);
                }

                boolean success = false;
                if (image != null) {
                    updateNotification();

                    long t1 = System.currentTimeMillis();
                    success = uploadItem(image);
                    long t2 = System.currentTimeMillis();
                    if (LOCAL_LOGV) Log.v(TAG, "upload took " + (t2-t1) + "; success = " + success);
                }

                synchronized (mUploadList) {
                    mUploadList.remove(0);
                    if (!success && image != null) {
                        mUploadList.add(image.fullSizeImageUri());
                    }
                }
                if (!success) {
                    int retryDelay = 30000;
                    if (LOCAL_LOGV)
                        Log.v(TAG, "failed to upload " + image.fullSizeImageUri() + " trying again in " + retryDelay + " ms");
                    try {
                        synchronized (mUploadList) {
                            long t1x = System.currentTimeMillis();
                            mUploadList.wait(retryDelay);
                            long t2x = System.currentTimeMillis();
                            if (Config.LOGV)
                                Log.v(TAG, "retry waited " + (t2x-t1x));
                        }
                    } catch (InterruptedException ex) {
                        if (Config.LOGV)
                            Log.v(TAG, "ping, was waiting but now retry again");
                    };
                }
            }
        } catch (Exception ex) {
            Log.e(TAG, "got exception in upload thread", ex);
        }
        finally {
            if (LOCAL_LOGV)
                Log.v(TAG, "finished task");
        }
    }

    private String getLatLongString(IImage image) {
        if (image.hasLatLong()) {
            return  "<georss:where><gml:Point><gml:pos>"
            +   image.getLatitude()
            +   " "
            +   image.getLongitude()
            +   "</gml:pos></gml:Point></georss:where>";
        } else {
            return "";
        }
    }

    private String uploadAlbumName() {
        android.content.SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String s = prefs.getString("pref_camera_upload_albumname_key", sUploadAlbumName);
        return s;
    }

    private boolean uploadItem(IImage image) {
        if (LOCAL_LOGV)
            Log.v(TAG, "starting work on " + image);

        if (image instanceof ImageManager.VideoObject) {
            if (LOCAL_LOGV)
                Log.v(TAG, "Uploading video");
            computeYouTubeAuthToken();
            return (new VideoUploadTask(image)).upload();
        } else {
            if (LOCAL_LOGV)
                Log.v(TAG, "Uploading photo");

            computeAuthToken();
            // handle photos
            if (mAlbums == null)
                mAlbums = getAlbums();

            String albumName = uploadAlbumName();
            if (mAlbums == null || !mAlbums.containsKey(albumName)) {
                Album a = createAlbum(albumName, uploadAlbumName());
                if (a == null) {
                    return false;
                }
                if (LOCAL_LOGV)
                    Log.v(TAG, "made new album: " + a.getAlbumName() + "; " + a.getAlbumId());
                mAlbums.put(a.getAlbumName(), a);
            }

            if (mAndroidUploadAlbumPhotos == null)
                mAndroidUploadAlbumPhotos = getAlbumContents(albumName);

            if (mAndroidUploadAlbumPhotos != null) {
                String previousUploadId = image.getPicasaId();
                if (previousUploadId != null) {
                    if (mAndroidUploadAlbumPhotos.contains(previousUploadId)) {
                        if (Config.LOGV)
                            Log.v(TAG, "already have id " + previousUploadId);
                        return true;
                    }
                }
            }
            Album album = mAlbums.get(albumName);
            return (new ImageUploadTask(image)).upload(album);
        }
    }

//    void broadcastError(int error) {
//        HashMap map = new HashMap();
//        map.put("error", new Integer(error));
//
//        Message send = Message.obtain();
//        send.what = EVENT_UPLOAD_ERROR;
//        send.setData(map);
//
//        if (mBroadcaster == null) {
//            mBroadcaster = new Broadcaster();
//        }
//        mBroadcaster.broadcast(send);
//    }

    class Album {
        String mAlbumName;

        String mAlbumId;

        public Album() {
        }

        public void setAlbumName(String albumName) {
            mAlbumName = albumName;
        }

        public void setAlbumId(String albumId) {
            mAlbumId = albumId;
        }

        public String getAlbumName() {
            return mAlbumName;
        }

        public String getAlbumId() {
            return mAlbumId;
        }
    }

    static private String stringFromResponse(HttpResponse response) {
        try {
            HttpEntity entity = response.getEntity();
            InputStream inputStream = entity.getContent();
            StringWriter s = new StringWriter();
            while (true) {
                int c = inputStream.read();
                if (c == -1)
                    break;
                s.write((char)c);
            }
            inputStream.close();
            String retval = s.toString();
            if (Config.LOGV)
                Log.v(TAG, "got resposne " + retval);
            return retval;
        } catch (Exception ex) {
            return null;
        }
    }

    abstract class UploadTask {
        IImage mImageObj;

        public UploadTask(IImage image) {
            mImageObj = image;
        }

        public class UploadResponse {
            private HttpResponse mStatus;
            private String mBody;

            public UploadResponse(HttpResponse status) {
                mStatus = status;
                mBody = stringFromResponse(status);
            }

            public int getStatus() {
                return mStatus.getStatusLine().getStatusCode();
            }

            public String getResponse() {
                return mBody;
            }
        }

        class StreamPart extends PartBase {
            InputStream mInputStream;
            long mLength;

            StreamPart(String name, InputStream inputStream, String contentType) {
                super(name,
                      contentType == null ? "application/octet-stream" : contentType,
                      "ISO-8859-1",
                      "binary"
                    );
                mInputStream = inputStream;
                try {
                    mLength = inputStream.available();
                } catch (IOException ex) {

                }
            }

            @Override
            protected long lengthOfData() throws IOException {
                return mLength;
            }

            @Override
            protected void sendData(OutputStream out) throws IOException {
                byte [] buffer = new byte[4096];
                while (true) {
                    int got = mInputStream.read(buffer);
                    if (got == -1)
                        break;
                    out.write(buffer, 0, got);
                }
                mInputStream.close();
            }

            @Override
            protected void sendDispositionHeader(OutputStream out) throws IOException {
            }

            @Override
            protected void sendContentTypeHeader(OutputStream out) throws IOException {
                String contentType = getContentType();
                if (contentType != null) {
                    out.write(CONTENT_TYPE_BYTES);
                    out.write(EncodingUtils.getAsciiBytes(contentType));
                    String charSet = getCharSet();
                    if (charSet != null) {
                        out.write(CHARSET_BYTES);
                        out.write(EncodingUtils.getAsciiBytes(charSet));
                    }
                }
            }
        }

        public class StringPartX extends StringPart {
            public StringPartX(String name, String value, String charset) {
                super(name, value, charset);
                setContentType("application/atom+xml");
            }

            @Override
            protected void sendDispositionHeader(OutputStream out) throws IOException {
            }

            @Override
            protected void sendContentTypeHeader(OutputStream out) throws IOException {
                String contentType = getContentType();
                if (contentType != null) {
                    out.write(CONTENT_TYPE_BYTES);
                    out.write(EncodingUtils.getAsciiBytes(contentType));
                    String charSet = getCharSet();
                    if (charSet != null) {
                        out.write(CHARSET_BYTES);
                        out.write(EncodingUtils.getAsciiBytes(charSet));
                    }
                }
            }
        }

        public class MultipartEntityX extends MultipartEntity {
            public MultipartEntityX(Part[] parts, HttpParams params) {
                super(parts, params);
            }

            @Override
            public Header getContentType() {
                StringBuilder buffer = new StringBuilder();
                buffer.append("multipart/related; boundary=");
                buffer.append(EncodingUtils.getAsciiString(getMultipartBoundary()));
                return new BasicHeader(HTTP.CONTENT_TYPE, buffer.toString());
            }

        }

        protected UploadResponse doUpload(String uploadUrl,
                              String mimeType,
                              String data,
                              IImage imageObj,
                              String authToken,
                              String title,
                              String filename,
                              boolean youTubeAuthenticate) {
            if (authToken == null)
                return null;

            FileInputStream inputStream = (FileInputStream)mImageObj.fullSizeImageData();
            try {
                HttpPost post = new HttpPost(uploadUrl);
                post.addHeader(new BasicHeader("Authorization", "GoogleLogin auth=" + authToken));
                if (youTubeAuthenticate) {
                    // TODO: remove hardwired key? - This is our official YouTube issued developer key to Android.
                    String youTubeDeveloperKey = "key=AI39si5Cr35CiD1IgDqD9Ua6N4dSbY-oibnLUPITmBN_rFW6qRz-hd8sTqNzRf1gzNwSYZbDuS31Txa4iKyjAV77507O4tq7JA";
                    post.addHeader("X-GData-Key", youTubeDeveloperKey);
                    post.addHeader("Slug", filename);
                }

                Part p1 = new StringPartX("param_name", data, null);
                Part p2 = new StreamPart("field_uploadfile", inputStream, mimeType);

                MultipartEntity mpe = new MultipartEntityX(new Part[] { p1, p2 }, post.getParams());
                post.setEntity(mpe);
                HttpResponse status = mClient.execute(post);
                if (LOCAL_LOGV) Log.v(TAG, "doUpload response is " + status.getStatusLine());
                return new UploadResponse(status);
            } catch (java.io.IOException ex) {
                if (LOCAL_LOGV) Log.v(TAG, "IOException in doUpload", ex);
                return null;
            }
        }

        class ResponseHandler implements ElementListener {
            private static final String ATOM_NAMESPACE
                    = "http://www.w3.org/2005/Atom";
            private static final String PICASSA_NAMESPACE
                    = "http://schemas.google.com/photos/2007";

            private ContentHandler mHandler = null;
            private String mId = null;

            public ResponseHandler() {
                RootElement root = new RootElement(ATOM_NAMESPACE, "entry");
                Element entry = root;
                entry.setElementListener(this);

                entry.getChild(PICASSA_NAMESPACE, "id")
                .setEndTextElementListener(new EndTextElementListener() {
                    public void end(String body) {
                        mId = body;
                    }
                });

                mHandler = root.getContentHandler();
            }

            public void start(Attributes attributes) {
            }

            public void end() {
            }

            ContentHandler getContentHandler() {
                return mHandler;
            }

            public String getId() {
                return mId;
            }
        }
    }

    private class VideoUploadTask extends UploadTask {
        public VideoUploadTask(IImage image) {
            super(image);
        }
        protected String getYouTubeBaseUrl() {
            return "http://gdata.youtube.com";
        }

        public boolean upload() {
            String uploadUrl = "http://uploads.gdata.youtube.com"
                               + "/feeds/users/"
                               + mYouTubeUsername
                               + "/uploads?client=ytapi-google-android";

            String title = mImageObj.getTitle();
            String isPrivate = "";
            String keywords = "";
            String category = "";
            if (mImageObj instanceof ImageManager.VideoObject) {
                ImageManager.VideoObject video = (ImageManager.VideoObject)mImageObj;
                if (mImageObj.getIsPrivate()) {
                    isPrivate = "<yt:private/>";
                }
                keywords = video.getTags();
                if (keywords == null || keywords.trim().length() == 0) {
                    // there must be a keyword or YouTube will reject the video
                    keywords = getResources().getString(R.string.upload_default_tags_text);
                }
                // TODO: use the real category when we have the category spinner in details
//                category = video.getCategory();
                category = "";
                if (category == null || category.trim().length() == 0) {
                    // there must be a description or YouTube will get an internal error and return 500
                    category = getResources().getString(R.string.upload_default_category_text);
                }
            }
            String description = mImageObj.getDescription();
            if (description == null || description.trim().length() == 0) {
                // there must be a description or YouTube will get an internal error and return 500
                description = getResources().getString(R.string.upload_default_description_text);
            }
            String data = "<?xml version='1.0'?>\n"
                        + "<entry xmlns='http://www.w3.org/2005/Atom'\n"
                        + "  xmlns:media='http://search.yahoo.com/mrss/'\n"
                        + "  xmlns:yt='http://gdata.youtube.com/schemas/2007'>\n"
                        + "  <media:group>\n"
                        + "    <media:title type='plain'>" + title + "</media:title>\n"   // TODO: need user entered title
                        + "    <media:description type='plain'>" + description + "</media:description>\n"
                        +      isPrivate
                        + "    <media:category scheme='http://gdata.youtube.com/schemas/2007/categories.cat'>\n"
                        +         category
                        + "    </media:category>\n"
                        + "    <media:keywords>" + keywords + "</media:keywords>\n"
                        + "  </media:group>\n"
                        + "</entry>";

            if (LOCAL_LOGV) Log.v("youtube", "uploadUrl: " + uploadUrl);
            if (LOCAL_LOGV) Log.v("youtube", "GData: " + data);

            UploadResponse result = doUpload(uploadUrl,
                    "video/3gpp2",
                    data,
                    null,
                    mYouTubeAuthToken,
                    title,
                    mImageObj.fullSizeImageUri().getLastPathSegment(),
                    true);

            boolean success = false;
            if (result != null) {
                switch (result.getStatus()) {
                case 401:
                    if (result.getResponse().contains("Token expired")) {
                        // When we tried to upload a video to YouTube, the youtube server told us
                        // our auth token was expired. Get a new one and try again.
                        try {
                            mGls.invalidateAuthToken(mYouTubeAuthToken);
                        } catch (GoogleLoginServiceNotFoundException e) {
                            Log.e(TAG, "Could not invalidate youtube auth token", e);
                        }
                        mYouTubeAuthToken = null;   // Forces computeYouTubeAuthToken to get a new token.
                        computeYouTubeAuthToken();
                    }
                    break;

                case 200:
                case 201:
                case 202:
                case 203:
                case 204:
                case 205:
                case 206:
                    success = true;
                    break;

                }
            }
            return success;
        }
    }

    private class ImageUploadTask extends UploadTask {
        public ImageUploadTask(IImage image) {
            super(image);
        }

        public boolean upload(Album album) {
            String uploadUrl = getServiceBaseUrl()
                               + mPicasaUsername
                               + "/album/"
                               + album.getAlbumId();

            String name = mImageObj.getTitle();
            String description = mImageObj.getDescription();
            String data = "<entry xmlns='http://www.w3.org/2005/Atom' xmlns:georss='http://www.georss.org/georss' xmlns:gml='http://www.opengis.net/gml'><title>"
                    + name
                    + "</title>"
                    + "<summary>"
                    + (description != null ? description : "")
                    + "</summary>"
                    + getLatLongString(mImageObj)
                    + "<category scheme='http://schemas.google.com/g/2005#kind' term='http://schemas.google.com/photos/2007#photo'/></entry>\n";

            if (LOCAL_LOGV)
                Log.v(TAG, "xml for image is " + data);
            UploadResponse response = doUpload(uploadUrl,
                            "image/jpeg",
                            data,
                            mImageObj,
                            mPicasaAuthToken,
                            name,
                            name,
                            false);

            if (response != null) {
                int status = response.getStatus();
                if (status == HttpStatus.SC_UNAUTHORIZED ||
                    status == HttpStatus.SC_FORBIDDEN ||
                    status == HttpStatus.SC_INTERNAL_SERVER_ERROR) {
                    try {
                        mGls.invalidateAuthToken(mPicasaAuthToken);
                    } catch (GoogleLoginServiceNotFoundException e) {
                        Log.e(TAG, "Could not invalidate picasa auth token", e);
                    }
                    mPicasaAuthToken = null;
                } else {
                    ResponseHandler h = new ResponseHandler();
                    try {
                        Xml.parse(response.getResponse(), h.getContentHandler());
                        String id = h.getId();
                        if (id != null && mImageObj != null) {
                            mImageObj.setPicasaId(id);
                            mAndroidUploadAlbumPhotos.add(id);
                            return true;
                        }
                    } catch (org.xml.sax.SAXException ex) {
                        Log.e(TAG, "SAXException in doUpload " + ex.toString());
                    }
                }
            }
            return false;
        }
    }

    private Album createAlbum(String name, String summary) {
        String authToken = mPicasaAuthToken;
        if (authToken == null)
            return null;

        try {
            String url = getServiceBaseUrl() + mPicasaUsername;
            HttpPost post = new HttpPost(url);
            String entryString = "<entry xmlns='http://www.w3.org/2005/Atom' xmlns:media='http://search.yahoo.com/mrss/' xmlns:gphoto='http://schemas.google.com/photos/2007'>"
                + "<title type='text'>"
                + name
                + "</title>"
                + "<summary>"
                + summary
                + "</summary>"
                + "<gphoto:access>private</gphoto:access>"
                + "<gphoto:commentingEnabled>true</gphoto:commentingEnabled>"
                + "<gphoto:timestamp>"
                + String.valueOf(System.currentTimeMillis())
                + "</gphoto:timestamp>"
                + "<category scheme=\"http://schemas.google.com/g/2005#kind\" term=\"http://schemas.google.com/photos/2007#album\"/>"
                + "</entry>\n";

            StringEntity entity = new StringEntity(entryString);
            entity.setContentType(new BasicHeader("Content-Type", "application/atom+xml"));
            post.setEntity(entity);
            post.addHeader(new BasicHeader("Authorization", "GoogleLogin auth=" + authToken));
            HttpResponse status = mClient.execute(post);
            if (LOCAL_LOGV)
                Log.v(TAG, "status is " + status.getStatusLine());
            if (status.getStatusLine().getStatusCode() < 200 || status.getStatusLine().getStatusCode() >= 300) {
                return null;
            }
            Album album = new Album();
            Xml.parse(stringFromResponse(status), new PicasaAlbumHandler(album).getContentHandler());
            return album;
        } catch (java.io.UnsupportedEncodingException ex) {
            Log.e(TAG, "gak, UnsupportedEncodingException " + ex.toString());
        } catch (java.io.IOException ex) {
            Log.e(TAG, "IOException " + ex.toString());
        } catch (org.xml.sax.SAXException ex) {
            Log.e(TAG, "XmlPullParserException " + ex.toString());
        }
        return null;
    }

    public static String streamToString(InputStream stream, int maxChars, boolean reset)
            throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream), 8192);
        StringBuilder sb = new StringBuilder();
        String line = null;

        while ((line = reader.readLine()) != null
                && (maxChars == -1 || sb.length() < maxChars)) {
            sb.append(line);
        }
        reader.close();
        if (reset) stream.reset();
        return sb.toString();
    }

    InputStream get(String url) {
        try {
            if (LOCAL_LOGV) Log.v(TAG, "url is  " + url);

            for (int i = 0; i < 2; ++i) {
                HttpGet get = new HttpGet(url);
                get.setHeader(new BasicHeader("Authorization",
                                              "GoogleLogin auth=" + mPicasaAuthToken));

                HttpResponse response = mClient.execute(get);
                if (LOCAL_LOGV) Log.v(TAG, "response is " + response.getStatusLine());
                switch (response.getStatusLine().getStatusCode()) {
                    case HttpStatus.SC_UNAUTHORIZED:
                    case HttpStatus.SC_FORBIDDEN:
                    case HttpStatus.SC_INTERNAL_SERVER_ERROR:   // http://b/1151576
                        try {
                            mGls.invalidateAuthToken(mPicasaAuthToken);
                        } catch (GoogleLoginServiceNotFoundException e) {
                            Log.e(TAG, "Could not invalidate picasa auth token", e);
                        }
                        mPicasaAuthToken = null;
                        computeAuthToken();
                        if (mPicasaAuthToken != null) {
                            // retry fetch after getting new token
                            continue;
                        }
                        break;
                }

                InputStream inputStream = response.getEntity().getContent();
                return inputStream;
            }
            return null;
        } catch (java.io.IOException ex) {
            Log.e(TAG, "IOException");
        }
        return null;
    }

    private HashMap<String, Album> getAlbums() {
        if (LOCAL_LOGV)
            Log.v(TAG, "getAlbums");

        PicasaAlbumHandler h = new PicasaAlbumHandler();
        try {
            String url = getServiceBaseUrl() + mPicasaUsername + "?kind=album";
            InputStream inputStream = get(url);
            if (inputStream == null) {
                if (Config.LOGV)
                    Log.v(TAG, "can't get " + url + "; bail from getAlbums()");
                mPicasaAuthToken = null;
                return null;
            }

            Xml.parse(inputStream, Xml.findEncodingByName("UTF-8"), h.getContentHandler());
            if (LOCAL_LOGV)
                Log.v(TAG, "done getting albums");
            inputStream.close();
        } catch (IOException e) {
            Log.e(TAG, "got exception " + e.toString());
            e.printStackTrace();
        } catch (SAXException e) {
            Log.e(TAG, "got exception " + e.toString());
            e.printStackTrace();
        }
        if (LOCAL_LOGV) {
            java.util.Iterator it = h.getAlbums().keySet().iterator();
            while (it.hasNext()) {
                if (Config.LOGV)
                    Log.v(TAG, "album: " + (String) it.next());
            }
        }
        return h.getAlbums();
    }

    ArrayList<String> getAlbumContents(String albumName) {
        String url = getServiceBaseUrl() + mPicasaUsername + "/album/" + albumName + "?kind=photo&max-results=10000";
        try {
            InputStream inputStream = get(url);
            if (inputStream == null)
                return null;

            AlbumContentsHandler ah = new AlbumContentsHandler();
            Xml.parse(inputStream, Xml.findEncodingByName("UTF-8"), ah.getContentHandler());
            ArrayList<String> photos = ah.getPhotos();
            inputStream.close();
            return photos;
        } catch (IOException e) {
            Log.e(TAG, "got IOException " + e.toString());
            e.printStackTrace();
        } catch (SAXException e) {
            Log.e(TAG, "got SAXException " + e.toString());
            e.printStackTrace();
        }
        return null;
    }

    class AlbumContentsHandler implements ElementListener {
        private static final String ATOM_NAMESPACE
                = "http://www.w3.org/2005/Atom";
        private static final String PICASA_NAMESPACE
                = "http://schemas.google.com/photos/2007";

        private ContentHandler mHandler = null;
        private ArrayList<String> mPhotos = new ArrayList<String>();

        public AlbumContentsHandler() {
            RootElement root = new RootElement(ATOM_NAMESPACE, "feed");
            Element entry = root.getChild(ATOM_NAMESPACE, "entry");

            entry.setElementListener(this);

            entry.getChild(PICASA_NAMESPACE, "id")
            .setEndTextElementListener(new EndTextElementListener() {
                public void end(String body) {
                    mPhotos.add(body);
                }
            });

            mHandler = root.getContentHandler();
        }

        public void start(Attributes attributes) {
        }

        public void end() {
        }

        ContentHandler getContentHandler() {
            return mHandler;
        }

        public ArrayList<String> getPhotos() {
            return mPhotos;
        }
    }

    private String getServiceBaseUrl() {
        return "http://picasaweb.google.com/data/feed/api/user/";
    }


    class PicasaAlbumHandler implements ElementListener {
        private Album mAlbum;
        private HashMap<String, Album> mAlbums = new HashMap<String, Album>();
        private boolean mJustOne;
        private static final String ATOM_NAMESPACE
                = "http://www.w3.org/2005/Atom";
        private static final String PICASSA_NAMESPACE
                = "http://schemas.google.com/photos/2007";
        private ContentHandler handler = null;

        public PicasaAlbumHandler() {
            mJustOne = false;
            init();
        }

        public HashMap<String, Album> getAlbums() {
            return mAlbums;
        }

        public PicasaAlbumHandler(Album album) {
            mJustOne = true;
            mAlbum = album;
            init();
        }

        private void init() {
            Element entry;
            RootElement root;
            if (mJustOne) {
                root = new RootElement(ATOM_NAMESPACE, "entry");
                entry = root;
            } else {
                root = new RootElement(ATOM_NAMESPACE, "feed");
                entry = root.getChild(ATOM_NAMESPACE, "entry");
            }
            entry.setElementListener(this);

            entry.getChild(ATOM_NAMESPACE, "title")
                .setEndTextElementListener(new EndTextElementListener() {
                    public void end(String body) {
                        mAlbum.setAlbumName(body);
                    }
                });

            entry.getChild(PICASSA_NAMESPACE, "name")
                .setEndTextElementListener(new EndTextElementListener() {
                    public void end(String body) {
                        mAlbum.setAlbumId(body);
                    }
                });

            this.handler = root.getContentHandler();
        }

        public void start(Attributes attributes) {
            if (!mJustOne) {
                mAlbum = new Album();
            }
        }

        public void end() {
            if (!mJustOne) {
                mAlbums.put(mAlbum.getAlbumName(), mAlbum);
                mAlbum = null;
            }
        }

        ContentHandler getContentHandler() {
            return handler;
        }
    }
}
