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
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <math.h>
#include <android/log.h>

#include "Utils.h"

#define  LOG_TAG "Utils"
#define  LOGE(...)  __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)
#if 0
#define  LOGI(...)  __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)
#else
#define  LOGI(...)
#endif

//---------------------------------------------------------------------
// UTILS: PUBLIC METHODS
//---------------------------------------------------------------------
// Constructor
Utils::Utils(int32_t width, int32_t height)
{
	video_width  = width;
	video_height = height;
	video_area   = width * height;

	int32_t ret = yuv_init();
	if (ret != 0) {
		LOGE("%s(L=%d): yuv_init()=%d", __func__, __LINE__, ret);
		return;
	}
}

// Destructor
Utils::~Utils()
{
	int32_t ret = yuv_end();
	if (ret != 0) {
		LOGE("%s(L=%d): yuv_end()=%d", __func__, __LINE__, ret);
		return;
	}
}

//---------------------------------------------------------------------
// YUV: PUBLIC METHODS
//---------------------------------------------------------------------
//
int32_t Utils::yuv_init(void)
{
	tmp_rgb = new uint32_t[video_area];
	if (tmp_rgb == NULL) {
		return -1;
	}
	tmp_yuv = new uint8_t[video_area * 3 / 2];
	if (tmp_yuv == NULL) {
		return -1;
	}
	return 0;
}

//
int32_t Utils::yuv_end(void)
{
	if (tmp_rgb != NULL) {
		delete[] tmp_rgb;
		tmp_rgb = NULL;
	}
	if (tmp_yuv != NULL) {
		delete[] tmp_yuv;
		tmp_yuv = NULL;
	}
	return 0;
}

// YUV(NV21)→ARGB(8888)
uint32_t* Utils::yuv_YUVtoRGB(uint8_t* yuv, uint32_t* rgb32)
{
	if (rgb32 == NULL) {
		rgb32 = tmp_rgb;
	}
	const uint32_t vw  = video_width;
	const uint32_t vh  = video_height;
	const uint32_t vw2 = vw >> 1;
	const uint32_t vh2 = vh >> 1;
	const uint32_t va  = video_area;
	uint8_t*  yP1     = &yuv[0];
	uint8_t*  yP2     = &yuv[vw];
	uint8_t*  vuP     = &yuv[va];
	uint32_t* rgb32P1 = &rgb32[0];
	uint32_t* rgb32P2 = &rgb32[vw];

	for (uint32_t i=0; i<vh2; i++) {
		for (uint32_t j=0; j<vw2; j++) {
			int32_t y1 = ((*(yP1++))&0xFF) - 16;
			if (y1 < 0) y1 = 0;
			int32_t y2 = ((*(yP1++))&0xFF) - 16;
			if (y2 < 0) y2 = 0;
			int32_t y3 = ((*(yP2++))&0xFF) - 16;
			if (y3 < 0) y3 = 0;
			int32_t y4 = ((*(yP2++))&0xFF) - 16;
			if (y4 < 0) y4 = 0;

			int32_t v = ((*(vuP++))&0xFF) - 128;
			int32_t u = ((*(vuP++))&0xFF) - 128;

			*(rgb32P1++) = yuv_YUVtoRGB_1(y1, u, v);
			*(rgb32P1++) = yuv_YUVtoRGB_1(y2, u, v);
			*(rgb32P2++) = yuv_YUVtoRGB_1(y3, u, v);
			*(rgb32P2++) = yuv_YUVtoRGB_1(y4, u, v);
		}
		yP1 = yP2;
		yP2 += vw;
		rgb32P1 = rgb32P2;
		rgb32P2 += vw;
	}

	return rgb32;
}

// ARGB(8888)→YUV(*)
uint8_t* Utils::yuv_RGBtoYUV(uint32_t* rgb32, int32_t yuv_fmt, uint8_t* yuv)
{
	if (yuv == NULL) {
		yuv = tmp_yuv;
	}
	switch(yuv_fmt) {
	case 1: // Encoder.ColorFormat_NV12 (java)
		return yuv_RGBtoNV12(rgb32, yuv);
	case 2: // Encoder.ColorFormat_NV21 (java)
		return yuv_RGBtoNV21(rgb32, yuv);
	case 3: // Encoder.ColorFormat_I420 (java)
		return yuv_RGBtoI420(rgb32, yuv);
	}
	return NULL;
}

//---------------------------------------------------------------------
// YUV: PRIVATE METHODS
//---------------------------------------------------------------------
// YUV(NV21)→ARGB(8888)
uint32_t Utils::yuv_YUVtoRGB_1(int32_t y, int32_t u, int32_t v)
{
	y *= 1192;

	int32_t r = (y + 1634 * v);
	int32_t g = (y - 833 * v - 400 * u);
	int32_t b = (y + 2066 * u);

	r = (r<0 ? 0 : (r>262143 ? 262143 : r));
	g = (g<0 ? 0 : (g>262143 ? 262143 : g));
	b = (b<0 ? 0 : (b>262143 ? 262143 : b));

	return (uint32_t)(
			(0x00FF0000 & (r<< 6)) |
			(0x0000FF00 & (g>> 2)) |
			(0x000000FF & (b>>10)));
}

