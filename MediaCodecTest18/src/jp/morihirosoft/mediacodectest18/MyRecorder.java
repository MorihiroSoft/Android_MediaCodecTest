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

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;

public class MyRecorder {
	//---------------------------------------------------------------------
	// MEMBERS
	//---------------------------------------------------------------------
	private final VideoParam mVideoParam = VideoParam.getInstance();

	private MediaCodec            mMediaCodec   = null;
	private InputSurface          mInputSurface = null;
	private MediaCodec.BufferInfo mBufferInfo   = null;
	private MediaMuxer            mMediaMuxer   = null;
	private int                   mTrackIndex   = -1;
	private boolean               mMuxerStarted = false;
	private int                   mTotalSize    = 0; //TODO: DEBUG

	//---------------------------------------------------------------------
	// PUBLIC METHODS
	//---------------------------------------------------------------------
	public MyRecorder() {
	}

	public void prepareEncoder() {
		if (mMediaCodec != null || mInputSurface != null) {
			throw new RuntimeException("prepareEncoder called twice?");
		}

		mBufferInfo = new MediaCodec.BufferInfo();
		try {
			MediaFormat format = MediaFormat.createVideoFormat(
					mVideoParam.mMime,
					mVideoParam.mSize.width,
					mVideoParam.mSize.height);
			format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
					MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
			format.setInteger(MediaFormat.KEY_BIT_RATE, mVideoParam.mBps);
			format.setInteger(MediaFormat.KEY_FRAME_RATE, mVideoParam.getMaxFps());
			format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, mVideoParam.mIfi);

			mMediaCodec = MediaCodec.createEncoderByType(mVideoParam.mMime);
			mMediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

			mMediaMuxer = new MediaMuxer(mVideoParam.mOutput,
					MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
			mMuxerStarted = false;
		} catch (Exception e) {
			releaseEncoder();
			throw (RuntimeException)e;
		}
	}

	public boolean firstTimeSetup() {
		if (!isRecording() || mInputSurface != null) {
			return false;
		}
		try {
			mInputSurface = new InputSurface(mMediaCodec.createInputSurface());
			mMediaCodec.start();
		} catch (Exception e) {
			releaseEncoder();
			throw (RuntimeException)e;
		}
		return true;
	}

	public boolean isRecording() {
		return mMediaCodec != null;
	}

	public void makeCurrent() {
		mInputSurface.makeCurrent();
	}

	synchronized public void swapBuffers() {
		if (!isRecording()) {
			return;
		}
		drainEncoder(false);
		mInputSurface.swapBuffers();
		mInputSurface.setPresentationTime(System.nanoTime());
	}

	synchronized public void stop() {
		drainEncoder(true);
		releaseEncoder();
	}

	//---------------------------------------------------------------------
	// PRIVATE...
	//---------------------------------------------------------------------
	private void releaseEncoder() {
		if (mMediaCodec != null) {
			mMediaCodec.stop();
			mMediaCodec.release();
			mMediaCodec = null;
		}
		if (mInputSurface != null) {
			mInputSurface.release();
			mInputSurface = null;
		}
		if (mMediaMuxer != null) {
			mMediaMuxer.stop();
			mMediaMuxer.release();
			mMediaMuxer = null;
		}
	}

	private void drainEncoder(boolean endOfStream) {
		if (endOfStream) {
			mMediaCodec.signalEndOfInputStream();
		}
		ByteBuffer[] encoderOutputBuffers = mMediaCodec.getOutputBuffers();
		while (true) {
			int encoderStatus = mMediaCodec.dequeueOutputBuffer(mBufferInfo, 0);
			if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
				if (!endOfStream) {
					break;
				}
			} else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
				encoderOutputBuffers = mMediaCodec.getOutputBuffers();
			} else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
				if (mMuxerStarted) {
					throw new RuntimeException("format changed twice");
				}
				MediaFormat newFormat = mMediaCodec.getOutputFormat();
				mTrackIndex = mMediaMuxer.addTrack(newFormat);
				mMediaMuxer.start();
				mMuxerStarted = true;
			} else {
				ByteBuffer encodedData = encoderOutputBuffers[encoderStatus];
				if (encodedData == null) {
					throw new RuntimeException("encoderOutputBuffer " + encoderStatus + " was null");
				}
				if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
					mBufferInfo.size = 0;
				}
				if (mBufferInfo.size != 0) {
					if (!mMuxerStarted) {
						throw new RuntimeException("muxer hasn't started");
					}
					encodedData.position(mBufferInfo.offset);
					encodedData.limit(mBufferInfo.offset + mBufferInfo.size);

					boolean calc_time = true; //TODO: DEBUG
					if (calc_time) {
						long t0 = System.currentTimeMillis();
						mMediaMuxer.writeSampleData(mTrackIndex, encodedData, mBufferInfo);
						mTotalSize += mBufferInfo.size;
						long dt = System.currentTimeMillis() - t0;
						if (dt>50) Log.e("DEBUG", String.format("XXX: dt=%d, size=%.2f",dt,(float)mTotalSize/1024/1024));
					} else {
						mMediaMuxer.writeSampleData(mTrackIndex, encodedData, mBufferInfo);
					}
				}
				mMediaCodec.releaseOutputBuffer(encoderStatus, false);
				if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
					break;
				}
			}
		}
	}
}
