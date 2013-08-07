#
# Copyright (C) 2013 MorihiroSoft
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_ALLOW_UNDEFINED_SYMBOLS=false

# Library name
LOCAL_MODULE    := MediaCodecTest16

# Source files
LOCAL_SRC_FILES := \
    Effector.cpp \
    Utils.cpp \
    cnvavc.c \
    jp_morihirosoft_mediacodectest16_MainActivity.cpp

#
LOCAL_CFLAGS    := -Wall -Werror -Wno-deprecated
LOCAL_LDLIBS    := -llog -lm

include $(BUILD_SHARED_LIBRARY)
