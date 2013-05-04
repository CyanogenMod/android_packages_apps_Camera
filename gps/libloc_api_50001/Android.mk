ifneq ($(BUILD_TINY_ANDROID),true)
#Compile this library only for builds with the latest modem image

LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE := libloc_adapter

LOCAL_MODULE_TAGS := optional

LOCAL_SHARED_LIBRARIES := \
    libutils \
    libcutils \
    libgps.utils \
    libdl

LOCAL_SRC_FILES += \
    loc_eng_log.cpp \
    LocApiAdapter.cpp

LOCAL_CFLAGS += \
     -fno-short-enums \
     -D_ANDROID_

LOCAL_CFLAGS += -DFEATURE_IPV6

ifeq ($(FEATURE_DELEXT), true)
LOCAL_CFLAGS += -DFEATURE_DELEXT
endif #FEATURE_DELEXT

ifeq ($(FEATURE_ULP), true)
LOCAL_CFLAGS += -DFEATURE_ULP
endif #FEATURE_ULP

LOCAL_C_INCLUDES:= \
    $(TARGET_OUT_HEADERS)/gps.utils

LOCAL_COPY_HEADERS_TO:= libloc_eng/
LOCAL_COPY_HEADERS:= \
   LocApiAdapter.h \
   loc.h \
   loc_eng.h \
   loc_eng_xtra.h \
   loc_eng_ni.h \
   loc_eng_agps.h \
   loc_eng_msg.h \
   loc_eng_msg_id.h \
   loc_eng_log.h

LOCAL_PRELINK_MODULE := false

include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)

LOCAL_MODULE := libloc_eng

LOCAL_MODULE_TAGS := optional

LOCAL_SHARED_LIBRARIES := \
    libutils \
    libcutils \
    libloc_adapter \
    libgps.utils

LOCAL_SRC_FILES += \
    loc_eng.cpp \
    loc_eng_agps.cpp \
    loc_eng_xtra.cpp \
    loc_eng_ni.cpp \
    loc_eng_log.cpp \
    loc_eng_nmea.cpp

LOCAL_SRC_FILES += \
    loc_eng_dmn_conn.cpp \
    loc_eng_dmn_conn_handler.cpp \
    loc_eng_dmn_conn_thread_helper.c \
    loc_eng_dmn_conn_glue_msg.c \
    loc_eng_dmn_conn_glue_pipe.c

LOCAL_CFLAGS += \
     -fno-short-enums \
     -D_ANDROID_

LOCAL_CFLAGS += -DFEATURE_IPV6

ifeq ($(FEATURE_ULP), true)
LOCAL_CFLAGS += -DFEATURE_ULP
endif #FEATURE_ULP

LOCAL_C_INCLUDES:= \
    $(TARGET_OUT_HEADERS)/gps.utils \
    hardware/qcom/gps/loc_api/ulp/inc

LOCAL_PRELINK_MODULE := false

include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)

LOCAL_MODULE := gps.msm8960

LOCAL_MODULE_TAGS := optional

## Libs

LOCAL_SHARED_LIBRARIES := \
    libutils \
    libcutils \
    libloc_eng \
    libgps.utils \
    libdl

LOCAL_SRC_FILES += \
    loc.cpp \
    gps.c

LOCAL_CFLAGS += \
    -fno-short-enums \
    -D_ANDROID_ \

LOCAL_CFLAGS += -DFEATURE_IPV6

## Includes
LOCAL_C_INCLUDES:= \
    $(TARGET_OUT_HEADERS)/gps.utils \
    hardware/qcom/gps/loc_api/ulp/inc

LOCAL_PRELINK_MODULE := false
LOCAL_MODULE_PATH := $(TARGET_OUT_SHARED_LIBRARIES)/hw

include $(BUILD_SHARED_LIBRARY)

endif # not BUILD_TINY_ANDROID
