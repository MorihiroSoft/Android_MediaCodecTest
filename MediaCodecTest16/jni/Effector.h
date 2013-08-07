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
#ifndef __EFFECTOR__
#define __EFFECTOR__

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <memory.h>
#include <math.h>
#include <time.h>
#include <android/log.h>

#include "Utils.h"

class Effector {
protected:
	Utils*    mUtils;
	int32_t   mVideoWidth;
	int32_t   mVideoHeight;
	int32_t   mVideoArea;
	uint32_t* mMaskP;

public:
	Effector(void);
	~Effector(void);
	int32_t init(Utils* utils, int32_t width, int32_t height, uint32_t* maskP);
	int32_t quit(void);
	int32_t draw(uint8_t* src_yuv, uint32_t* dst_rgb);
};

#endif // __EFFECTOR__
