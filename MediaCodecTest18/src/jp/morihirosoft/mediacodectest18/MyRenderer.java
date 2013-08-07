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

import java.io.IOException;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;

class MyRenderer implements
GLSurfaceView.Renderer,
SurfaceTexture.OnFrameAvailableListener
{
	//---------------------------------------------------------------------
	// MEMBERS
	//---------------------------------------------------------------------
	private final VideoParam mVideoParam = VideoParam.getInstance();
	private final float[]    mTexMtx     = GlUtil.createIdentityMtx();

	private final CameraView mView;

	private Camera         mCamera       = null;
	private SurfaceTexture mSrfTex       = null;
	private int            mSrfTexId     = -1;
	private int            mFboTexId     = -1;
	private boolean        updateSurface = false;
	private RenderFbo      mRenderFbo    = null;
	private RenderScreen   mRenderScreen = null;
	private RenderSrfTex   mRenderSrfTex = null;

	//---------------------------------------------------------------------
	// PUBLIC METHODS
	//---------------------------------------------------------------------
	public MyRenderer(CameraView view) {
		mView = view;
	}

	public void setCamera(Camera camera) {
		mCamera = camera;
	}

	public void setRecorder(MyRecorder recorder) {
		synchronized(this) {
			if (recorder != null) {
				mRenderSrfTex = new RenderSrfTex(
						mVideoParam.mSize.width, mVideoParam.mSize.height,
						mFboTexId, recorder);
			} else {
				mRenderSrfTex = null;
			}
		}
	}

	public void onSurfaceCreated(GL10 glUnused, EGLConfig config) {
		GlUtil.checkGlError("onSurfaceCreated_S");

		GLES20.glDisable(GLES20.GL_DEPTH_TEST);
		GLES20.glDisable(GLES20.GL_CULL_FACE);
		GLES20.glDisable(GLES20.GL_BLEND);

		int[] textures = new int[1];
		GLES20.glGenTextures(1, textures, 0);
		mSrfTexId = textures[0];

		GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
		GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mSrfTexId);
		GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
				GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
		GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
				GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
		GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
				GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
		GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
				GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

		mSrfTex = new SurfaceTexture(mSrfTexId);
		mSrfTex.setOnFrameAvailableListener(this);
		try {
			mCamera.setPreviewTexture(mSrfTex);
		} catch (IOException t) {
		}
		synchronized(this) {
			updateSurface = false;
		}
		mCamera.startPreview();

		mRenderFbo = new RenderFbo(
				mVideoParam.mSize.width, mVideoParam.mSize.height, mSrfTexId);
		mFboTexId = mRenderFbo.getFboTexId();
		mRenderScreen = new RenderScreen(
				mVideoParam.mSize.width, mVideoParam.mSize.height, mFboTexId);
		GlUtil.checkGlError("onSurfaceCreated_E");
	}

	public void onSurfaceChanged(GL10 glUnused, int width, int height) {
		GlUtil.checkGlError("onSurfaceChanged_S");
		mRenderScreen.setSize(width, height);
		GlUtil.checkGlError("onSurfaceChanged_E");
	}

	public void onFrameAvailable(SurfaceTexture surface) {
		synchronized(this) {
			updateSurface = true;
		}
		mView.requestRender();
	}

	public void onDrawFrame(GL10 glUnused) {
		GlUtil.checkGlError("onDrawFrame_S");
		synchronized(this) {
			if (updateSurface) {
				mSrfTex.updateTexImage();
				mSrfTex.getTransformMatrix(mTexMtx);
				updateSurface = false;
			}
		}
		mRenderFbo.draw(mTexMtx);
		mRenderScreen.draw();
		if (mRenderSrfTex != null) {
			mRenderSrfTex.draw();
		}
		GlUtil.checkGlError("onDrawFrame_E");
	}
}
