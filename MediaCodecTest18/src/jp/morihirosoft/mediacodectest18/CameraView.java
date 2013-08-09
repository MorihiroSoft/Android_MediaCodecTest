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

import android.content.Context;
import android.hardware.Camera;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;

class CameraView extends GLSurfaceView {
	//---------------------------------------------------------------------
	// MEMBERS
	//---------------------------------------------------------------------
	private final VideoParam mVideoParam = VideoParam.getInstance();

	private Camera      mCamera   = null;
	private MyRenderer  mRenderer = null;
	private MyRecorder  mRecorder = null;

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

	@Override
	public void onResume() {
		super.onResume();
		initCamera();
	}

	@Override
	public void onPause() {
		quitCamera();
		super.onPause();
	}

	public void startVideo() {
		if (mRecorder == null) {
			mRecorder = new MyRecorder();
			mRecorder.prepareEncoder();
			mRenderer.setRecorder(mRecorder);
		}
	}

	public void stopVideo() {
		if (mRecorder != null) {
			mRecorder.stop();
			mRecorder = null;
			mRenderer.setRecorder(null);
		}
	}

	//---------------------------------------------------------------------
	// PRIVATE...
	//---------------------------------------------------------------------
	private void init() {
		setEGLContextClientVersion(2);
		mRenderer = new MyRenderer(this);
		setRenderer(mRenderer);
		setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
	}

	private void initCamera() {
		if (mCamera == null) {
			try {
				mCamera = Camera.open(mVideoParam.mCameraId);
				Camera.Parameters cp = mCamera.getParameters();
				cp.setPreviewSize(mVideoParam.mSize.width, mVideoParam.mSize.height);
				cp.setPreviewFpsRange(
						mVideoParam.mFpsRange[Camera.Parameters.PREVIEW_FPS_MIN_INDEX],
						mVideoParam.mFpsRange[Camera.Parameters.PREVIEW_FPS_MAX_INDEX]);
				mCamera.setParameters(cp);
				mRenderer.setCamera(mCamera);
			} catch (Exception e) {
				e.printStackTrace();
				throw new RuntimeException("setup camera");
			}
		}
	}

	private void quitCamera() {
		if (mCamera != null) {
			mCamera.stopPreview();
			mCamera.release();
			mCamera = null;
		}
	}
}
