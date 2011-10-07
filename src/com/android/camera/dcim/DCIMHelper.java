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

package com.android.camera.dcim;

import java.io.File;
import java.io.FileFilter;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import android.os.Environment;
import android.util.Log;

/**
 * {@code DCIMHelper} is used to provide DCIM-compliant informations regarding
 * directory and file naming.
 */
public class DCIMHelper {

	private final static String TAG = DCIMHelper.class.getSimpleName();
	/**
	 * See spec {@literal "4.2.2 DCF directories"}
	 */
	private final static String RE_DCIM_DIRECTORY = "([1-9][0-9]{2})([A-Z_0-9]{5})(\\..+)?";

	/**
	 * See spec {@literal "4.3.1 DCF file names"}
	 */
	private final static String RE_DCIM_FILE = "([A-Z_0-9]{4})([0-9]{4})(\\..+)";

	/**
	 * 512 is the limit for FAT(16), but OSX creates hidden (._xxx) files for each
	 * image, plus a "._DSStore" file and Windows creates a "Thumbs.db". A value
	 * of 254 gives us room for another 2 junk files lying around (Linus, not more
	 * than 2, ok?).
	 */
	private final static int MAX_IMAGES_PER_DIRECTORY = 254;

	private static String dcimPath = Environment.getExternalStorageDirectory().toString() + "/DCIM/";

	private static DCIMDirectoryFileFilter dcimDirectoryFileFilter = new DCIMDirectoryFileFilter();
	private static DCIMDirectoryComparator dcimDirectoryComparator = new DCIMDirectoryComparator();
	private static DCIMImageFileFilter dcimImageFileFilter = new DCIMImageFileFilter();
	private static DCIMImageComparator dcimImageComparator = new DCIMImageComparator();

	private static NumberFormat nfDirectory;
	private static NumberFormat nfFile;

	static {
		nfDirectory = NumberFormat.getIntegerInstance();
		nfDirectory.setGroupingUsed(false);
		nfDirectory.setMinimumIntegerDigits(3);
		nfDirectory.setMaximumIntegerDigits(3);

		nfFile = NumberFormat.getIntegerInstance();
		nfFile.setGroupingUsed(false);
		nfFile.setMinimumIntegerDigits(4);
		nfFile.setMaximumIntegerDigits(4);

		/*
		 * Ensure that the first directory exists. Makes remaining code simpler.
		 */
		File firstDir = new File(dcimPath, "100ANDRO");
		if (!firstDir.exists()) {
			firstDir.mkdirs();
		}
	}

	private static File currentDir = null;

	/**
	 * Returns the full path to the directory currently in use.
	 * 
	 * @return the full path, DCIM-compliant (NNNAAAAA)
	 */
	public static String getCurrentDirectory() {
		File dcimDirectory = new File(dcimPath);
		if (currentDir == null) {
			File[] dirs = dcimDirectory.listFiles(dcimDirectoryFileFilter);
			if (dirs.length > 1) {
				List<File> dirsList = Arrays.asList(dirs);
				Collections.sort(dirsList, dcimDirectoryComparator);
				currentDir = dirsList.get(dirs.length - 1);
			} else {
				currentDir = dirs[0];
			}
		}
		return currentDir.getAbsolutePath();
	}

	/**
	 * Returns the full path to the directory to be used to store a new image.
	 * 
	 * @return the full path, DCIM-compliant (NNNAAAAA)
	 */
	public static String getDirectoryForNewImage() {
		if (currentDir == null) {
			getCurrentDirectory();
		}

		File[] files = currentDir.listFiles(dcimImageFileFilter);
		if (files.length >= MAX_IMAGES_PER_DIRECTORY) {
			makeNewDirectory();
		}
		return currentDir.getAbsolutePath();
	}

	/**
	 * Returns the name (without extension) for the next available "slot" in the
	 * current directory. The name is DCIM compliant, in the form "IMG_NNNN".
	 * 
	 * @return the name, DCIM compliant (AAAANNNN), without extension.
	 */
	public static String getNameForNewImage() {
		return getNameForNewItem("IMG_");
	}

	/**
	 * Returns the name (without extension) for the next available "slot" in the
	 * current directory. The name is DCIM compliant, in the form "VID_NNNN".
	 * 
	 * @return the name, DCIM compliant (AAAANNNN), without extension.
	 */
	public static String getNameForNewVideo() {
		return getNameForNewItem("VID_");
	}

