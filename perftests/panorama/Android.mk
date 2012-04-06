LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_C_INCLUDES := \
    $(LOCAL_PATH)/../../jni/feature_mos/src \
    $(LOCAL_PATH)/../../jni/feature_stab/src \
    $(LOCAL_PATH)/../../jni/feature_stab/db_vlvm

LOCAL_CFLAGS := -O3 -DNDEBUG

LOCAL_SRC_FILES := benchmark.cpp

LOCAL_SHARED_LIBRARIES := libjni_mosaic

LOCAL_MODULE_TAGS := tests

LOCAL_MODULE := panorama_bench

include $(BUILD_EXECUTABLE)
