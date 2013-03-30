# Copyright 2006 The Android Open Source Project

ifeq ($(BOARD_PROVIDES_LIBRIL),true)
ifeq ($(TARGET_BOARD_PLATFORM),msm8960)
ifeq ($(BOARD_VENDOR),htc)

LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES:= \
    ril.cpp \
    ril_event.cpp

LOCAL_SHARED_LIBRARIES := \
    libutils \
    libbinder \
    libcutils \
    libhardware_legacy

LOCAL_CFLAGS :=

LOCAL_MODULE:= libril

LOCAL_LDLIBS += -lpthread

include $(BUILD_SHARED_LIBRARY)

endif # BOARD_VENDOR
endif # TARGET_BOARD_PLATFORM
endif # BOARD_PROVIDES_LIBRIL