	/**
	 * Returns the name (without extension) for the next available "slot" in the
	 * current directory.
	 * 
	 * @return the name, DCIM compliant (AAAANNNN), without extension.
	 */
	private static String getNameForNewItem(String prefix) {
		if (currentDir == null) {
			getCurrentDirectory();
		}
		File[] files = currentDir.listFiles(dcimImageFileFilter);
		if (files.length >= MAX_IMAGES_PER_DIRECTORY) {
			makeNewDirectory();
			files = currentDir.listFiles(dcimImageFileFilter);
		}
		if (files.length == 0) {
			return prefix + "0001";
		}
		List<File> filesList = Arrays.asList(files);
		Collections.sort(filesList, dcimImageComparator);
		File lastFile = filesList.get(files.length - 1);
		int nnnn = Integer.parseInt(lastFile.getName().substring(4, 8));
		if (nnnn >= 9999) {
			/*
			 * This should not happen, since out MAX_IMAGES_PER_DIRECTORY limit is way
			 * below 9999 and we are creating the images using consecutive numbers.
			 */
			Log.e(TAG, "Unable to create new image name: already reached limit of 9999");
			return null;
		}
		return prefix + nfFile.format(nnnn + 1);
	}

	private static void makeNewDirectory() {
		int nnn = Integer.parseInt(currentDir.getName().substring(0, 3));
		if (nnn < 999) {
			nnn++;
			File newDir = new File(dcimPath, nfDirectory.format(nnn) + "ANDRO");
			if (newDir.mkdirs()) {
				currentDir = newDir;
			} else {
				Log.e(TAG, "Unable to create NNNAAAAA directory: unable to create " + newDir.getName());
			}
		} else {
			Log.e(TAG, "Unable to create NNNAAAAA directory: reached 999 directories.");
		}
	}

	private static class DCIMDirectoryComparator implements Comparator<File> {
		public int compare(File dir1, File dir2) {
			return dir1.getName().substring(0, 3).compareTo(dir2.getName().substring(0, 3));
		}
	}

	private static class DCIMDirectoryFileFilter implements FileFilter {
		public boolean accept(File pathname) {
			if (pathname.isDirectory()) {
				return pathname.getName().matches(RE_DCIM_DIRECTORY);
			}
			return false;
		}
	}

	private static class DCIMImageFileFilter implements FileFilter {
		public boolean accept(File pathname) {
			if (pathname.isFile()) {
				return pathname.getName().matches(RE_DCIM_FILE);
			}
			return false;
		}
	}

	private static class DCIMImageComparator implements Comparator<File> {
		public int compare(File file1, File file2) {
			return file1.getName().substring(4, 8).compareTo(file2.getName().substring(4, 8));
		}
	}

	/**
	 * Scans the "/DCIM/Camera" directory renaming files into a proper DCIM
	 * structure.
	 */
	public static void convertToDCIM() {
		File cameraDir = new File(dcimPath, "Camera");
		File[] files = cameraDir.listFiles();
		List<File> filesList = Arrays.asList(files);
		Collections.sort(filesList, new Comparator<File>() {
			public int compare(File object1, File object2) {
				if (object1.lastModified() < object2.lastModified()) {
					return -1;
				} else if (object1.lastModified() > object2.lastModified()) {
					return 1;
				} else {
					return 0;
				}
			}
		});
		for (File file : filesList) {
			String name = file.getName().toLowerCase();
			String newName = null;
			if (name.endsWith("jpg")) {
				newName = getNameForNewImage() + ".jpg";
			} else if (name.endsWith("3gp")) {
				newName = getNameForNewVideo() + ".3gp";
			} else if (name.endsWith("mp4")) {
				newName = getNameForNewVideo() + ".mp4";
			} else if (name.endsWith("m4v")) {
				newName = getNameForNewVideo() + ".m4v";
			} else {
				continue;
			}
			File newFile = new File(getDirectoryForNewImage(), newName);
			if (!file.renameTo(newFile)) {
				Log.e(TAG, "Error renaming " + file.getAbsolutePath() + " to " + newFile.getAbsolutePath());
			}
		}
	}
}
