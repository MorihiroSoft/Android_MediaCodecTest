/*
 * Copyright (C) 2013 MorihiroSoft
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
package jp.morihirosoft.mediacodectest16;

import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.hardware.Camera.Size;
import android.os.Environment;
import android.util.Log;

public class VideoParam {
	private static final String TAG = "VideoParam";

	//---------------------------------------------------------------------
	// CONSTANTS
	//---------------------------------------------------------------------
	private static final int    VIDEO_W = 640;
	private static final int    VIDEO_H = 480;
	private static final int    FPS     = 30;
	private static final String MIME    = "video/avc";
	private static final int    BPS     = 4194304; // 0x400000
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
	public final String  mMime   = MIME;
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
		int num = Camera.getNumberOfCameras();
		if (num < 1) {
			throw new UnsupportedOperationException("No camera");
		}

		// Id
		mCameraId = 0;

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

		// Size
		Size size = null;
		for (Camera.Size s : cp.getSupportedPreviewSizes()) {
			if (s.width == VIDEO_W && s.height == VIDEO_H) {
				size = s;
			}
		}
		if (size == null) {
			throw new UnsupportedOperationException(
					String.format("Not support size: %dx%d",VIDEO_W,VIDEO_H));
		}
		mSize = size;

		// Frame rate
		int[] fps = null;
		for (int[] s : cp.getSupportedPreviewFpsRange()) {
			if (s[Camera.Parameters.PREVIEW_FPS_MAX_INDEX] >= FPS*1000) {
				fps = s;
			}
		}
		if (fps == null) {
			throw new UnsupportedOperationException(
					String.format("Not support fps: %d",FPS));
		}
		mFpsRange = fps;

		// Format (check only)
		final int img_fmt = cp.getPreviewFormat();
		switch (img_fmt) {
		case ImageFormat.NV21:
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

		//
		Log.i(TAG, "CameraId = "+mCameraId);
		Log.i(TAG, "FacingFront = "+mFacingFront);
		Log.i(TAG, "Size = "+mSize.width+"x"+mSize.height);
		Log.i(TAG, "FpsRange = {"+
				mFpsRange[Camera.Parameters.PREVIEW_FPS_MIN_INDEX]+","+
				mFpsRange[Camera.Parameters.PREVIEW_FPS_MAX_INDEX]+"}");
		Log.i(TAG, "ImageFormat = NV21");
	}

	//---------------------------------------------------------------------
	// PUBLIC METHODS
	//---------------------------------------------------------------------
	public int getMaxFps() {
		return mFpsRange[Camera.Parameters.PREVIEW_FPS_MAX_INDEX] / 1000;
	}
}
