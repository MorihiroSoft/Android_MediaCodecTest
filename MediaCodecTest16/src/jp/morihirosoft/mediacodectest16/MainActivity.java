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

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;

public class MainActivity extends Activity implements
View.OnClickListener,
CameraView.CameraPreviewBuffer
{
	//---------------------------------------------------------------------
	// NATIVE METHODS
	//---------------------------------------------------------------------
	static {
		System.loadLibrary("MediaCodecTest16");
	}
	native public static int native_init(boolean f, int w, int h, int fps, int[] mask);
	native public static int native_quit();
	native public static int native_draw(byte[] src_yuv, int[] dst_rgb, int dst_yuv_fmt, byte[] dst_yuv);
	native public static int native_cnvavc(String src_path, String dst_path);

	//---------------------------------------------------------------------
	// MEMBERS
	//---------------------------------------------------------------------
	private final VideoParam mVideoParam = VideoParam.getInstance();

	private CameraView mCameraView = null;
	private EffectView mEffectView = null;
	private Button     mBtnStart   = null;
	private Button     mBtnStop    = null;
	private Button     mBtnPlay    = null;

	private MyRecorder mRecorder = null;

	private byte[]   mSrcYuv = null;
	private int[][]  mDstRgb = {null,null};
	private byte[][] mDstYuv = {null,null};
	private int[]    mDstIdx = {0};
	private int[]    mMask   = null;

	//---------------------------------------------------------------------
	// PUBLIC / PROTECTED METHODS
	//---------------------------------------------------------------------
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.main_activity);

		mCameraView = (CameraView)findViewById(R.id.cameraview);
		mEffectView = (EffectView)findViewById(R.id.effectview);
		mBtnStart   = (Button)findViewById(R.id.start);
		mBtnStop    = (Button)findViewById(R.id.stop);
		mBtnPlay    = (Button)findViewById(R.id.play);

		mBtnStart.setOnClickListener(this);
		mBtnStop.setOnClickListener(this);
		mBtnPlay.setOnClickListener(this);

		mBtnStart.setEnabled(true);
		mBtnStop.setEnabled(false);

		mCameraView.setup(this);

		// Buffers
		final int vw = mVideoParam.mSize.width;
		final int vh = mVideoParam.mSize.height;
		final int rgb_size = vw * vh;
		final int yuv_size = vw * vh * 3 / 2;
		mSrcYuv    = new byte[yuv_size];
		mDstRgb[0] = new int[rgb_size];
		mDstRgb[1] = new int[rgb_size];
		mDstYuv[0] = new byte[yuv_size];
		mDstYuv[1] = new byte[yuv_size];
		mMask      = new int[rgb_size];

		// for Effect sample
		Bitmap b = Bitmap.createBitmap(vw, vh, Bitmap.Config.ARGB_8888);
		Canvas c = new Canvas(b);
		Paint  p = new Paint();
		p.setStyle(Paint.Style.FILL);
		p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
		c.drawColor(Color.BLUE);
		c.drawOval(new RectF(0,0,vw,vh), p);
		b.getPixels(mMask, 0, vw, 0, 0, vw, vh);
	}

	@Override
	protected void onResume() {
		super.onResume();

		final int vw  = mVideoParam.mSize.width;
		final int vh  = mVideoParam.mSize.height;
		final int fps = mVideoParam.getMaxFps();

		// CameraView
		mCameraView.resume();

		// JNI
		if (native_init(mVideoParam.mFacingFront, vw, vh, fps, mMask) !=0) {
			native_quit();
		}

		// EffectView
		mEffectView.setup(vw, vh, mDstRgb, mDstIdx);
		mEffectView.resume();
		mEffectView.setZOrderMediaOverlay(true);
		mEffectView.setZOrderOnTop(true);
		mEffectView.getParent().bringChildToFront(mEffectView);

		// Video
		// -> not auto restart
	}

	@Override
	protected void onPause() {
		// Video
		stopVideo();

		// EffectView
		mEffectView.pause();

		// JNI
		native_quit();

		// CameraView
		mCameraView.pause();

		super.onPause();
	}

	@Override
	public void onClick(View v) {
		switch(v.getId()) {
		case R.id.start:
			startVideo();
			break;
		case R.id.stop:
			stopVideo();
			break;
		case R.id.play:
			playVideo();
			break;
		}
	}

	@Override
	public byte[] getBuffer() {
		return mSrcYuv;
	}

	@Override
	public void onPreviewFrame(byte[] src_yuv) {
		final int idx = 1 - mDstIdx[0];

		if (mRecorder == null) {
			native_draw(src_yuv, mDstRgb[idx], 0, null);
		} else {
			native_draw(src_yuv, mDstRgb[idx], mRecorder.getColorFormat(), mDstYuv[idx]);
			mRecorder.offerEncoder(mDstYuv[idx]);
		}

		synchronized (mDstIdx) {
			mDstIdx[0] = idx;
			mDstIdx.notify();
		}
	}

	//---------------------------------------------------------------------
	// PRIVATE METHODS
	//---------------------------------------------------------------------
	private void startVideo() {
		mBtnStart.setEnabled(false);
		mBtnStop.setEnabled(true);
		if (mRecorder == null) {
			mRecorder = new MyRecorder();
			if (mRecorder.start() != 0) {
				stopVideo();
				return;
			}
		}
	}

	private void stopVideo() {
		mBtnStart.setEnabled(true);
		mBtnStop.setEnabled(false);
		if (mRecorder != null) {
			mRecorder.stop();
			mRecorder = null;
		}
	}

	private void playVideo() {
		Uri uri = Uri.parse("file://"+mVideoParam.mOutput);
		Intent i = new Intent(Intent.ACTION_VIEW, uri);
		i.setDataAndType(uri, "video/mp4");
		startActivity(i);
	}
}
