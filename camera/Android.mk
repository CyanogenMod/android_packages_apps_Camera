ifeq ($(TARGET_PROVIDES_CAMERA_HAL),true)
ifneq ($(USE_CAMERA_STUB),true)
ifeq ($(BOARD_USES_QCOM_HARDWARE),true)
ifeq ($(BOARD_VENDOR),htc)
ifeq ($(TARGET_BOARD_PLATFORM),msm8960)

# When zero we link against libmmcamera; when 1, we dlopen libmmcamera.
DLOPEN_LIBMMCAMERA := 1

LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

# hax for libmmjpeg
$(shell mkdir -p $(OUT)/obj/SHARED_LIBRARIES/libmmjpeg_intermediates)
$(shell touch $(OUT)/obj/SHARED_LIBRARIES/libmmjpeg_intermediates/export_includes)

LOCAL_CFLAGS := -DDLOPEN_LIBMMCAMERA=$(DLOPEN_LIBMMCAMERA)

LOCAL_CFLAGS += -DHW_ENCODE

LOCAL_CFLAGS += -include bionic/libc/kernel/common/linux/socket.h

# To Choose neon/C routines for YV12 conversion
LOCAL_CFLAGS += -DUSE_NEON_CONVERSION

# Uncomment below line to enable smooth zoom
#LOCAL_CFLAGS += -DCAMERA_SMOOTH_ZOOM

ifeq ($(TARGET_USES_ION),true)
LOCAL_CFLAGS += -DUSE_ION
endif

ifeq ($(TARGET_BOARD_PLATFORM),msm7627)
LOCAL_CFLAGS += -DNUM_PREVIEW_BUFFERS=6 -D_ANDROID_
else
LOCAL_CFLAGS += -DNUM_PREVIEW_BUFFERS=4 -D_ANDROID_
endif

LOCAL_C_INCLUDES += \
    $(TARGET_OUT_HEADERS)/mm-camera \
    $(TARGET_OUT_HEADERS)/mm-camera/common \
    $(TARGET_OUT_HEADERS)/mm-still \
    $(TARGET_OUT_HEADERS)/mm-still/jpeg \

ifeq ($(TARGET_BOARD_PLATFORM),msm8960)
LOCAL_C_INCLUDES += $(TARGET_OUT_HEADERS)/mm-core/omxcore
LOCAL_C_INCLUDES += $(TARGET_OUT_HEADERS)/mm-still/mm-omx
endif

#yyan if debug service layer and up , use stub camera!
LOCAL_C_INCLUDES += \
        frameworks/base/services/camera/libcameraservice \
        hardware/qcom/display/libgralloc \
        hardware/qcom/display/libgenlock \
        hardware/qcom/media/libstagefrighthw

# CM doesn't use kernel includes
#LOCAL_C_INCLUDES+= $(TARGET_OUT_INTERMEDIATES)/KERNEL_OBJ/usr/include/media
#LOCAL_C_INCLUDES+= $(TARGET_OUT_INTERMEDIATES)/KERNEL_OBJ/usr/include
#LOCAL_ADDITIONAL_DEPENDENCIES := $(TARGET_OUT_INTERMEDIATES)/KERNEL_OBJ/usr

ifeq ($(TARGET_BOARD_PLATFORM),msm8960)
LOCAL_SRC_FILES := mm_camera_interface2.c mm_camera_stream.c \
                   mm_camera_channel.c mm_camera.c \
                   mm_camera_poll_thread.c mm_camera_notify.c \
                   mm_camera_helper.c mm_jpeg_encoder.c \
                   QCameraHAL.cpp QCameraHWI_Parm.cpp \
                   QCameraHWI.cpp QCameraHWI_Preview.cpp \
                   QCameraHWI_Record.cpp QCameraHWI_Still.cpp \
                   QCameraHWI_Mem.cpp QCameraHWI_Display.cpp \
                   QCameraStream.cpp QualcommCamera2.cpp
                   #mm_camera_sock.c
else
LOCAL_SRC_FILES := QualcommCamera.cpp QualcommCameraHardware.cpp
endif

LOCAL_SHARED_LIBRARIES := libgenlock libbinder

ifeq ($(TARGET_BOARD_PLATFORM),msm8960)
LOCAL_SHARED_LIBRARIES += libutils libui libcamera_client liblog libcutils libmmjpeg #libmmstillomx libimage-jpeg-enc-omx-comp
else
LOCAL_SHARED_LIBRARIES += libutils libui libcamera_client liblog libcutils libmmjpeg
endif

ifneq ($(DLOPEN_LIBMMCAMERA),1)
LOCAL_SHARED_LIBRARIES += liboemcamera
else
LOCAL_SHARED_LIBRARIES += libdl
endif

LOCAL_MODULE_PATH := $(TARGET_OUT_SHARED_LIBRARIES)/hw
LOCAL_MODULE := camera.$(TARGET_BOARD_PLATFORM)
LOCAL_MODULE_TAGS := optional
include $(BUILD_SHARED_LIBRARY)

endif # TARGET_BOARD_PLATFORM
endif # BOARD_VENDOR
endif # BOARD_USES_QCOM_HARDWARE
endif # USE_CAMERA_STUB
endif # TARGET_PROVIDES_CAMERA_HAL
