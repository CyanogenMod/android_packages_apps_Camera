/*
 * Copyright (C) 2008 The Android Open Source Project
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

import java.io.Closeable;
import java.util.ArrayList;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.StatFs;
import android.provider.MediaStore;
import android.provider.MediaStore.Images;
import android.util.Config;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.MenuItem.OnMenuItemClickListener;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.camera.ImageManager.IImage;

public class MenuHelper {
    static private final String TAG = "MenuHelper";

    static public final int GENERIC_ITEM      = 1;
    static public final int IMAGE_SAVING_ITEM = 2;
    static public final int VIDEO_SAVING_ITEM = 3;
    static public final int IMAGE_MODE_ITEM   = 4;
    static public final int VIDEO_MODE_ITEM   = 5;
    static public final int MENU_ITEM_MAX     = 5;

    static public final int INCLUDE_ALL           = 0xFFFFFFFF;
    static public final int INCLUDE_VIEWPLAY_MENU = (1 << 0);
    static public final int INCLUDE_SHARE_MENU    = (1 << 1);
    static public final int INCLUDE_SET_MENU      = (1 << 2);
    static public final int INCLUDE_CROP_MENU     = (1 << 3);
    static public final int INCLUDE_DELETE_MENU   = (1 << 4);
    static public final int INCLUDE_ROTATE_MENU   = (1 << 5);
    static public final int INCLUDE_DETAILS_MENU  = (1 << 6);

    static public final int MENU_SWITCH_CAMERA_MODE = 0;
    static public final int MENU_CAPTURE_PICTURE = 1;
    static public final int MENU_CAPTURE_VIDEO = 2;
    static public final int MENU_IMAGE_SHARE = 10;
    static public final int MENU_IMAGE_SET = 14;
    static public final int MENU_IMAGE_SET_WALLPAPER = 15;
    static public final int MENU_IMAGE_SET_CONTACT = 16;
    static public final int MENU_IMAGE_SET_MYFAVE = 17;
    static public final int MENU_IMAGE_CROP = 18;
    static public final int MENU_IMAGE_ROTATE = 19;
    static public final int MENU_IMAGE_ROTATE_LEFT = 20;
    static public final int MENU_IMAGE_ROTATE_RIGHT = 21;
    static public final int MENU_IMAGE_TOSS = 22;
    static public final int MENU_VIDEO_PLAY = 23;
    static public final int MENU_VIDEO_SHARE = 24;
    static public final int MENU_VIDEO_TOSS = 27;

    static private final long SHARE_FILE_LENGTH_LIMIT = 3L * 1024L * 1024L;

    public static final int NO_STORAGE_ERROR = -1;
    public static final int CANNOT_STAT_ERROR = -2;

    /** Activity result code used to report crop results.
     */
    public static final int RESULT_COMMON_MENU_CROP = 490;

    public interface MenuItemsResult {
        public void gettingReadyToOpen(Menu menu, ImageManager.IImage image);
        public void aboutToCall(MenuItem item, ImageManager.IImage image);
    }

    public interface MenuInvoker {
        public void run(MenuCallback r);
    }

    public interface MenuCallback {
        public void run(Uri uri, ImageManager.IImage image);
    }

    private static void closeSilently(Closeable target) {
        try {
            if (target != null) target.close();
        } catch (Throwable t) {
            // ignore all exceptions, that's what silently means
        }
    }

    public static long getImageFileSize(ImageManager.IImage image) {
        java.io.InputStream data = image.fullSizeImageData();
        if (data == null) return -1;
        try {
            return data.available();
        } catch (java.io.IOException ex) {
            return -1;
        } finally {
            closeSilently(data);
        }
    }

    // This is a hack before we find a solution to pass a permission to other
    // applications. See bug #1735149.
    // Checks if the URI starts with "content://mms".
    public static boolean isMMSUri(Uri uri) {
        return (uri != null) &&
               uri.getScheme().equals("content") &&
               uri.getAuthority().equals("mms");
    }
    
    public static void enableShareMenuItem(Menu menu, boolean enabled) {
        MenuItem item = menu.findItem(MENU_IMAGE_SHARE);
        if (item != null) {
            item.setVisible(enabled);
            item.setEnabled(enabled);
        }
    }

    static MenuItemsResult addImageMenuItems(
            Menu menu,
            int inclusions,
            final boolean isImage,
            final Activity activity,
            final Handler handler,
            final Runnable onDelete,
            final MenuInvoker onInvoke) {
        final ArrayList<MenuItem> requiresWriteAccessItems = new ArrayList<MenuItem>();
        final ArrayList<MenuItem> requiresNoDrmAccessItems = new ArrayList<MenuItem>();

        if (isImage && ((inclusions & INCLUDE_ROTATE_MENU) != 0)) {
            SubMenu rotateSubmenu = menu.addSubMenu(IMAGE_SAVING_ITEM, MENU_IMAGE_ROTATE,
                    40, R.string.rotate).setIcon(android.R.drawable.ic_menu_rotate);
            // Don't show the rotate submenu if the item at hand is read only
            // since the items within the submenu won't be shown anyway.  This is
            // really a framework bug in that it shouldn't show the submenu if
            // the submenu has no visible items.
            requiresWriteAccessItems.add(rotateSubmenu.getItem());
            if (rotateSubmenu != null) {
                requiresWriteAccessItems.add(rotateSubmenu.add(0, MENU_IMAGE_ROTATE_LEFT, 50, R.string.rotate_left).setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                    public boolean onMenuItemClick(MenuItem item) {
                        onInvoke.run(new MenuCallback() {
                            public void run(Uri u, ImageManager.IImage image) {
                                if (image == null || image.isReadonly())
                                    return;
                                image.rotateImageBy(-90);
                            }
                        });
                        return true;
                    }
                }).setAlphabeticShortcut('l'));
                requiresWriteAccessItems.add(rotateSubmenu.add(0, MENU_IMAGE_ROTATE_RIGHT, 60, R.string.rotate_right).setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                    public boolean onMenuItemClick(MenuItem item) {
                        onInvoke.run(new MenuCallback() {
                            public void run(Uri u, ImageManager.IImage image) {
                                if (image == null || image.isReadonly())
                                    return;

                                image.rotateImageBy(90);
                            }
                        });
                        return true;
                    }
                }).setAlphabeticShortcut('r'));
            }
        }

        if (isImage && ((inclusions & INCLUDE_CROP_MENU) != 0)) {
            MenuItem autoCrop = menu.add(IMAGE_SAVING_ITEM, MENU_IMAGE_CROP, 73,
                    R.string.camera_crop).setOnMenuItemClickListener(
                            new MenuItem.OnMenuItemClickListener() {
                public boolean onMenuItemClick(MenuItem item) {
                    onInvoke.run(new MenuCallback() {
                        public void run(Uri u, ImageManager.IImage image) {
                            if (u == null)
                                return;

                            Intent cropIntent = new Intent();
                            cropIntent.setClass(activity, CropImage.class);
                            cropIntent.setData(u);
                            activity.startActivityForResult(cropIntent, RESULT_COMMON_MENU_CROP);
                        }
                    });
                    return true;
                }
            });
            autoCrop.setIcon(android.R.drawable.ic_menu_crop);
            requiresWriteAccessItems.add(autoCrop);
        }

        if (isImage && ((inclusions & INCLUDE_SET_MENU) != 0)) {
            MenuItem setMenu = menu.add(IMAGE_SAVING_ITEM, MENU_IMAGE_SET, 75, R.string.camera_set);
            setMenu.setIcon(android.R.drawable.ic_menu_set_as);

            setMenu.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                public boolean onMenuItemClick(MenuItem item) {
                    onInvoke.run(new MenuCallback() {
                        public void run(Uri u, ImageManager.IImage image) {
                            if (u == null || image == null)
                                return;

                            if (Config.LOGV)
                                Log.v(TAG, "in callback u is " + u + "; mime type is " + image.getMimeType());
                            Intent intent = new Intent(Intent.ACTION_ATTACH_DATA);
                            intent.setDataAndType(u, image.getMimeType());
                            intent.putExtra("mimeType", image.getMimeType());
                            activity.startActivity(Intent.createChooser(intent, activity.getText(R.string.setImage)));
                        }
                    });
                    return true;
                }
            });
        }

        if ((inclusions & INCLUDE_SHARE_MENU) != 0) {
            if (Config.LOGV)
                Log.v(TAG, ">>>>> add share");
            MenuItem item1 = menu.add(IMAGE_SAVING_ITEM, MENU_IMAGE_SHARE, 10,
                    R.string.camera_share).setOnMenuItemClickListener(
                    new MenuItem.OnMenuItemClickListener() {
                public boolean onMenuItemClick(MenuItem item) {
                    onInvoke.run(new MenuCallback() {
                        public void run(Uri u, ImageManager.IImage image) {
                            if (image == null) return;
                            if (!isImage && getImageFileSize(image) > SHARE_FILE_LENGTH_LIMIT ) {
                                Toast.makeText(activity,
                                        R.string.too_large_to_attach, Toast.LENGTH_LONG).show();
                                return;
                            }

                            Intent intent = new Intent();
                            intent.setAction(Intent.ACTION_SEND);
                            String mimeType = image.getMimeType();
                            intent.setType(mimeType);
                            intent.putExtra(Intent.EXTRA_STREAM, u);
                            boolean isImage = ImageManager.isImageMimeType(mimeType);
                            try {
                                activity.startActivity(Intent.createChooser(intent,
                                        activity.getText(
                                                isImage ? R.string.sendImage : R.string.sendVideo)));
                            } catch (android.content.ActivityNotFoundException ex) {
                                Toast.makeText(activity,
                                        isImage ? R.string.no_way_to_share_image
                                                : R.string.no_way_to_share_video,
                                                Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                    return true;
                }
            });
            item1.setIcon(android.R.drawable.ic_menu_share);
            MenuItem item = item1;
            requiresNoDrmAccessItems.add(item);
        }

        if ((inclusions & INCLUDE_DELETE_MENU) != 0) {
            MenuItem deleteItem = menu.add(IMAGE_SAVING_ITEM, MENU_IMAGE_TOSS, 70, R.string.camera_toss);
            requiresWriteAccessItems.add(deleteItem);
            deleteItem.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                public boolean onMenuItemClick(MenuItem item) {
                    deleteImpl(activity, onDelete, isImage);
                    return true;
                }
            })
            .setAlphabeticShortcut('d')
            .setIcon(android.R.drawable.ic_menu_delete);
        }

        if ((inclusions & INCLUDE_DETAILS_MENU) != 0) {
            MenuItem detailsMenu = menu.add(0, 0, 80, R.string.details).setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                public boolean onMenuItemClick(MenuItem item) {
                    onInvoke.run(new MenuCallback() {
                        public void run(Uri u, ImageManager.IImage image) {
                            if (image == null)
                                return;

                            AlertDialog.Builder builder = new AlertDialog.Builder(activity);

                            final View d = View.inflate(activity, R.layout.detailsview, null);

                            ImageView imageView = (ImageView) d.findViewById(R.id.details_thumbnail_image);
                            imageView.setImageBitmap(image.miniThumbBitmap());

                            TextView textView = (TextView) d.findViewById(R.id.details_image_title);
                            textView.setText(image.getDisplayName());

                            long length = getImageFileSize(image);
                            String lengthString = lengthString = length < 0 ? ""
                                    : android.text.format.Formatter.formatFileSize(activity, length);
                            ((TextView)d.findViewById(R.id.details_file_size_value))
                                .setText(lengthString);

                            int dimensionWidth = 0;
                            int dimensionHeight = 0;
                            if (isImage) {
                                dimensionWidth = image.getWidth();
                                dimensionHeight = image.getHeight();
                                d.findViewById(R.id.details_duration_row).setVisibility(View.GONE);
                                d.findViewById(R.id.details_frame_rate_row).setVisibility(View.GONE);
                                d.findViewById(R.id.details_bit_rate_row).setVisibility(View.GONE);
                                d.findViewById(R.id.details_format_row).setVisibility(View.GONE);
                                d.findViewById(R.id.details_codec_row).setVisibility(View.GONE);
                            } else {
                                MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                                try {
                                    retriever.setMode(MediaMetadataRetriever.MODE_GET_METADATA_ONLY);
                                    retriever.setDataSource(image.getDataPath());
                                    try {
                                        dimensionWidth = Integer.parseInt(
                                                retriever.extractMetadata(
                                                MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
                                        dimensionHeight = Integer.parseInt(
                                                retriever.extractMetadata(
                                                MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
                                    } catch (NumberFormatException e) {
                                        dimensionWidth = 0;
                                        dimensionHeight = 0;
                                    }

                                    try {
                                        int durationMs = Integer.parseInt(retriever.extractMetadata(
                                                MediaMetadataRetriever.METADATA_KEY_DURATION));
                                        String durationValue = formatDuration(
                                                activity, durationMs);
                                        ((TextView)d.findViewById(R.id.details_duration_value))
                                            .setText(durationValue);
                                    } catch (NumberFormatException e) {
                                        d.findViewById(R.id.details_frame_rate_row)
                                        .setVisibility(View.GONE);
                                    }

                                    try {
                                        String frame_rate = String.format(
                                                activity.getString(R.string.details_fps),
                                                Integer.parseInt(
                                                        retriever.extractMetadata(
                                                                MediaMetadataRetriever.METADATA_KEY_FRAME_RATE)));
                                        ((TextView)d.findViewById(R.id.details_frame_rate_value))
                                            .setText(frame_rate);
                                    } catch (NumberFormatException e) {
                                        d.findViewById(R.id.details_frame_rate_row)
                                        .setVisibility(View.GONE);
                                    }

                                    try {
                                        long bitRate = Long.parseLong(retriever.extractMetadata(
                                                MediaMetadataRetriever.METADATA_KEY_BIT_RATE));
                                        String bps;
                                        if (bitRate < 1000000) {
                                            bps = String.format(
                                                    activity.getString(R.string.details_kbps),
                                                    bitRate / 1000);
                                        } else {
                                            bps = String.format(
                                                    activity.getString(R.string.details_mbps),
                                                    ((double) bitRate) / 1000000.0);
                                        }
                                        ((TextView)d.findViewById(R.id.details_bit_rate_value))
                                                .setText(bps);
                                    } catch (NumberFormatException e) {
                                        d.findViewById(R.id.details_bit_rate_row)
                                                .setVisibility(View.GONE);
                                    }

                                    String format = retriever.extractMetadata(
                                            MediaMetadataRetriever.METADATA_KEY_VIDEO_FORMAT);
                                    ((TextView)d.findViewById(R.id.details_format_value))
                                        .setText(format);

                                    String codec = retriever.extractMetadata(
                                                MediaMetadataRetriever.METADATA_KEY_CODEC);

                                    if (codec == null) {
                                        d.findViewById(R.id.details_codec_row).
                                            setVisibility(View.GONE);
                                    } else {
                                        ((TextView)d.findViewById(R.id.details_codec_value))
                                            .setText(codec);
                                    }
                                } catch(RuntimeException ex) {
                                    // Assume this is a corrupt video file.
                                } finally {
                                    try {
                                        retriever.release();
                                    } catch (RuntimeException ex) {
                                        // Ignore failures while cleaning up.
                                    }
                                }
                            }

                            String dimensionsString = String.format(
                                    activity.getString(R.string.details_dimension_x),
                                    dimensionWidth, dimensionHeight);
                            ((TextView)d.findViewById(R.id.details_resolution_value))
                                .setText(dimensionsString);

                            String dateString = "";
                            long dateTaken = image.getDateTaken();
                            if (dateTaken != 0) {
                                java.util.Date date = new java.util.Date(image.getDateTaken());
                                java.text.SimpleDateFormat dateFormat = new java.text.SimpleDateFormat();
                                dateString = dateFormat.format(date);

                                ((TextView)d.findViewById(R.id.details_date_taken_value))
                                    .setText(dateString);
                            } else {
                                d.findViewById(R.id.details_date_taken_row)
                                    .setVisibility(View.GONE);
                            }

                            builder.setNeutralButton(R.string.details_ok,
                                    new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int which) {
                                        dialog.dismiss();
                                    }
                                });

                            builder.setIcon(android.R.drawable.ic_dialog_info)
                                .setTitle(R.string.details_panel_title)
                                .setView(d)
                                .show();

                        }
                    });
                    return true;
                }
            });
            detailsMenu.setIcon(R.drawable.ic_menu_view_details);
        }

        if ((!isImage) && ((inclusions & INCLUDE_VIEWPLAY_MENU) != 0)) {
            menu.add(VIDEO_SAVING_ITEM, MENU_VIDEO_PLAY, 0, R.string.video_play)
                .setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                public boolean onMenuItemClick(MenuItem item) {
                    onInvoke.run(new MenuCallback() {
                        public void run(Uri uri, IImage image) {
                            if (image != null) {
                                Intent intent = new Intent(Intent.ACTION_VIEW,
                                        image.fullSizeImageUri());
                                activity.startActivity(intent);
                            }
                        }});
                    return true;
                }
            });
        }


        return new MenuItemsResult() {
            public void gettingReadyToOpen(Menu menu, ImageManager.IImage image) {
                // protect against null here.  this isn't strictly speaking required
                // but if a client app isn't handling sdcard removal properly it
                // could happen
                if (image == null) {
                    return;
                }
                boolean readOnly = image.isReadonly();
                boolean isDrm = image.isDrm();
                if (Config.LOGV)
                    Log.v(TAG, "readOnly: " + readOnly + "; drm: " + isDrm);
                for (MenuItem item: requiresWriteAccessItems) {
                    if (Config.LOGV)
                        Log.v(TAG, "item is " + item.toString());
                      item.setVisible(!readOnly);
                      item.setEnabled(!readOnly);
                }
                for (MenuItem item: requiresNoDrmAccessItems) {
                    if (Config.LOGV)
                        Log.v(TAG, "item is " + item.toString());
                      item.setVisible(!isDrm);
                      item.setEnabled(!isDrm);
                }
            }
            public void aboutToCall(MenuItem menu, ImageManager.IImage image) {
            }
        };
    }

    static void deletePhoto(Activity activity, Runnable onDelete) {
        deleteImpl(activity, onDelete, true);
    }

    static void deleteVideo(Activity activity, Runnable onDelete) {
        deleteImpl(activity, onDelete, false);
    }

    static void deleteImage(Activity activity, Runnable onDelete, IImage image) {
        if (image != null) {
            deleteImpl(activity, onDelete, ImageManager.isImage(image));
        }
    }

    private static void deleteImpl(Activity activity, final Runnable onDelete, boolean isPhoto) {
        boolean confirm = android.preference.PreferenceManager.getDefaultSharedPreferences(activity).getBoolean("pref_gallery_confirm_delete_key", true);
        if (!confirm) {
            if (onDelete != null)
                onDelete.run();
        } else {
            displayDeleteDialog(activity, onDelete, isPhoto);
        }
    }

    public static void displayDeleteDialog(Activity activity,
            final Runnable onDelete, boolean isPhoto) {
        android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(activity);
        b.setIcon(android.R.drawable.ic_dialog_alert);
        b.setTitle(R.string.confirm_delete_title);
        b.setMessage(isPhoto? R.string.confirm_delete_message
                : R.string.confirm_delete_video_message);
        b.setPositiveButton(android.R.string.ok, new android.content.DialogInterface.OnClickListener() {
            public void onClick(android.content.DialogInterface v, int x) {
                if (onDelete != null)
                    onDelete.run();
            }
        });
        b.setNegativeButton(android.R.string.cancel, new android.content.DialogInterface.OnClickListener() {
            public void onClick(android.content.DialogInterface v, int x) {

            }
        });
        b.create().show();
    }

    static void addSwitchModeMenuItem(Menu menu, final Activity activity,
            final boolean switchToVideo) {
        int group = switchToVideo ? MenuHelper.IMAGE_MODE_ITEM : MenuHelper.VIDEO_MODE_ITEM;
        int labelId = switchToVideo ? R.string.switch_to_video_lable
                : R.string.switch_to_camera_lable;
        int iconId = switchToVideo ? R.drawable.ic_menu_camera_video_view
                : android.R.drawable.ic_menu_camera;
        MenuItem item = menu.add(group, MENU_SWITCH_CAMERA_MODE, 0,
                labelId).setOnMenuItemClickListener(
                        new OnMenuItemClickListener() {
            public boolean onMenuItemClick(MenuItem item) {
                String action = switchToVideo ? MediaStore.INTENT_ACTION_VIDEO_CAMERA
                        : MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA;
                Intent intent = new Intent(action);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                intent.addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT);
                activity.startActivity(intent);
                return true;
             }
        });
        item.setIcon(iconId);
    }

    static void gotoStillImageCapture(Activity activity) {
        Intent intent = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try {
            activity.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Log.e(TAG, "Could not start still image capture activity", e);
        }
    }

    static void gotoCameraImageGallery(Activity activity) {
        gotoGallery(activity, R.string.gallery_camera_bucket_name, ImageManager.INCLUDE_IMAGES);
    }

    static void gotoCameraVideoGallery(Activity activity) {
        gotoGallery(activity, R.string.gallery_camera_videos_bucket_name,
                ImageManager.INCLUDE_VIDEOS);
    }

    static private void gotoGallery(Activity activity, int windowTitleId, int mediaTypes) {
        Uri target = Images.Media.INTERNAL_CONTENT_URI.buildUpon().appendQueryParameter("bucketId",
                ImageManager.CAMERA_IMAGE_BUCKET_ID).build();
        Intent intent = new Intent(Intent.ACTION_VIEW, target);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra("windowTitle", activity.getString(windowTitleId));
        intent.putExtra("mediaTypes", mediaTypes);
        // Request unspecified so that we match the current camera orientation rather than
        // matching the "flip orientation" preference.
        // Disabled because people don't care for it. Also it's
        // not as compelling now that we have implemented have quick orientation flipping.
        // intent.putExtra(MediaStore.EXTRA_SCREEN_ORIENTATION,
        //        android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        try {
            activity.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Log.e(TAG, "Could not start gallery activity", e);
        }
    }

    static void addCapturePictureMenuItems(Menu menu, final Activity activity) {
        menu.add(0, MENU_CAPTURE_PICTURE, 1, R.string.capture_picture)
            .setOnMenuItemClickListener(
                 new MenuItem.OnMenuItemClickListener() {
                     public boolean onMenuItemClick(MenuItem item) {
                        Intent intent = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA);
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        try {
                               activity.startActivity(intent);
                        } catch (android.content.ActivityNotFoundException e) {
                            // Ignore exception
                        }
                return true;
            }
        })
        .setIcon(android.R.drawable.ic_menu_camera);
    }

    static void addCaptureVideoMenuItems(Menu menu, final Activity activity) {
        menu.add(0, MENU_CAPTURE_VIDEO, 2, R.string.capture_video)
            .setOnMenuItemClickListener(
                 new MenuItem.OnMenuItemClickListener() {
                     public boolean onMenuItemClick(MenuItem item) {
                         Intent intent = new Intent(MediaStore.INTENT_ACTION_VIDEO_CAMERA);
                         intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                         try {
                             activity.startActivity(intent);
                         } catch (android.content.ActivityNotFoundException e) {
                             // Ignore exception
                         }
                return true;
            }
        })
        .setIcon(R.drawable.ic_menu_camera_video_view);
    }

    static void addCaptureMenuItems(Menu menu, final Activity activity) {
        addCapturePictureMenuItems(menu, activity);
        addCaptureVideoMenuItems(menu, activity);
    }

    static MenuItem addFlipOrientation(Menu menu, final Activity activity, final SharedPreferences prefs) {
        // position 41 after rotate
        // D
        return menu
                .add(Menu.CATEGORY_SECONDARY, 304, 41, R.string.flip_orientation)
                .setOnMenuItemClickListener(
                        new MenuItem.OnMenuItemClickListener() {
            public boolean onMenuItemClick(MenuItem item) {
                // Check what our actual orientation is
                int current = activity.getResources().getConfiguration().orientation;
                int newOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
                if (current == Configuration.ORIENTATION_LANDSCAPE) {
                    newOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
                }
                SharedPreferences.Editor editor = prefs.edit();
                editor.putInt("nuorientation", newOrientation);
                editor.commit();
                requestOrientation(activity, prefs, true);
                return true;
            }
        })
        .setIcon(android.R.drawable.ic_menu_always_landscape_portrait);
    }

    static void requestOrientation(Activity activity, SharedPreferences prefs) {
        requestOrientation(activity, prefs, false);
    }

    static private void requestOrientation(Activity activity, SharedPreferences prefs,
            boolean ignoreIntentExtra) {
        // Disable orientation for now. If it is set to SCREEN_ORIENTATION_SENSOR,
        // a duplicated orientation will be observed.

        return;
    }

    static void setFlipOrientationEnabled(Activity activity, MenuItem flipItem) {
        int keyboard = activity.getResources().getConfiguration().hardKeyboardHidden;
        flipItem.setEnabled(keyboard != android.content.res.Configuration.HARDKEYBOARDHIDDEN_NO);
    }

    public static String formatDuration(final Activity activity, int durationMs) {
        int duration = durationMs / 1000;
        int h = duration / 3600;
        int m = (duration - h * 3600) / 60;
        int s = duration - (h * 3600 + m * 60);
        String durationValue;
        if (h == 0) {
            durationValue = String.format(
                    activity.getString(R.string.details_ms), m, s);
        } else {
            durationValue = String.format(
                    activity.getString(R.string.details_hms), h, m, s);
        }
        return durationValue;
    }

    public static void showStorageToast(Activity activity) {
      showStorageToast(activity, calculatePicturesRemaining());
    }

    public static void showStorageToast(Activity activity, int remaining) {
        String noStorageText = null;

        if (remaining == MenuHelper.NO_STORAGE_ERROR) {
            String state = Environment.getExternalStorageState();
            if (state == Environment.MEDIA_CHECKING) {
                noStorageText = activity.getString(R.string.preparing_sd);
            } else {
                noStorageText = activity.getString(R.string.no_storage);
            }
        } else if (remaining < 1) {
            noStorageText = activity.getString(R.string.not_enough_space);
        }

        if (noStorageText != null) {
            Toast.makeText(activity, noStorageText, 5000).show();
        }
    }

    public static int calculatePicturesRemaining() {
        try {
            if (!ImageManager.hasStorage()) {
                return NO_STORAGE_ERROR;
            } else {
                String storageDirectory = Environment.getExternalStorageDirectory().toString();
                StatFs stat = new StatFs(storageDirectory);
                float remaining = ((float)stat.getAvailableBlocks() * (float)stat.getBlockSize()) / 400000F;
                return (int)remaining;
            }
        } catch (Exception ex) {
            // if we can't stat the filesystem then we don't know how many
            // pictures are remaining.  it might be zero but just leave it
            // blank since we really don't know.
            return CANNOT_STAT_ERROR;
        }
    }
}

