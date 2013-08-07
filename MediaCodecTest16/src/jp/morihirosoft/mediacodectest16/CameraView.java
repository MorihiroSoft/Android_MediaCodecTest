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

import android.content.Context;
import android.hardware.Camera;
import android.util.AttributeSet;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup.LayoutParams;

class CameraView extends SurfaceView implements
SurfaceHolder.Callback,
Camera.PreviewCallback
{
	//---------------------------------------------------------------------
	// INTERFACE
	//---------------------------------------------------------------------
	interface CameraPreviewBuffer {
		abstract byte[] getBuffer();
		abstract void onPreviewFrame(byte[] data);
	}

	//---------------------------------------------------------------------
	// MEMBERS
	//---------------------------------------------------------------------
	private final VideoParam mVideoParam = VideoParam.getInstance();

	private boolean             mIsResumed           = false;
	private boolean             mIsCreated           = false;
	private CameraPreviewBuffer mCameraPreviewBuffer = null;
	private Camera              mCamera              = null;

	//---------------------------------------------------------------------
	// PUBLIC METHODS
	//---------------------------------------------------------------------
	public CameraView(Context context) {
		super(context);
		init();
	}

	public CameraView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}

	public CameraView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		init();
	}

	public void setup(CameraPreviewBuffer previewbuffer) {
		mCameraPreviewBuffer = previewbuffer;
	}

	public void resume() {
		mIsResumed = true;
		startCamera();
	}

	public void pause() {
		mIsResumed = false;
		stopCamera();
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		mIsCreated = true;
		startCamera();
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
		if (mCameraPreviewBuffer == null) {
			throw new IllegalStateException("mCameraPreviewBuffer=null");
		}
		if (mCamera != null) {
			mCamera.stopPreview();

			if (width > mVideoParam.mSize.width || height > mVideoParam.mSize.height) {
				LayoutParams lparam = getLayoutParams();
				lparam.width  = mVideoParam.mSize.width;
				lparam.height = mVideoParam.mSize.height;
				setLayoutParams(lparam);
			}

			mCamera.addCallbackBuffer(mCameraPreviewBuffer.getBuffer());
			mCamera.setPreviewCallbackWithBuffer(this);
			mCamera.startPreview();
		}
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		mIsCreated = false;
		stopCamera();

	}

	@Override
	public void onPreviewFrame(byte[] data, Camera camera) {
		mCameraPreviewBuffer.onPreviewFrame(data);
		camera.addCallbackBuffer(mCameraPreviewBuffer.getBuffer());
	}

	//---------------------------------------------------------------------
	// PRIVATE...
	//---------------------------------------------------------------------
	private void init() {
		if (Camera.getNumberOfCameras() < 1) {
			throw new UnsupportedOperationException("No camera");
		}
		getHolder().addCallback(this);
	}

	private void startCamera() {
		if (mCameraPreviewBuffer == null) {
			throw new IllegalStateException("mCameraPreviewBuffer=null");
		}
		if (mIsCreated && mIsResumed && mCamera == null) {
			try {
				mCamera = Camera.open(mVideoParam.mCameraId);
				Camera.Parameters cp = mCamera.getParameters();
				cp.setPreviewSize(mVideoParam.mSize.width, mVideoParam.mSize.height);
				cp.setPreviewFpsRange(
						mVideoParam.mFpsRange[Camera.Parameters.PREVIEW_FPS_MIN_INDEX],
						mVideoParam.mFpsRange[Camera.Parameters.PREVIEW_FPS_MAX_INDEX]);
				mCamera.setParameters(cp);
				mCamera.addCallbackBuffer(mCameraPreviewBuffer.getBuffer());
				mCamera.setPreviewCallbackWithBuffer(this);
				mCamera.setPreviewDisplay(getHolder());
				mCamera.startPreview();
			} catch (Exception e) {
				e.printStackTrace();
				throw new RuntimeException("setup camera");
			}
		}
	}

	private void stopCamera() {
		if (mCamera != null) {
			mCamera.stopPreview();
			mCamera.setPreviewCallback(null);
			mCamera.release();
			mCamera = null;
		}
	}
}
