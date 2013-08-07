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
#include "stdio.h"

#include "jp_morihirosoft_mediacodectest16_MainActivity.h"
#include "Utils.h"
#include "Effector.h"

#define  LOG_TAG "JNI"
#define  LOGE(...)  __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)
#if 0
#define  LOGI(...)  __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)
#else
#define  LOGI(...)
#endif

#if 1
#ifdef __cplusplus
extern "C" {
#endif
extern int32_t cnvavc(const char* src_path, const char* dst_path,
		int32_t video_w, int32_t video_h, int32_t fps);
#ifdef __cplusplus
}
#endif
#endif

//---------------------------------------------------------------------
// Static data
//---------------------------------------------------------------------
static bool      sFacingFront = false;
static int32_t   sVideoW      = 0;
static int32_t   sVideoH      = 0;
static int32_t   sFps         = 0;
static uint32_t  sDataSize    = 0;
static uint8_t*  sDataP       = NULL;
static uint32_t* sMaskP       = NULL;

static Utils*    sUtils    = NULL;
static Effector* sEffector = NULL;

//---------------------------------------------------------------------
//
//---------------------------------------------------------------------
jint JNI_OnLoad(JavaVM* vm, void* reserved)
{
	return JNI_VERSION_1_6;
}

void JNI_OnUnload(JavaVM* vm, void* reserved)
{
}

//---------------------------------------------------------------------
//
//---------------------------------------------------------------------
/** . */
JNIEXPORT jint JNICALL Java_jp_morihirosoft_mediacodectest16_MainActivity_native_1init
(JNIEnv* env, jclass clazz, jboolean jFacingFront, jint jVideoW, jint jVideoH, jint jFps, jintArray jMask)
{
	if (jVideoW <= 0 || jVideoH <= 0 || jFps <= 0 || jMask == NULL) {
		return -1;
	}

	sFacingFront = jFacingFront;
	sVideoW = jVideoW;
	sVideoH = jVideoH;
	sFps = jFps;

	if (sDataP != NULL) {
		delete[] sDataP;
	}
	sDataSize = sVideoW * sVideoH * 3 / 2;
	sDataP = new uint8_t[sDataSize];

	uint32_t* maskP = (uint32_t*)(env->GetIntArrayElements(jMask, NULL));
	if (sMaskP != NULL) {
		delete[] sMaskP;
	}
	sMaskP = new uint32_t[sVideoW * sVideoH];
	memcpy(sMaskP, maskP, sizeof(uint32_t) * sVideoW * sVideoH);
	env->ReleaseIntArrayElements(jMask, (jint*)maskP, JNI_ABORT);

	sUtils    = new Utils(jVideoW, jVideoH);
	sEffector = new Effector();

	return sEffector->init(sUtils, sVideoW, sVideoH, sMaskP);
}

/** . */
JNIEXPORT jint JNICALL Java_jp_morihirosoft_mediacodectest16_MainActivity_native_1quit
(JNIEnv* env, jclass clazz)
{
	jint rcode = sEffector->quit();

	if (sUtils != NULL) {
		delete sUtils;
		sUtils = NULL;
	}
	if (sEffector != NULL) {
		delete sEffector;
		sEffector = NULL;
	}

	sVideoW = 0;
	sVideoH = 0;
	sDataSize = 0;
	if (sDataP != NULL) {
		delete[] sDataP;
		sDataP = NULL;
	}
	if (sMaskP != NULL) {
		delete[] sMaskP;
		sMaskP = NULL;
	}

	return rcode;
}

/** . */
JNIEXPORT jint JNICALL Java_jp_morihirosoft_mediacodectest16_MainActivity_native_1draw
(JNIEnv* env, jclass clazz, jbyteArray jSrcYuv, jintArray jDstRgb, jint jDstYuvFmt, jbyteArray jDstYuv)
{
	if (sVideoW <= 0 || sVideoH <= 0 || sDataP == NULL) {
		return -1;
	}

	uint8_t*  srcYuvP = (uint8_t*)(env->GetByteArrayElements(jSrcYuv, NULL));
	uint32_t* dstRgbP = (uint32_t*)(env->GetIntArrayElements(jDstRgb, NULL));
	uint8_t*  dstYuvP = NULL;
	if (jDstYuv != NULL) {
		dstYuvP = (uint8_t*)(env->GetByteArrayElements(jDstYuv, NULL));
	}

	// H-FLIP?
	if (sFacingFront) {
		// only for NV21
		const int32_t w1 = sVideoW;
		const int32_t h1 = sVideoH;
		const int32_t wh = w1 / 2;
		const int32_t hh = h1 / 2;
		const int32_t w2 = w1 * 2;
		uint8_t* sy = srcYuvP;
		uint8_t* dy = sDataP + w1 - 1;
		unsigned short* suv = ((unsigned short*)srcYuvP) + wh * h1;
		unsigned short* duv = ((unsigned short*)sDataP)  + wh * h1 + wh - 1;
		// Y
		for (int32_t y=h1; y>0; y--) {
			for (int32_t x=w1; x>0; x--) {
				*dy-- = *sy++;
			}
			dy += w2;
		}
		// U/V
		for (int32_t y=hh; y>0; y--) {
			for (int32_t x=wh; x>0; x--) {
				*duv-- = *suv++;
			}
			duv += w1;
		}
	} else {
		memcpy(sDataP, srcYuvP, sizeof(uint8_t) * sDataSize);
	}

	jint rcode = sEffector->draw(sDataP, dstRgbP);
	if (rcode == 0) {
		// for Video recording
		if (dstYuvP != NULL) {
			sUtils->yuv_RGBtoYUV(dstRgbP, jDstYuvFmt, dstYuvP);
		}
	}

	env->ReleaseByteArrayElements(jSrcYuv, (jbyte*)srcYuvP, JNI_ABORT);
	env->ReleaseIntArrayElements(jDstRgb, (jint*)dstRgbP, 0);
	if (jDstYuv != NULL) {
		env->ReleaseByteArrayElements(jDstYuv, (jbyte*)dstYuvP, 0);
	}

	return rcode;
}

/** . */
JNIEXPORT jint JNICALL Java_jp_morihirosoft_mediacodectest16_MainActivity_native_1cnvavc
(JNIEnv* env, jclass clazz, jstring jSrcPath, jstring jDstPath)
{
	const char* src_path = env->GetStringUTFChars(jSrcPath, NULL);
	const char* dst_path = env->GetStringUTFChars(jDstPath, NULL);

	jint rcode = cnvavc(src_path, dst_path, sVideoW, sVideoH, sFps);

	if (src_path != NULL) {
		env->ReleaseStringUTFChars(jSrcPath, src_path);
	}
	if (dst_path != NULL) {
		env->ReleaseStringUTFChars(jDstPath, dst_path);
	}

	return rcode;
}
