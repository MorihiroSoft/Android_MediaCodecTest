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
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

class EffectView extends SurfaceView implements
SurfaceHolder.Callback,
Runnable
{
	//---------------------------------------------------------------------
	// MEMBERS
	//---------------------------------------------------------------------
	private boolean       mIsResumed = false;
	private boolean       mIsCreated = false;
	private Thread        mThread    = null;
	private SurfaceHolder mHolder    = null;
	private int           mSurfaceW  = 0;
	private int           mSurfaceH  = 0;
	private int           mImageW    = 0;
	private int           mImageH    = 0;
	private int[][]       mImagePix  = null;
	private int[]         mImageIdx  = null;

	//---------------------------------------------------------------------
	// PUBLIC MRTHODS
	//---------------------------------------------------------------------
	public EffectView(Context context) {
		super(context);
		init();
	}

	public EffectView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}

	public EffectView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		init();
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		mIsCreated = true;
		startThread();
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
		mHolder = holder;
		mSurfaceW = width;
		mSurfaceH = height;
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		mIsCreated = false;
		stopThread();
	}

	public void setup(int image_w, int image_h, int[][] image_pix, int[] image_idx) {
		mImageW   = image_w;
		mImageH   = image_h;
		mImagePix = image_pix;
		mImageIdx = image_idx;
	}

	public void resume() {
		mIsResumed = true;
		startThread();
	}

	public void pause() {
		mIsResumed = false;
		stopThread();
	}

	public void run() {
		Canvas canvas;
		Paint paint = new Paint();

		while(mThread != null){
			if (mHolder == null || mSurfaceW < 1 || mSurfaceH < 1) {
				continue;
			}
			int idx = 0;
			try {
				synchronized (mImageIdx) {
					mImageIdx.wait();
					idx = mImageIdx[0];
				}
			} catch (InterruptedException e) {
				e.printStackTrace();
				throw new RuntimeException();
			}

			canvas = mHolder.lockCanvas();
			if (canvas == null) {
				continue;
			}

			canvas.drawColor(Color.GRAY);

			float s;
			if (mSurfaceW * mImageH < mSurfaceH * mImageW) {
				s = (float)mSurfaceW / (float)mImageW;
			} else {
				s = (float)mSurfaceH / (float)mImageH;
			}
			canvas.scale(s, s);
			canvas.drawBitmap(mImagePix[idx], 0, mImageW, 0, 0, mImageW, mImageH, false, paint);

			mHolder.unlockCanvasAndPost(canvas);
		}
	}

	//---------------------------------------------------------------------
	// PRIVATE...
	//---------------------------------------------------------------------
	private void init() {
		getHolder().addCallback(this);
	}

	private void startThread() {
		if (mIsCreated && mIsResumed && mThread == null) {
			mThread = new Thread(this);
			mThread.start();
		}
	}

	private void stopThread() {
		if (mThread != null) {
			mThread = null;
		}
	}
}
