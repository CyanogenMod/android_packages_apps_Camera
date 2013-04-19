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
$(call inherit-product, $(SRC_TARGET_DIR)/product/languages_full.mk)

DEVICE_PACKAGE_OVERLAYS += device/htc/m7/overlay

# Ramdisk
PRODUCT_COPY_FILES += \
    device/htc/m7/ramdisk/fstab.m7:root/fstab.m7 \
    device/htc/m7/ramdisk/init:root/init \
    device/htc/m7/ramdisk/init.m7.rc:root/init.m7.rc \
    device/htc/m7/ramdisk/init.m7.usb.rc:root/init.m7.usb.rc \
    device/htc/m7/ramdisk/init.rc:root/init.rc \
    device/htc/m7/ramdisk/ueventd.m7.rc:root/ueventd.m7.rc \

# QCOM Boot ramdisk setup
PRODUCT_PACKAGES += \
    init.qcom.sh \
    init.qcom.usb.rc \
    init.qcom.rc \
    ueventd.qcom.rc

# Qualcomm scripts
PRODUCT_COPY_FILES += \
    device/htc/m7/configs/init.qcom.bt.sh:/system/etc/init.qcom.bt.sh \
    device/htc/m7/configs/init.qcom.fm.sh:/system/etc/init.qcom.fm.sh \
    device/htc/m7/configs/init.qcom.post_boot.sh:/system/etc/init.qcom.post_boot.sh \
    device/htc/m7/configs/init.qcom.sdio.sh:/system/etc/init.qcom.sdio.sh \
    device/htc/m7/configs/init.qcom.wifi.sh:/system/etc/init.qcom.wifi.sh

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

# Wifi config
PRODUCT_COPY_FILES += \
    device/htc/m7/configs/wpa_supplicant.conf:/system/etc/wifi/wpa_supplicant.conf \
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

# Audio
PRODUCT_PACKAGES += \
    alsa.msm8960 \
    audio.a2dp.default \
    audio_policy.msm8960 \
    audio.primary.msm8960 \
    audio.r_submix.default \
    audio.usb.default \
    libaudio-resampler

# Bluetooth
PRODUCT_PACKAGES += \
    hci_qcomm_init

# Camera
PRODUCT_PACKAGES += \
    camera.msm8960

# GPS
PRODUCT_PACKAGES += \
    libloc_adapter \
    libloc_eng \
    libgps.utils \
    gps.msm8960

# Graphics
PRODUCT_PACKAGES += \
    copybit.msm8960 \
    gralloc.msm8960 \
    hwcomposer.msm8960 \
    libgenlock \
    liboverlay

# Lights
PRODUCT_PACKAGES += \
    lights.msm8960

# OMX
PRODUCT_PACKAGES += \
    libc2dcolorconvert \
    libdivxdrmdecrypt \
    libOmxCore \
    libOmxVdec \
    libOmxVenc \
    libOmxAacEnc \
    libOmxAmrEnc \
    libOmxEvrcEnc \
    libOmxQcelp13Enc \
    libstagefrighthw

# Power
PRODUCT_PACKAGES += \
    power.msm8960

# HDMI
PRODUCT_PACKAGES += \
    hdmid

# QCOM rngd
PRODUCT_PACKAGES += \
    qrngd

# USB
PRODUCT_PACKAGES += \
    com.android.future.usb.accessory

# Filesystem management tools
PRODUCT_PACKAGES += \
    make_ext4fs \
    setup_fs

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
    frameworks/native/data/etc/android.hardware.telephony.gsm.xml:system/etc/permissions/android.hardware.telephony.gsm.xml \
    frameworks/native/data/etc/android.hardware.telephony.cdma.xml:system/etc/permissions/android.hardware.telephony.cdma.xml \
    frameworks/native/data/etc/handheld_core_hardware.xml:system/etc/permissions/handheld_core_hardware.xml \
    frameworks/native/data/etc/android.hardware.camera.flash-autofocus.xml:system/etc/permissions/android.hardware.camera.flash-autofocus.xml \
    frameworks/native/data/etc/android.hardware.camera.front.xml:system/etc/permissions/android.hardware.camera.front.xml \
    frameworks/native/data/etc/android.hardware.location.gps.xml:system/etc/permissions/android.hardware.location.gps.xml \
    frameworks/native/data/etc/android.hardware.wifi.xml:system/etc/permissions/android.hardware.wifi.xml \
    frameworks/native/data/etc/android.hardware.sensor.proximity.xml:system/etc/permissions/android.hardware.sensor.proximity.xml \
    frameworks/native/data/etc/android.hardware.sensor.light.xml:system/etc/permissions/android.hardware.sensor.light.xml \
    frameworks/native/data/etc/android.hardware.sensor.gyroscope.xml:system/etc/permissions/android.hardwardware.sensor.gyroscope.xml \
    frameworks/native/data/etc/android.hardware.touchscreen.multitouch.distinct.xml:system/etc/permissions/android.hardware.touchscreen.multitouch.distinct.xml \
    frameworks/native/data/etc/android.hardware.usb.accessory.xml:system/etc/permissions/android.hardware.usb.accessory.xml \
    frameworks/native/data/etc/android.hardware.usb.host.xml:system/etc/permissions/android.hardware.usb.host.xml \
    frameworks/native/data/etc/android.software.sip.voip.xml:system/etc/permissions/android.software.sip.voip.xml \
    frameworks/native/data/etc/android.hardware.sensor.accelerometer.xml:system/etc/permissions/android.hardware.sensor.accelerometer.xml \
    frameworks/native/data/etc/android.hardware.sensor.compass.xml:system/etc/permissions/android.hardware.compass.xml \
    packages/wallpapers/LivePicker/android.software.live_wallpaper.xml:system/etc/permissions/android.software.live_wallpaper.xml

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
    wifi.interface=wlan0

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
