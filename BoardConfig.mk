# Copyright (C) 2013 The Android Open Source Project
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
# This file sets variables that control the way modules are built
# thorughout the system. It should not be used to conditionally
# disable makefiles (the proper mechanism to control what gets
# included in a build is to use PRODUCT_PACKAGES in a product
# definition file).
#

# WARNING: This line must come *before* including the proprietary
# variant, so that it gets overwritten by the parent (which goes
# against the traditional rules of inheritance).

# inherit from common msm8960
-include device/htc/msm8960-common/BoardConfigCommon.mk

# Bootloader
TARGET_BOOTLOADER_BOARD_NAME := m7wls

# Kernel
TARGET_KERNEL_CONFIG := m7wls_defconfig

# Bluetooth
BOARD_BLUETOOTH_BDROID_BUILDCFG_INCLUDE_DIR := device/htc/m7wls/bluetooth

# HTClog
COMMON_GLOBAL_CFLAGS += -DHTCLOG

# USB
TARGET_USE_CUSTOM_LUN_FILE_PATH := /sys/devices/platform/msm_hsusb/gadget/lun%d/file

# Filesystem
BOARD_BOOTIMAGE_PARTITION_SIZE := 16777216
BOARD_RECOVERYIMAGE_PARTITION_SIZE := 16776704
BOARD_SYSTEMIMAGE_PARTITION_SIZE := 1946156032
BOARD_USERDATAIMAGE_PARTITION_SIZE := 27917287424
BOARD_FLASH_BLOCK_SIZE := 131072

# cat /proc/emmc:
# dev: size erasesize name
# mmcblk0p19: 000ffa00 00000200 "misc"
# mmcblk0p36: 00fffe00 00000200 "recovery"
# mmcblk0p35: 01000000 00000200 "boot"
# mmcblk0p37: 73fffc00 00000200 "system"
# mmcblk0p26: 00140200 00000200 "local"
# mmcblk0p38: 27fffe00 00000200 "cache"
# mmcblk0p39: 680000000 00000200 "userdata"
# mmcblk0p22: 01400000 00000200 "devlog"
# mmcblk0p24: 00040000 00000200 "pdata"
# mmcblk0p27: 00010000 00000200 "extra"
# mmcblk0p33: 04b00200 00000200 "radio"
# mmcblk0p16: 03c00400 00000200 "adsp"
# mmcblk0p15: 00100000 00000200 "dsps"
# mmcblk0p17: 007ffa00 00000200 "radio_config"
# mmcblk0p20: 00400000 00000200 "modem_st1"
# mmcblk0p21: 00400000 00000200 "modem_st2"
# mmcblk0p29: 00040000 00000200 "skylink"
# mmcblk0p30: 01900000 00000200 "carrier"
# mmcblk0p28: 00100000 00000200 "cdma_record"
# mmcblk0p18: 02000000 00000200 "reserve_1"
# mmcblk0p32: 034ffa00 00000200 "reserve_2"
# mmcblk0p34: 05fffc00 00000200 "reserve_3"
# mmcblk0p31: 04729a00 00000200 "reserve"