// ARGB(8888)→YUV(NV12)
uint8_t* Utils::yuv_RGBtoNV12(uint32_t* rgb32, uint8_t* yuv)
{
	const uint32_t vw = video_width;
	const uint32_t vh = video_height;
	const uint32_t va = video_area;

	int32_t yIndex = 0;
	int32_t uvIndex = va;

	for (uint32_t i=0,k=0; i<vh; i++) {
		for (uint32_t j=0; j<vw; j++,k++) {
			const int32_t R = (rgb32[k] & 0x00FF0000) >> 16;
			const int32_t G = (rgb32[k] & 0x0000FF00) >> 8;
			const int32_t B = (rgb32[k] & 0x000000FF);

			const int32_t Y = (( 66*R + 129*G +  25*B + 128) >> 8) +  16;
			yuv[yIndex++] = (uint8_t) ((Y < 0) ? 0 : ((Y > 255) ? 255 : Y));

			if (i % 2 == 0 && j % 2 == 0) {
				const int32_t U = ((-38*R -  74*G + 112*B + 128) >> 8) + 128;
				const int32_t V = ((112*R -  94*G -  18*B + 128) >> 8) + 128;
				yuv[uvIndex++] = (uint8_t)((U<0) ? 0 : ((U > 255) ? 255 : U));
				yuv[uvIndex++] = (uint8_t)((V<0) ? 0 : ((V > 255) ? 255 : V));
			}
		}
	}

	return yuv;
}

// ARGB(8888)→YUV(NV21)
uint8_t* Utils::yuv_RGBtoNV21(uint32_t* rgb32, uint8_t* yuv)
{
	const uint32_t vw = video_width;
	const uint32_t vh = video_height;
	const uint32_t va = video_area;

	int32_t yIndex = 0;
	int32_t uvIndex = va;

	for (uint32_t i=0,k=0; i<vh; i++) {
		for (uint32_t j=0; j<vw; j++,k++) {
			const int32_t R = (rgb32[k] & 0x00FF0000) >> 16;
			const int32_t G = (rgb32[k] & 0x0000FF00) >> 8;
			const int32_t B = (rgb32[k] & 0x000000FF);

			const int32_t Y = (( 66*R + 129*G +  25*B + 128) >> 8) +  16;
			yuv[yIndex++] = (uint8_t) ((Y < 0) ? 0 : ((Y > 255) ? 255 : Y));

			if (i % 2 == 0 && j % 2 == 0) {
				const int32_t V = ((112*R -  94*G -  18*B + 128) >> 8) + 128;
				const int32_t U = ((-38*R -  74*G + 112*B + 128) >> 8) + 128;
				yuv[uvIndex++] = (uint8_t)((V<0) ? 0 : ((V > 255) ? 255 : V));
				yuv[uvIndex++] = (uint8_t)((U<0) ? 0 : ((U > 255) ? 255 : U));
			}
		}
	}

	return yuv;
}

// ARGB(8888)→YUV(I420)
uint8_t* Utils::yuv_RGBtoI420(uint32_t* rgb32, uint8_t* yuv)
{
	const uint32_t vw = video_width;
	const uint32_t vh = video_height;
	const uint32_t va = video_area;

	int32_t yIndex = 0;
	int32_t uIndex = va;
	int32_t vIndex = va + va / 4;

	for (uint32_t i=0,k=0; i<vh; i++) {
		for (uint32_t j=0; j<vw; j++,k++) {
			const int32_t R = (rgb32[k] & 0x00FF0000) >> 16;
			const int32_t G = (rgb32[k] & 0x0000FF00) >> 8;
			const int32_t B = (rgb32[k] & 0x000000FF);

			const int32_t Y = (( 66*R + 129*G +  25*B + 128) >> 8) +  16;
			yuv[yIndex++] = (uint8_t) ((Y < 0) ? 0 : ((Y > 255) ? 255 : Y));

			if (i % 2 == 0 && j % 2 == 0) {
				const int32_t U = ((-38*R -  74*G + 112*B + 128) >> 8) + 128;
				yuv[uIndex++] = (uint8_t)((U<0) ? 0 : ((U > 255) ? 255 : U));

				const int32_t V = ((112*R -  94*G -  18*B + 128) >> 8) + 128;
				yuv[vIndex++] = (uint8_t)((V<0) ? 0 : ((V > 255) ? 255 : V));
			}
		}
	}

	return yuv;
}
