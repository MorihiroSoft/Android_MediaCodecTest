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

import android.opengl.GLES20;
import android.opengl.Matrix;

class RenderScreen {
	//---------------------------------------------------------------------
	// MEMBERS
	//---------------------------------------------------------------------
	private final FloatBuffer mVtxBuf = GlUtil.createSquareVtx();
	private final float[]     mPosMtx = GlUtil.createIdentityMtx();

	private final int mFboTexW;
	private final int mFboTexH;
	private final int mFboTexId;

	private int mProgram         = -1;
	private int maPositionHandle = -1;
	private int maTexCoordHandle = -1;
	private int muPosMtxHandle   = -1;
	private int muSamplerHandle  = -1;

	private int mScreenW  = -1;
	private int mScreenH  = -1;

	//---------------------------------------------------------------------
	// PUBLIC METHODS
	//---------------------------------------------------------------------
	public RenderScreen(int w, int h, int id) {
		mFboTexW  = w;
		mFboTexH  = h;
		mFboTexId = id;

		initGL();
	}

	public void setSize(int w, int h) {
		mScreenW = w;
		mScreenH = h;

		Matrix.setIdentityM(mPosMtx, 0);
		if (mFboTexW * mScreenH < mFboTexH * mScreenW) {
			Matrix.scaleM(mPosMtx, 0,
					(float)mFboTexW*((float)mScreenH/(float)mFboTexH)/(float)mScreenW, 1f, 1f);
		} else {
			Matrix.scaleM(mPosMtx, 0,
					1f, (float)mFboTexH*((float)mScreenW/(float)mFboTexW)/(float)mScreenH, 1f);
		}
	}

	public void draw() {
		if (mScreenW <= 0 || mScreenH <= 0) {
			return;
		}
		GlUtil.checkGlError("draw_S");

		GLES20.glViewport(0, 0, mScreenW, mScreenH);

		GLES20.glClearColor(0.5f, 0.5f, 0.5f, 1f);
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

		GLES20.glUniformMatrix4fv(muPosMtxHandle, 1, false, mPosMtx, 0);
		GLES20.glUniform1i(muSamplerHandle, 0);

		GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
		GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mFboTexId);

		GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

		GlUtil.checkGlError("draw_E");
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
				"uniform   mat4 uPosMtx;\n" +
				"varying   vec2 vTexCoord;\n" +
				"void main() {\n" +
				"  gl_Position = uPosMtx * aPosition;\n" +
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
		muPosMtxHandle   = GLES20.glGetUniformLocation(mProgram, "uPosMtx");
		muSamplerHandle  = GLES20.glGetUniformLocation(mProgram, "uSampler");

		GlUtil.checkGlError("initGL_E");
	}
}
