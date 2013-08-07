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

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;

public class MainActivity extends Activity implements
View.OnClickListener
{
	//---------------------------------------------------------------------
	// MEMBERS
	//---------------------------------------------------------------------
	private CameraView mCameraView = null;
	private Button     mBtnStart   = null;
	private Button     mBtnStop    = null;
	private Button     mBtnPlay    = null;

	//---------------------------------------------------------------------
	// PUBLIC METHODS
	//---------------------------------------------------------------------
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.main_activity);

		mCameraView = (CameraView)findViewById(R.id.cameraview);
		mBtnStart   = (Button)findViewById(R.id.start);
		mBtnStop    = (Button)findViewById(R.id.stop);
		mBtnPlay    = (Button)findViewById(R.id.play);

		mBtnStart.setOnClickListener(this);
		mBtnStop.setOnClickListener(this);
		mBtnPlay.setOnClickListener(this);

		mBtnStart.setEnabled(true);
		mBtnStop.setEnabled(false);
	}

	@Override
	protected void onResume() {
		super.onResume();
		mCameraView.onResume();
	}

	@Override
	protected void onPause() {
		mCameraView.onPause();
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

	//---------------------------------------------------------------------
	// PRIVATE...
	//---------------------------------------------------------------------
	private void startVideo() {
		mBtnStart.setEnabled(false);
		mBtnStop.setEnabled(true);
		mCameraView.startVideo();
	}

	private void stopVideo() {
		mBtnStart.setEnabled(true);
		mBtnStop.setEnabled(false);
		mCameraView.stopVideo();
	}

	private void playVideo() {
		Uri uri = Uri.parse("file://"+VideoParam.getInstance().mOutput);
		Intent i = new Intent(Intent.ACTION_VIEW, uri);
		i.setDataAndType(uri, "video/mp4");
		startActivity(i);
	}
}
