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

DEVICE_PACKAGE_OVERLAYS += device/htc/m7/overlay

# Boot ramdisk setup
PRODUCT_PACKAGES += \
    fstab.jet \
    init.target.rc

# Custom recovery charging
PRODUCT_COPY_FILES += \
    device/htc/m7/recovery/sbin/choice_fn:recovery/root/sbin/choice_fn \
    device/htc/m7/recovery/sbin/offmode_charging:recovery/root/sbin/offmode_charging \
    device/htc/m7/recovery/sbin/detect_key:recovery/root/sbin/detect_key \
    device/htc/m7/recovery/sbin/power_test:recovery/root/sbin/power_test

# Vold config
PRODUCT_COPY_FILES += \
    device/htc/m7/configs/vold.fstab:system/etc/vold.fstab

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
    device/htc/m7/dsp/snd_soc_msm/snd_soc_msm_2x_Fusion3:system/etc/snd_soc_msm/snd_soc_msm_2x_Fusion3
	
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

# NFCEE access control
ifeq ($(TARGET_BUILD_VARIANT),user)
    NFCEE_ACCESS_PATH := device/htc/m7/configs/nfcee_access.xml
else
    NFCEE_ACCESS_PATH := device/htc/m7/configs/nfcee_access_debug.xml
endif
PRODUCT_COPY_FILES += \
    $(NFCEE_ACCESS_PATH):system/etc/nfcee_access.xml

# NFC Support
PRODUCT_PACKAGES += \
    libnfc \
    libnfc_jni \
    Nfc \
    Tag \
    com.android.nfc_extras

# Torch
PRODUCT_PACKAGES += \
    Torch

# Filesystem management tools
PRODUCT_PACKAGES += \
   make_ext4fs \
   e2fsck \
   setup_fs

# Permissions
PRODUCT_COPY_FILES += \
    frameworks/native/data/etc/android.hardware.telephony.cdma.xml:system/etc/permissions/android.hardware.telephony.cdma.xml \
    frameworks/native/data/etc/android.hardware.nfc.xml:system/etc/permissions/android.hardware.nfc.xml \
    frameworks/base/nfc-extras/com.android.nfc_extras.xml:system/etc/permissions/com.android.nfc_extras.xml \
    frameworks/native/data/etc/com.nxp.mifare.xml:system/etc/permissions/com.nxp.mifare.xml

## CDMA Sprint stuffs
PRODUCT_PROPERTY_OVERRIDES += \
    ro.com.google.clientidbase=android-sprint-us \
    ro.com.google.locationfeatures=1 \
    ro.cdma.home.operator.numeric=310120 \
    ro.cdma.home.operator.alpha=Sprint \
    gsm.sim.operator.alpha = sprint \
    gsm.sim.operator.numeric = 310120 \
    gsm.sim.operator.iso-country = us \
    gsm.operator.alpha = sprint \
    gsm.operator.numeric = 310120 \
    gsm.operator.iso-country = us \
    ro.carrier=Sprint

# We have enough space to hold precise GC data
PRODUCT_TAGS += dalvik.gc.type-precise

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
