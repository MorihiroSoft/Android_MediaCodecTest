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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;

public class MyRecorder {
	//---------------------------------------------------------------------
	// CONSTANTS
	//---------------------------------------------------------------------
	private static final int ColorFormat_NV12 = 1;
	private static final int ColorFormat_NV21 = 2;
	private static final int ColorFormat_I420 = 3;

	private static final int[][] ColorFormatList = {
		{ColorFormat_NV12, MediaCodecInfo.CodecCapabilities.COLOR_TI_FormatYUV420PackedSemiPlanar},
		{ColorFormat_NV21, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar},
		{ColorFormat_I420, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar},
	};

	//---------------------------------------------------------------------
	// MEMBERS
	//---------------------------------------------------------------------
	private final VideoParam mVideoParam = VideoParam.getInstance();
	private final String     mFileMp4 = mVideoParam.mOutput;
	private final String     mFileTmp = mFileMp4+".tmp";

	private MediaCodec           mMediaCodec  = null;
	private int                  mColorFormat = 0;
	private BufferedOutputStream mOutput      = null;

	//---------------------------------------------------------------------
	// PUBLIC METHODS
	//---------------------------------------------------------------------
	public MyRecorder() {
		File f1 = new File(mFileTmp);
		if (!f1.exists()) {
			try {
				f1.createNewFile();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		File f2 = new File(mFileMp4);
		if (!f2.exists()) {
			try {
				f2.createNewFile();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public int start() {
		// MediaCodec, ColorFormat
		mMediaCodec = MediaCodec.createEncoderByType(mVideoParam.mMime);
		MediaFormat mediaFormat = MediaFormat.createVideoFormat(
				mVideoParam.mMime, mVideoParam.mSize.width, mVideoParam.mSize.height);
		mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE,
				mVideoParam.mBps);
		mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE,
				mVideoParam.getMaxFps());
		mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,
				mVideoParam.mIfi);
		int col_fmt = -1;
		for (int[] colorFormat : ColorFormatList) {
			try {
				mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat[1]);
				mMediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
				col_fmt = colorFormat[0];
			} catch (Exception e) {
				continue;
			}
		}
		if (col_fmt < 0) {
			mMediaCodec.release();
			throw new UnsupportedOperationException("Not found color format");
		}
		mColorFormat = col_fmt;
		mMediaCodec.start();

		//
		try {
			mOutput = new BufferedOutputStream(new FileOutputStream(mFileTmp));
		} catch (Exception e){
			e.printStackTrace();
			return -1;
		}
		return 0;
	}

	public void stop() {
		mMediaCodec.stop();
		mMediaCodec.release();
		mMediaCodec = null;
		mColorFormat = 0;
		try {
			mOutput.flush();
			mOutput.close();
		} catch (Exception e){
			e.printStackTrace();
		}
		mOutput = null;

		// Convert
		int rcode = MainActivity.native_cnvavc(mFileTmp, mFileMp4);
		if (rcode != 0) {
			throw new RuntimeException("native_cnvavc()="+rcode);
		}
	}

	public void offerEncoder(byte[] in) {
		if (mMediaCodec == null) {
			return;
		}
		try {
			ByteBuffer[] iBufs = mMediaCodec.getInputBuffers();
			ByteBuffer[] oBufs = mMediaCodec.getOutputBuffers();

			int iIdx = mMediaCodec.dequeueInputBuffer(-1);
			if (iIdx >= 0) {
				ByteBuffer iBuf = iBufs[iIdx];
				iBuf.clear();
				iBuf.put(in);
				mMediaCodec.queueInputBuffer(iIdx, 0, in.length, 0, 0);
			}

			MediaCodec.BufferInfo bufInfo = new MediaCodec.BufferInfo();
			int oIdx = mMediaCodec.dequeueOutputBuffer(bufInfo,0);
			while (oIdx >= 0) {
				ByteBuffer oBuf = oBufs[oIdx];
				byte[] out = new byte[bufInfo.size];
				oBuf.get(out);
				mOutput.write(out, 0, out.length);
				mMediaCodec.releaseOutputBuffer(oIdx, false);
				oIdx = mMediaCodec.dequeueOutputBuffer(bufInfo, 0);
			}
		} catch (Throwable t) {
			t.printStackTrace();
		}
	}

	public int getColorFormat() {
		return mColorFormat;
	}
}
