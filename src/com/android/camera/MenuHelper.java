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

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.util.Config;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

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
    static public final int INCLUDE_DETAILS_MENU   = (1 << 5);
    
    static public final int MENU_IMAGE_SHARE = 10;
    static public final int MENU_IMAGE_SHARE_EMAIL = 11;
    static public final int MENU_IMAGE_SHARE_MMS = 12;
    static public final int MENU_IMAGE_SHARE_PICASA =13;
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
    static public final int MENU_VIDEO_SHARE_MMS = 25;
    static public final int MENU_VIDEO_SHARE_YOUTUBE = 26;
    static public final int MENU_VIDEO_TOSS = 27;
    static public final int MENU_IMAGE_SHARE_PICASA_ALL =28;
    
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

    static MenuItemsResult addImageMenuItems(
            Menu menu, 
            int inclusions,
            final Activity activity,
            final Handler handler,
            final Runnable onDelete,
            final MenuInvoker onInvoke) {
        final ArrayList<MenuItem> requiresWriteAccessItems = new ArrayList<MenuItem>(); 
        final ArrayList<MenuItem> requiresNoDrmAccessItems = new ArrayList<MenuItem>(); 
        if ((inclusions & INCLUDE_SHARE_MENU) != 0) {
            if (Config.LOGV)
                Log.v(TAG, ">>>>> add share");
            MenuItem item = menu.add(IMAGE_SAVING_ITEM, MENU_IMAGE_SHARE, 10,
                    R.string.camera_share).setOnMenuItemClickListener(
                    new MenuItem.OnMenuItemClickListener() {
                public boolean onMenuItemClick(MenuItem item) {
                    onInvoke.run(new MenuCallback() {
                        public void run(Uri u, ImageManager.IImage image) {
                            if (image == null)
                                return;
                            
                            Intent intent = new Intent();
                            intent.setAction(Intent.ACTION_SEND);
                            intent.setType(image.getMimeType());
                            intent.putExtra(Intent.EXTRA_STREAM, u);
                            try {
                                activity.startActivity(Intent.createChooser(intent,
                                        activity.getText(R.string.sendImage)));
                            } catch (android.content.ActivityNotFoundException ex) {
                                Toast.makeText(activity, R.string.no_way_to_share_image, Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                    return true;
                }
            });
            item.setIcon(android.R.drawable.ic_menu_share);
            requiresNoDrmAccessItems.add(item);
        }

        if ((inclusions & INCLUDE_ROTATE_MENU) != 0) {
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
        
        if ((inclusions & INCLUDE_DELETE_MENU) != 0) {
            MenuItem deleteItem = menu.add(IMAGE_SAVING_ITEM, MENU_IMAGE_TOSS, 70, R.string.camera_toss);
            requiresWriteAccessItems.add(deleteItem);
            deleteItem.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                public boolean onMenuItemClick(MenuItem item) {
                    deletePhoto(activity, onDelete);
                    return true;
                }
            })
            .setAlphabeticShortcut('d')
            .setIcon(android.R.drawable.ic_menu_delete);
        }
        
        if ((inclusions & INCLUDE_CROP_MENU) != 0) {
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
                            activity.startActivity(cropIntent);
                        }
                    });
                    return true;
                }
            });
            autoCrop.setIcon(android.R.drawable.ic_menu_crop);
            requiresWriteAccessItems.add(autoCrop);
        }
        
        if ((inclusions & INCLUDE_SET_MENU) != 0) {
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

                            java.io.InputStream data = image.fullSizeImageData();
                            String lengthString = "";
                            try {
                                long length = data.available();
                                lengthString = 
                                    android.content.Formatter.formatFileSize(activity, length);
                                data.close();
                            } catch (java.io.IOException ex) {

                            } finally {
                            }
                            ((TextView)d.findViewById(R.id.details_attrname_1)).setText(R.string.details_file_size);
                            ((TextView)d.findViewById(R.id.details_attrvalu_1)).setText(lengthString);

                            String dimensionsString = String.valueOf(image.getWidth() + " X " + image.getHeight());
                            ((TextView)d.findViewById(R.id.details_attrname_2)).setText(R.string.details_image_resolution);
                            ((TextView)d.findViewById(R.id.details_attrvalu_2)).setText(dimensionsString);

                            String dateString = "";
                            long dateTaken = image.getDateTaken();
                            if (dateTaken != 0) {
                                java.util.Date date = new java.util.Date(image.getDateTaken());
                                java.text.SimpleDateFormat dateFormat = new java.text.SimpleDateFormat();
                                dateString = dateFormat.format(date);
                                
                                ((TextView)d.findViewById(R.id.details_attrname_3)).setText(R.string.details_date_taken);
                                ((TextView)d.findViewById(R.id.details_attrvalu_3)).setText(dateString);
                            } else {
                                d.findViewById(R.id.details_daterow).setVisibility(View.GONE);
                            }

                            
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

    static MenuItemsResult addVideoMenuItems(
            Menu menu,
            int inclusions,
            final Activity activity,
            final Handler handler,
            final SelectedImageGetter mGetter,
            final Runnable onDelete, 
            final Runnable preWork, 
            final Runnable postWork) {
        
        if ((inclusions & INCLUDE_VIEWPLAY_MENU) != 0) {
            menu.add(VIDEO_SAVING_ITEM, MENU_VIDEO_PLAY, 0, R.string.video_play).setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                public boolean onMenuItemClick(MenuItem item) {
                    if (preWork != null)
                        preWork.run();

                    Intent intent = new Intent(Intent.ACTION_VIEW, mGetter.getCurrentImageUri());
                    activity.startActivity(intent);

                    // don't do the postWork since we're launching another activity
                    return true;
                }
            });
        }
        
        if ((inclusions & INCLUDE_SHARE_MENU) != 0) {
            MenuItem item = menu.add(VIDEO_SAVING_ITEM, 0, 0, R.string.camera_share).setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                public boolean onMenuItemClick(MenuItem item) {
                    Uri u = mGetter.getCurrentImageUri();
                    if (u == null)
                        return true;
                    
                    if (preWork != null)
                        preWork.run();

                    Intent intent = new Intent();
                    intent.setAction(Intent.ACTION_SEND);
                    intent.setType(mGetter.getCurrentImage().getMimeType());
                    intent.putExtra(Intent.EXTRA_STREAM, u);
                    try {
                        activity.startActivity(Intent.createChooser(intent,
                                activity.getText(R.string.sendVideo)));
                    } catch (android.content.ActivityNotFoundException ex) {
                        Toast.makeText(activity, R.string.no_way_to_share_video, Toast.LENGTH_SHORT).show();

                        if (postWork != null)
                            postWork.run();
                    }
                    return true;
                }
            });
            item.setIcon(android.R.drawable.ic_menu_share);
        }     

        if ((inclusions & INCLUDE_DELETE_MENU) != 0) {
            MenuItem deleteMenu = menu.add(VIDEO_SAVING_ITEM, MENU_VIDEO_TOSS, 0, R.string.camera_toss).setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                public boolean onMenuItemClick(MenuItem item) {
                    if (preWork != null)
                        preWork.run();

                    activity.getContentResolver().delete(mGetter.getCurrentImageUri(), null, null);

                    if (onDelete != null)
                        onDelete.run();

                    if (postWork != null)
                        postWork.run();
                    
                    return true;
                }
            });
            deleteMenu.setIcon(android.R.drawable.ic_menu_delete);
            deleteMenu.setAlphabeticShortcut('d');
        }

        return null;
    }
    
    static void deletePhoto(Activity activity, final Runnable onDelete) {
        boolean confirm = android.preference.PreferenceManager.getDefaultSharedPreferences(activity).getBoolean("pref_gallery_confirm_delete_key", true);
        if (!confirm) {
            if (onDelete != null)
                onDelete.run();
        } else {
            android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(activity);
            b.setIcon(R.drawable.delete_image);
            b.setTitle(R.string.confirm_delete_title);
            b.setMessage(R.string.confirm_delete_message);
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
    }
    
    static MenuItem addFlipOrientation(Menu menu, final Activity activity, final SharedPreferences prefs) {
        // position 41 after rotate
        return menu
                .add(Menu.CATEGORY_SECONDARY, 304, 41, R.string.flip_orientation)
                .setOnMenuItemClickListener(
                        new MenuItem.OnMenuItemClickListener() {
            public boolean onMenuItemClick(MenuItem item) {
                int current = activity.getRequestedOrientation();
                int newOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
                if (current == android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
                    newOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
                }
                SharedPreferences.Editor editor = prefs.edit();
                editor.putInt("nuorientation", newOrientation);
                editor.commit();
                requestOrientation(activity, prefs);
                return true;
            }
        })
        .setIcon(android.R.drawable.ic_menu_always_landscape_portrait);
    }

    static void requestOrientation(Activity activity, SharedPreferences prefs) {
        int req = prefs.getInt("nuorientation",
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        // A little trick: use USER instead of UNSPECIFIED, so we ignore the
        // orientation set by the activity below.  It may have forced a landscape
        // orientation, which the user has now cleared here.
        activity.setRequestedOrientation(
                req == android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                        ? android.content.pm.ActivityInfo.SCREEN_ORIENTATION_USER
                        : req);
    }

    static public class YouTubeUploadInfoDialog extends Dialog {
        private CheckBox mPrivate;
        private ImageManager.VideoObject mVideo;
        private EditText mTitle;
        private EditText mTags;
        private EditText mDescription;
        private Spinner mCategory;
        private Button mUpload;

        public YouTubeUploadInfoDialog(final Activity activity,
                    final ArrayList<String> categoriesShort,
                    final ArrayList<String> categoriesLong,
                    ImageManager.VideoObject video,
                    final Runnable postRunnable) {
            super(activity, android.R.style.Theme_Dialog);
            mVideo = video;
            setContentView(R.layout.youtube_upload_info);
            setTitle(R.string.upload_dialog_title);

            mPrivate = (CheckBox)findViewById(R.id.public_or_private);
            if (!mPrivate.isChecked()) {
                mPrivate.setChecked(true);
            }
            
            mTitle = (EditText)findViewById(R.id.video_title);
            mTags = (EditText)findViewById(R.id.video_tags);
            mDescription = (EditText)findViewById(R.id.video_description);
            mCategory = (Spinner)findViewById(R.id.category);
            
            if (Config.LOGV)
                Log.v(TAG, "setting categories in adapter");
            android.widget.ArrayAdapter<String> categories = new android.widget.ArrayAdapter<String>(activity, android.R.layout.simple_spinner_item, categoriesLong);
            categories.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            mCategory.setAdapter(categories);

            if (mVideo != null) {
                mTitle.setText(mVideo.getTitle());
                mTags.setText(mVideo.getTags());
                mDescription.setText(mVideo.getDescription());
            }
            
            mUpload = (Button)findViewById(R.id.do_upload);
            mUpload.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v)
                {
                    if (mVideo != null) {
                        mVideo.setName(mTitle.getText().toString());
                        mVideo.setDescription(mDescription.getText().toString());
                        mVideo.setTags(mTags.getText().toString());
                    }
                    
                    YouTubeUploadInfoDialog.this.dismiss();
                    UploadAction.uploadImage(activity, mVideo);
                    if (postRunnable != null)
                        postRunnable.run();
                }
            });
        }
    }
}

