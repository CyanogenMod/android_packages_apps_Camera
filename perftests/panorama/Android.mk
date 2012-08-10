local_target_dir := $(TARGET_OUT_DATA)/local/tmp

LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_C_INCLUDES := \
    $(LOCAL_PATH)/../../jni/feature_mos/src \
    $(LOCAL_PATH)/../../jni/feature_stab/src \
    $(LOCAL_PATH)/../../jni/feature_stab/db_vlvm

LOCAL_CFLAGS := -O3 -DNDEBUG

LOCAL_SRC_FILES := benchmark.cpp

LOCAL_SHARED_LIBRARIES := libjni_mosaic libGLESv2 libEGL

LOCAL_MODULE_TAGS := tests

LOCAL_LDFLAGS :=  -llog -lGLESv2

LOCAL_MODULE := panorama_bench

LOCAL_MODULE_PATH := $(local_target_dir)

include $(BUILD_EXECUTABLE)
