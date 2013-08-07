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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import android.opengl.EGL14;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.util.Log;

class GlUtil {
	private static final String TAG = "GlUtil";

	public static FloatBuffer createSquareVtx() {
		final float vtx[] = {
				// XYZ, UV
				-1f,  1f, 0f, 0f, 1f,
				-1f, -1f, 0f, 0f, 0f,
				1f,   1f, 0f, 1f, 1f,
				1f,  -1f, 0f, 1f, 0f,
		};
		ByteBuffer bb = ByteBuffer.allocateDirect(4 * vtx.length);
		bb.order(ByteOrder.nativeOrder());
		FloatBuffer fb = bb.asFloatBuffer();
		fb.put(vtx);
		fb.position(0);
		return fb;
	}

	public static float[] createIdentityMtx() {
		float[] m = new float[16];
		Matrix.setIdentityM(m, 0);
		return m;
	}

	public static int createProgram(String vertexSource, String fragmentSource) {
		int vs = loadShader(GLES20.GL_VERTEX_SHADER,   vertexSource);
		int fs = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);
		int program = GLES20.glCreateProgram();
		GLES20.glAttachShader(program, vs);
		GLES20.glAttachShader(program, fs);
		GLES20.glLinkProgram(program);
		//
		int[] linkStatus = new int[1];
		GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
		if (linkStatus[0] != GLES20.GL_TRUE) {
			Log.e(TAG, "Could not link program:");
			Log.e(TAG, GLES20.glGetProgramInfoLog(program));
			GLES20.glDeleteProgram(program);
			program = 0;
		}
		//
		return program;
	}

	public static int loadShader(int shaderType, String source) {
		int shader = GLES20.glCreateShader(shaderType);
		GLES20.glShaderSource(shader, source);
		GLES20.glCompileShader(shader);
		//
		int[] compiled = new int[1];
		GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
		if (compiled[0] == 0) {
			Log.e(TAG, "Could not compile shader(TYPE=" + shaderType + "):");
			Log.e(TAG, GLES20.glGetShaderInfoLog(shader));
			GLES20.glDeleteShader(shader);
			shader = 0;
		}
		//
		return shader;
	}

	public static void checkGlError(String op) {
		int error;
		while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
			Log.e(TAG, op + ": glGetError: 0x" + Integer.toHexString(error));
			throw new RuntimeException("glGetError encountered (see log)");
		}
	}

	public static void checkEglError(String op) {
		int error;
		while ((error = EGL14.eglGetError()) != EGL14.EGL_SUCCESS) {
			Log.e(TAG, op + ": eglGetError: 0x" + Integer.toHexString(error));
			throw new RuntimeException("eglGetError encountered (see log)");
		}
	}
}
