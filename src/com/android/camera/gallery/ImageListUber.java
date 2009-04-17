/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.camera.gallery;

import com.android.camera.ImageManager;

import android.net.Uri;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * A union of different <code>IImageList</code>.
 */
public class ImageListUber implements IImageList {
    @SuppressWarnings("unused")
    private static final String TAG = "ImageListUber";

    private final IImageList [] mSubList;
    private final int mSort;

    // This is an array of Longs wherein each Long consists of
    // two components.  The first component indicates the number of
    // consecutive entries that belong to a given sublist.
    // The second component indicates which sublist we're referring
    // to (an int which is used to index into mSubList).
    private ArrayList<Long> mSkipList = null;
    private int [] mSkipCounts = null;

    public HashMap<String, String> getBucketIds() {
        HashMap<String, String> hashMap = new HashMap<String, String>();
        for (IImageList list : mSubList) {
            hashMap.putAll(list.getBucketIds());
        }
        return hashMap;
    }

    public ImageListUber(IImageList [] sublist, int sort) {
        mSubList = sublist.clone();
        mSort = sort;
    }

    public void checkThumbnails(ThumbCheckCallback cb, int totalThumbnails) {
        for (IImageList i : mSubList) {
            int count = i.getCount();
            i.checkThumbnails(cb, totalThumbnails);
            totalThumbnails -= count;
        }
    }

    public void deactivate() {
        final IImageList sublist[] = mSubList;
        final int length = sublist.length;
        int pos = -1;
        while (++pos < length) {
            IImageList sub = sublist[pos];
            sub.deactivate();
        }
    }

    public int getCount() {
        final IImageList sublist[] = mSubList;
        final int length = sublist.length;
        int count = 0;
        for (int i = 0; i < length; i++)
            count += sublist[i].getCount();
        return count;
    }

    public boolean isEmpty() {
        final IImageList sublist[] = mSubList;
        final int length = sublist.length;
        for (int i = 0; i < length; i++) {
            if (!sublist[i].isEmpty()) {
                return false;
            }
        }
        return true;
    }

    // mSkipCounts is used to tally the counts as we traverse
    // the mSkipList.  It's a member variable only so that
    // we don't have to allocate each time through.  Otherwise
    // it could just as easily be a local.

    public synchronized IImage getImageAt(int index) {
        if (index < 0 || index > getCount()) {
            throw new IndexOutOfBoundsException(
                    "index " + index + " out of range max is " + getCount());
        }

        // first make sure our allocations are in order
        if (mSkipCounts == null || mSubList.length > mSkipCounts.length) {
            mSkipCounts = new int[mSubList.length];
        }

        if (mSkipList == null) {
            mSkipList = new ArrayList<Long>();
        }

        // zero out the mSkipCounts since that's only used for the
        // duration of the function call.
        for (int i = 0; i < mSubList.length; i++) {
            mSkipCounts[i] = 0;
        }

        // a counter of how many images we've skipped in
        // trying to get to index.  alternatively we could
        // have decremented index but, alas, I liked this
        // way more.
        int skipCount = 0;

        // scan the existing mSkipList to see if we've computed
        // enough to just return the answer
        for (int i = 0; i < mSkipList.size(); i++) {
            long v = mSkipList.get(i);

            int offset = (int) (v & 0xFFFFFFFF);
            int which  = (int) (v >> 32);

            if (skipCount + offset > index) {
                int subindex = mSkipCounts[which] + (index - skipCount);
                IImage img = mSubList[which].getImageAt(subindex);
                return img;
            }

            skipCount += offset;
            mSkipCounts[which] += offset;
        }

        // if we get here we haven't computed the answer for
        // "index" yet so keep computing.  This means running
        // through the list of images and either modifying the
        // last entry or creating a new one.
        while (true) {
            // We are merging the sublists into this uber list.
            // We pick the next image by choosing the one with
            // max/min timestamp from the next image of each sublists.
            // Then we record this fact in mSkipList (which encodes
            // sublist number in a run-length encoding fashion).
            long maxTimestamp = mSort == ImageManager.SORT_ASCENDING
                    ? Long.MAX_VALUE
                    : Long.MIN_VALUE;
            int which = -1;
            for (int i = 0; i < mSubList.length; i++) {
                int pos = mSkipCounts[i];
                IImageList list = mSubList[i];
                if (pos < list.getCount()) {
                    IImage image = list.getImageAt(pos);
                    // this should never be null but sometimes the database is
                    // causing problems and it is null
                    if (image != null) {
                        long timestamp = image.getDateTaken();
                        if (mSort == ImageManager.SORT_ASCENDING
                                ? (timestamp < maxTimestamp)
                                : (timestamp > maxTimestamp)) {
                            maxTimestamp = timestamp;
                            which = i;
                        }
                    }
                }
            }

            if (which == -1) {
                return null;
            }

            boolean done = false;
            if (mSkipList.size() > 0) {
                int pos = mSkipList.size() - 1;
                long oldEntry = mSkipList.get(pos);
                if ((oldEntry >> 32) == which) {
                    long newEntry = oldEntry + 1;
                    mSkipList.set(pos, newEntry);
                    done = true;
                }
            }
            if (!done) {
                long newEntry = ((long) which << 32) | 1;  // initial count = 1
                mSkipList.add(newEntry);
            }

            if (skipCount++ == index) {
                return mSubList[which].getImageAt(mSkipCounts[which]);
            }
            mSkipCounts[which] += 1;
        }
    }

    public IImage getImageForUri(Uri uri) {
        // TODO: perhaps we can preflight the base of the uri
        // against each sublist first
        for (int i = 0; i < mSubList.length; i++) {
            IImage img = mSubList[i].getImageForUri(uri);
            if (img != null) return img;
        }
        return null;
    }

    /**
     * Modify the skip list when an image is deleted by finding
     * the relevant entry in mSkipList and decrementing the
     * counter.  This is simple because deletion can never
     * cause change the order of images.
     */
    public void modifySkipCountForDeletedImage(int index) {
        int skipCount = 0;

        for (int i = 0; i < mSkipList.size(); i++) {
            long v = mSkipList.get(i);

            int offset = (int) (v & 0xFFFFFFFF);
            int which  = (int) (v >> 32);

            if (skipCount + offset > index) {
                mSkipList.set(i, v - 1);
                break;
            }

            skipCount += offset;
        }
    }

    public boolean removeImage(IImage image) {
        IImageList parent = image.getContainer();
        int pos = -1;
        int baseIndex = 0;
        while (++pos < mSubList.length) {
            IImageList sub = mSubList[pos];
            if (sub == parent) {
                if (sub.removeImage(image)) {
                    modifySkipCountForDeletedImage(baseIndex);
                    return true;
                } else {
                    break;
                }
            }
            baseIndex += sub.getCount();
        }
        return false;
    }

    public void removeImageAt(int index) {
        IImage img = getImageAt(index);
        if (img != null) {
            IImageList list = img.getContainer();
            if (list != null) {
                list.removeImage(img);
                modifySkipCountForDeletedImage(index);
            }
        }
    }
}
