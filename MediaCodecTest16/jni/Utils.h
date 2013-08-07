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
#ifndef __UTILS__
#define __UTILS__

//---------------------------------------------------------------------
// Utilities
//---------------------------------------------------------------------
class Utils {
	//-----------------------------------------------------------------
	// UTILS
	//-----------------------------------------------------------------
public:
	Utils(int32_t width, int32_t height);
	~Utils();

private:
	int32_t video_width;
	int32_t video_height;
	int32_t video_area;

	//-----------------------------------------------------------------
	// YUV
	//-----------------------------------------------------------------
public:
	uint32_t* tmp_rgb;
	uint8_t*  tmp_yuv;

	int32_t yuv_init(void);
	int32_t yuv_end(void);
	uint32_t* yuv_YUVtoRGB(uint8_t* yuv, uint32_t* rgb32=NULL);
	uint8_t* yuv_RGBtoYUV(uint32_t* rgb32, int32_t yuv_fmt=0, uint8_t* yuv=NULL);

private:
	uint32_t yuv_YUVtoRGB_1(int32_t y, int32_t u, int32_t v);
	uint8_t* yuv_RGBtoNV12(uint32_t* rgb32, uint8_t* yuv);
	uint8_t* yuv_RGBtoNV21(uint32_t* rgb32, uint8_t* yuv);
	uint8_t* yuv_RGBtoI420(uint32_t* rgb32, uint8_t* yuv);
};

#endif // __UTILS__
