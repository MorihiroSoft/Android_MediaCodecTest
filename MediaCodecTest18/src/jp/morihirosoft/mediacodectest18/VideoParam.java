/*
 * Copyright (C) 2013 MorihiroSoft
 * Copyright 2013 Google Inc. All Rights Reserved.
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
package jp.morihirosoft.mediacodectest18;

import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.hardware.Camera.Size;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.os.Environment;
import android.util.Log;

public class VideoParam {
	private static final boolean DEBUG = true;
	private static final String TAG = "VideoParam";

	//---------------------------------------------------------------------
	// CONSTANTS
	//---------------------------------------------------------------------
	private static final int    VIDEO_W = 640;
	private static final int    VIDEO_H = 480;
	private static final int    FPS     = 30;
	private static final String MIME    = "video/avc";
	private static final int    BPS     = 4*1024*1024;
	private static final int    IFI     = 5;
	private static final String SDCARD  = Environment.getExternalStorageDirectory().getPath();
	private static final String OUTPUT  = SDCARD + "/video.mp4";

	//---------------------------------------------------------------------
	// MEMBERS
	//---------------------------------------------------------------------
	public final int     mCameraId;
	public final boolean mFacingFront;
	public final Size    mSize;
	public final int[]   mFpsRange;
	public final String  mMime;
	public final int     mBps    = BPS;
	public final int     mIfi    = IFI;
	public final String  mOutput = OUTPUT;

	//---------------------------------------------------------------------
	// SINGLETON
	//---------------------------------------------------------------------
	private static volatile VideoParam sInstance = null;
	private static final    Object     sSyncObj  = new Object();

	public static VideoParam getInstance() {
		if (sInstance == null) {
			synchronized (sSyncObj) {
				if (sInstance == null) {
					sInstance = new VideoParam();
				}
			}
		}
		return sInstance;
	}

	private VideoParam() {
		// Id
		int camera_num = Camera.getNumberOfCameras();
		if (DEBUG) Log.d(TAG, "CameraNum = "+camera_num);
		if (camera_num < 1) {
			throw new UnsupportedOperationException("No camera");
		}
		mCameraId = 0;
		Log.i(TAG, "mCameraId = "+mCameraId);

		Camera.Parameters cp = null;
		try {
			Camera c = Camera.open(mCameraId);
			cp = c.getParameters();
			c.release();
		} catch (Exception e) {
			throw new RuntimeException();
		}
		final Camera.CameraInfo ci = new Camera.CameraInfo();
		Camera.getCameraInfo(mCameraId, ci);

		// Facing
		mFacingFront = (ci.facing == Camera.CameraInfo.CAMERA_FACING_FRONT);
		Log.i(TAG, "mFacingFront = "+mFacingFront);

		// Size
		Size size = null;
		for (Camera.Size s : cp.getSupportedPreviewSizes()) {
			if (DEBUG) Log.d(TAG, "Size = "+s.width+"x"+s.height);
			if (s.width == VIDEO_W && s.height == VIDEO_H) {
				size = s;
			}
		}
		if (size == null) {
			throw new UnsupportedOperationException(
					String.format("Not support size: %dx%d",VIDEO_W,VIDEO_H));
		}
		mSize = size;
		Log.i(TAG, "mSize = "+mSize.width+"x"+mSize.height);

		// Frame rate
		int[] fps = null;
		for (int[] f : cp.getSupportedPreviewFpsRange()) {
			if (DEBUG) Log.d(TAG, "FpsRange = {"+
					f[Camera.Parameters.PREVIEW_FPS_MIN_INDEX]+","+
					f[Camera.Parameters.PREVIEW_FPS_MAX_INDEX]+"}");
			if (f[Camera.Parameters.PREVIEW_FPS_MAX_INDEX] >= FPS*1000) {
				fps = f;
			}
		}
		if (fps == null) {
			throw new UnsupportedOperationException(
					String.format("Not support fps: %d",FPS));
		}
		mFpsRange = fps;
		Log.i(TAG, "mFpsRange = {"+
				mFpsRange[Camera.Parameters.PREVIEW_FPS_MIN_INDEX]+","+
				mFpsRange[Camera.Parameters.PREVIEW_FPS_MAX_INDEX]+"}");

		// Format (check only)
		final int img_fmt = cp.getPreviewFormat();
		switch (img_fmt) {
		case ImageFormat.NV21:
			Log.i(TAG, "mImageFormat = NV21");
			break;
		case ImageFormat.YUY2:
			throw new UnsupportedOperationException(
					"Not supported: ImageFormat=YUY2");
		case ImageFormat.YV12:
			throw new UnsupportedOperationException(
					"Not supported: ImageFormat=YV12");
		default:
			throw new UnsupportedOperationException(
					"Not supported: ImageFormat=???("+img_fmt+")");
		}

		// MIME
		String mime = null;
		int codec_num = MediaCodecList.getCodecCount();
		for (int i=0; i<codec_num; i++) {
			MediaCodecInfo info = MediaCodecList.getCodecInfoAt(i);
			if (info.isEncoder()) {
				if (DEBUG) Log.d(TAG, "Codec: "+info.getName());
				final String[] mimes = info.getSupportedTypes();
				for (String m : mimes) {
					if (DEBUG) Log.d(TAG, "MIME: "+m);
					if (MIME.equals(m)) {
						mime = m;
					}
				}
			}
		}
		if (mime == null) {
			throw new UnsupportedOperationException(
					String.format("Not support MIME: %s",MIME));
		}
		mMime = mime;
		Log.i(TAG, "mMime = "+mMime);
	}

	//---------------------------------------------------------------------
	// PUBLIC METHODS
	//---------------------------------------------------------------------
	public int getMaxFps() {
		return mFpsRange[Camera.Parameters.PREVIEW_FPS_MAX_INDEX] / 1000;
	}
}
