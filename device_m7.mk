#
# Copyright (C) 2011 The CyanogenMod Project
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

$(call inherit-product, $(SRC_TARGET_DIR)/product/full_base_telephony.mk)

# common msm8960 configs
$(call inherit-product, device/htc/msm8960-common/msm8960.mk)

DEVICE_PACKAGE_OVERLAYS += $(LOCAL_PATH)/overlay

# Ramdisk
PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/ramdisk/root/fstab.m7:root/fstab.m7 \
    $(LOCAL_PATH)/ramdisk/root/init:root/init \
    $(LOCAL_PATH)/ramdisk/root/init.m7.rc:root/init.m7.rc \
    $(LOCAL_PATH)/ramdisk/root/init.post_mount.sh:root/init.post_mount.sh \
    $(LOCAL_PATH)/ramdisk/root/init.m7.usb.rc:root/init.m7.usb.rc \
    $(LOCAL_PATH)/ramdisk/root/init.rc:root/init.rc \
    $(LOCAL_PATH)/ramdisk/root/ueventd.m7.rc:root/ueventd.m7.rc \
    $(LOCAL_PATH)/configs/init.post_boot.sh:/system/etc/init.post_boot.sh

# Custom recovery charging
PRODUCT_COPY_FILES += \
    device/htc/m7/recovery/sbin/choice_fn:recovery/root/sbin/choice_fn \
    device/htc/m7/recovery/sbin/offmode_charging:recovery/root/sbin/offmode_charging \
    device/htc/m7/recovery/sbin/detect_key:recovery/root/sbin/detect_key \
    device/htc/m7/recovery/sbin/power_test:recovery/root/sbin/power_test

# QC thermald config
PRODUCT_COPY_FILES += device/htc/m7/configs/thermald.conf:system/etc/thermald.conf

# Vold config
PRODUCT_COPY_FILES += \
    device/htc/m7/configs/vold.fstab:system/etc/vold.fstab

PRODUCT_PACKAGES += \
    libnetcmdiface

# Wifi config
PRODUCT_COPY_FILES += \
    device/htc/m7/configs/calibration:/system/etc/calibration \
    device/htc/m7/configs/calibration_EMEA:/system/etc/calibration_EMEA	

# Sound configs
PRODUCT_COPY_FILES += \
    device/htc/m7/dsp/soundimage/srs_bypass.cfg:system/etc/soundimage/srs_bypass.cfg \
    device/htc/m7/dsp/soundimage/srsfx_trumedia_51.cfg:system/etc/soundimage/srsfx_trumedia_51.cfg \
    device/htc/m7/dsp/soundimage/srsfx_trumedia_movie.cfg:system/etc/soundimage/srsfx_trumedia_movie.cfg \
    device/htc/m7/dsp/soundimage/srsfx_trumedia_music.cfg:system/etc/soundimage/srsfx_trumedia_music.cfg \
    device/htc/m7/dsp/soundimage/srsfx_trumedia_voice.cfg:system/etc/soundimage/srsfx_trumedia_voice.cfg \
    device/htc/m7/dsp/soundimage/srs_geq10.cfg:system/etc/soundimage/srs_geq10.cfg \
    device/htc/m7/dsp/soundimage/srs_global.cfg:system/etc/soundimage/srs_global.cfg

# Media config
PRODUCT_COPY_FILES += \
    device/htc/m7/configs/audio_policy.conf:system/etc/audio_policy.conf \
    device/htc/m7/configs/AudioBTID.csv:system/etc/AudioBTID.csv \
    device/htc/m7/configs/AudioBTIDnew.csv:system/etc/AudioBTIDnew.csvs \
    device/htc/m7/configs/media_profiles.xml:system/etc/media_profiles.xml \
    device/htc/m7/configs/media_codecs.xml:system/etc/media_codecs.xml \
    device/htc/m7/dsp/snd_soc_msm/snd_soc_msm:system/etc/snd_soc_msm/snd_soc_msm \
    device/htc/m7/dsp/snd_soc_msm/snd_soc_msm_2x:system/etc/snd_soc_msm/snd_soc_msm_2x \
    device/htc/m7/dsp/snd_soc_msm/snd_soc_msm_2x_Fusion3:system/etc/snd_soc_msm/snd_soc_msm_2x_Fusion3 \
    device/htc/m7/dsp/snd_soc_msm/snd_soc_msm_2x_Fusion3_DMIC:system/etc/snd_soc_msm/snd_soc_msm_2x_Fusion3_DMIC \
    device/htc/m7/dsp/snd_soc_msm/snd_soc_msm_Sitar:system/etc/snd_soc_msm/snd_soc_msm_Sitar

