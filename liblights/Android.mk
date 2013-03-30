# Copyright (C) 2008 The Android Open Source Project
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

LOCAL_PATH:= $(call my-dir)

ifeq ($(TARGET_PROVIDES_LIBLIGHT),true)
ifeq ($(BOARD_VENDOR),htc)
ifeq ($(TARGET_BOARD_PLATFORM),msm8960)

include $(CLEAR_VARS)

LOCAL_MODULE := lights.msm8960

LOCAL_MODULE_PATH := $(TARGET_OUT_SHARED_LIBRARIES)/hw

LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES := lights.c
LOCAL_SHARED_LIBRARIES := liblog

LOCAL_MODULE_TAGS := optional

include $(BUILD_SHARED_LIBRARY)

endif # TARGET_BOARD_PLATFORM
endif # TARGET_VENDOR
endif # TARGET_PROVIDES_LIBLIGHT
