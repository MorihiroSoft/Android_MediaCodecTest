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

import java.nio.FloatBuffer;

import android.opengl.EGL14;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;

class RenderSrfTex {
	//---------------------------------------------------------------------
	// MEMBERS
	//---------------------------------------------------------------------
	private final FloatBuffer mVtxBuf = GlUtil.createSquareVtx();

	private final int mFboTexW;
	private final int mFboTexH;
	private final int mFboTexId;
	private final MyRecorder mRecorder;

	private int mProgram         = -1;
	private int maPositionHandle = -1;
	private int maTexCoordHandle = -1;
	private int muSamplerHandle  = -1;

	private EGLDisplay mSavedEglDisplay     = null;
	private EGLSurface mSavedEglDrawSurface = null;
	private EGLSurface mSavedEglReadSurface = null;
	private EGLContext mSavedEglContext     = null;

	//---------------------------------------------------------------------
	// PUBLIC METHODS
	//---------------------------------------------------------------------
	public RenderSrfTex(int w, int h, int id, MyRecorder recorder) {
		mFboTexW  = w;
		mFboTexH  = h;
		mFboTexId = id;
		mRecorder = recorder;
	}

	public void draw() {
		saveRenderState();
		{
			GlUtil.checkGlError("draw_S");

			if (mRecorder.firstTimeSetup()) {
				mRecorder.makeCurrent();
				initGL();
			} else {
				mRecorder.makeCurrent();
			}

			GLES20.glViewport(0, 0, mFboTexW, mFboTexH);

			GLES20.glClearColor(0f, 0f, 0f, 1f);
			GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT | GLES20.GL_COLOR_BUFFER_BIT);

			GLES20.glUseProgram(mProgram);

			mVtxBuf.position(0);
			GLES20.glVertexAttribPointer(maPositionHandle,
					3, GLES20.GL_FLOAT, false, 4*(3+2), mVtxBuf);
			GLES20.glEnableVertexAttribArray(maPositionHandle);

			mVtxBuf.position(3);
			GLES20.glVertexAttribPointer(maTexCoordHandle,
					2, GLES20.GL_FLOAT, false, 4*(3+2), mVtxBuf);
			GLES20.glEnableVertexAttribArray(maTexCoordHandle);

			GLES20.glUniform1i(muSamplerHandle, 0);

			GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
			GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mFboTexId);

			GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

			mRecorder.swapBuffers();

			GlUtil.checkGlError("draw_E");
		}
		restoreRenderState();
	}

	//---------------------------------------------------------------------
	// PRIVATE...
	//---------------------------------------------------------------------
	private void initGL() {
		GlUtil.checkGlError("initGL_S");

		final String vertexShader =
				//
				"attribute vec4 aPosition;\n" +
				"attribute vec4 aTexCoord;\n" +
				"varying   vec2 vTexCoord;\n" +
				"void main() {\n" +
				"  gl_Position = aPosition;\n" +
				"  vTexCoord   = aTexCoord.xy;\n" +
				"}\n";
		final String fragmentShader =
				//
				"precision mediump float;\n" +
				"uniform sampler2D uSampler;\n" +
				"varying vec2      vTexCoord;\n" +
				"void main() {\n" +
				"  gl_FragColor = texture2D(uSampler, vTexCoord);\n" +
				"}\n";
		mProgram         = GlUtil.createProgram(vertexShader, fragmentShader);
		maPositionHandle = GLES20.glGetAttribLocation(mProgram, "aPosition");
		maTexCoordHandle = GLES20.glGetAttribLocation(mProgram, "aTexCoord");
		muSamplerHandle  = GLES20.glGetUniformLocation(mProgram, "uSampler");

		GLES20.glDisable(GLES20.GL_DEPTH_TEST);
		GLES20.glDisable(GLES20.GL_CULL_FACE);
		GLES20.glDisable(GLES20.GL_BLEND);

		GlUtil.checkGlError("initGL_E");
	}

	private void saveRenderState() {
		mSavedEglDisplay     = EGL14.eglGetCurrentDisplay();
		mSavedEglDrawSurface = EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW);
		mSavedEglReadSurface = EGL14.eglGetCurrentSurface(EGL14.EGL_READ);
		mSavedEglContext     = EGL14.eglGetCurrentContext();
	}

	private void restoreRenderState() {
		if (!EGL14.eglMakeCurrent(
				mSavedEglDisplay,
				mSavedEglDrawSurface,
				mSavedEglReadSurface,
				mSavedEglContext)) {
			throw new RuntimeException("eglMakeCurrent failed");
		}
	}
}