# Keylayouts and Keychars
PRODUCT_COPY_FILES += \
    device/htc/m7/keylayout/AVRCP.kl:system/usr/keylayout/AVRCP.kl \
    device/htc/m7/keylayout/Generic.kl:system/usr/keylayout/Generic.kl \
    device/htc/m7/keylayout/h2w_headset.kl:system/usr/keylayout/h2w_headset.kl \
    device/htc/m7/keylayout/keypad_8960.kl:system/usr/keylayout/keypad_8960.kl \
    device/htc/m7/keylayout/projector-Keypad.kl:system/usr/keylayout/projector-Keypad.kl \
    device/htc/m7/keylayout/qwerty.kl:system/usr/keylayout/qwerty.kl \
    device/htc/m7/keylayout/synaptics-rmi-touchscreen.kl:system/usr/keylayout/synaptics-rmi-touchscreen.kl \

# Input device config
PRODUCT_COPY_FILES += \
    device/htc/m7/idc/projector_input.idc:system/usr/idc/projector_input.idc \
    device/htc/m7/idc/qwerty2.idc:system/usr/idc/qwerty2.idc \
    device/htc/m7/idc/qwerty.idc:system/usr/idc/qwerty.idc \
    device/htc/m7/idc/synaptics-rmi-touchscreen.idc:system/usr/idc/synaptics-rmi-touchscreen.idc


# NFC
PRODUCT_PACKAGES += \
    libnfc \
    libnfc_ndef \
    libnfc_jni \
    Nfc \
    Tag \
    com.android.nfc_extras

# Torch
PRODUCT_PACKAGES += \
    Torch

# Permissions
PRODUCT_COPY_FILES += \
    frameworks/native/data/etc/android.hardware.nfc.xml:system/etc/permissions/android.hardware.nfc.xml \
    frameworks/native/data/etc/com.android.nfc_extras.xml:system/etc/permissions/com.android.nfc_extras.xml \
    frameworks/native/data/etc/android.hardware.telephony.gsm.xml:system/etc/permissions/android.hardware.telephony.gsm.xml

# GPS config
PRODUCT_COPY_FILES += \
    device/htc/m7/configs/gps.conf:system/etc/gps.conf

PRODUCT_DEFAULT_PROPERTY_OVERRIDES += \
    persist.sys.usb.config=mtp

# Common build properties
PRODUCT_PROPERTY_OVERRIDES += \
    com.qc.hardware=true \
    debug.composition.type=dyn \
    debug.egl.hw=1 \
    debug.mdpcomp.logs=0 \
    debug.sf.hw=1 \
    dev.pm.dyn_samplingrate=1 \
    lpa.decode=true \
    persist.audio.fluence.mode=endfire \
    persist.audio.vr.enable=false \
    persist.audio.handset.mic=digital \
    persist.audio.speaker.location=high \
    persist.gps.qmienabled=true \
    persist.hwc.mdpcomp.enable=true \
    persist.thermal.monitor=true \
    ro.baseband.arch=msm \
    ro.opengles.version=131072 \
    ro.product.wireless=WCN3660 \
    ro.qc.sdk.audio.fluencetype=fluence \
    ro.qualcomm.bt.hci_transport=smd \
    ro.telephony.ril_class=HTC8960RIL \
    ro.use_data_netmgrd=true \
    wifi.interface=wlan0 \
    dalvik.vm.dexopt-data-only=1

# Set build date
PRODUCT_BUILD_PROP_OVERRIDES += BUILD_UTC_DATE=0

# Device uses high-density artwork where available
PRODUCT_AAPT_CONFIG := normal hdpi xhdpi xxhdpi
PRODUCT_AAPT_PREF_CONFIG := xhdpi xxhdpi
PRODUCT_NAME := full_m7
PRODUCT_DEVICE := m7

# call the proprietary setup
$(call inherit-product-if-exists, vendor/htc/m7/m7-vendor.mk)

# call dalvik heap config
$(call inherit-product-if-exists, frameworks/native/build/phone-xhdpi-2048-dalvik-heap.mk)

WIFI_BAND := 802_11_ABG
 $(call inherit-product-if-exists, hardware/broadcom/wlan/bcmdhd/firmware/bcm4330/device-bcm.mk)
