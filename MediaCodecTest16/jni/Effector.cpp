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
#include "Effector.h"

#define  LOG_TAG "Effector"
#define  LOGE(...)  __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)
#if 0
#define  LOGI(...)  __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)
#else
#define  LOGI(...)
#endif

Effector::Effector(void)
: mUtils(NULL)
, mVideoWidth(0)
, mVideoHeight(0)
, mVideoArea(0)
, mMaskP(NULL)
{
}

Effector::~Effector(void)
{
	quit();
}

int32_t Effector::init(Utils* utils, int32_t width, int32_t height, uint32_t* maskP)
{
	mUtils       = utils;
	mVideoWidth  = width;
	mVideoHeight = height;
	mVideoArea   = width * height;
	mMaskP       = maskP;
	return 0;
}

int32_t Effector::quit(void)
{
	return 0;
}

int32_t Effector::draw(uint8_t* src_yuv, uint32_t* dst_rgb)
{
	mUtils->yuv_YUVtoRGB(src_yuv, dst_rgb);

	// Masking (effect sample)
	uint32_t* p = mMaskP;
	uint32_t* q = dst_rgb;
	for (int i=mVideoArea; i>0; i--) {
		if ((*p & 0xFF000000) != 0) {
			*q = *p;
		}
		p++;
		q++;
	}

	return 0;
}
